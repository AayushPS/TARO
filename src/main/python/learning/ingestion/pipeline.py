"""Stage E1 CSV ingestion, validation, normalization, and manifest writing."""

from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

from src.main.python.Utils import IDMapper

from .contracts import (
    SourceFeed,
    SourceValidationDiagnostic,
    SourceValidationError,
    TimeUnit,
)
from .manifest import DatasetBundle, DatasetManifest, DatasetSourceManifest

_MANIFEST_VERSION = "E1.v1"


@dataclass
class _SourceProcessingResult:
    source_name: str
    manifest_entry: DatasetSourceManifest | None
    normalized_rows: tuple[dict[str, Any], ...]
    diagnostics: tuple[SourceValidationDiagnostic, ...]
    fatal: bool


def _normalize_to_engine_ticks(timestamp: int, input_unit: TimeUnit, engine_unit: TimeUnit) -> int:
    if input_unit == engine_unit:
        return timestamp
    if input_unit == TimeUnit.SECONDS and engine_unit == TimeUnit.MILLISECONDS:
        return timestamp * 1_000
    return timestamp // 1_000 if timestamp >= 0 else -((-timestamp + 999) // 1_000)


def _clean_cell(row: dict[str, str | None], field_name: str) -> str:
    value = row.get(field_name)
    return "" if value is None else value.strip()


def _content_hash(payload: Any) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _build_source_result(
    source_feed: SourceFeed,
    engine_time_unit: TimeUnit,
    namespace_mappers: dict[str, IDMapper],
) -> _SourceProcessingResult:
    diagnostics: list[SourceValidationDiagnostic] = []
    path = source_feed.path
    if not path.exists():
        diagnostics.append(
            SourceValidationDiagnostic(
                severity="error",
                code="missing_source_file",
                message=f"source file does not exist: {path}",
                source_name=source_feed.source_name,
            )
        )
        return _SourceProcessingResult(
            source_name=source_feed.source_name,
            manifest_entry=None,
            normalized_rows=(),
            diagnostics=tuple(diagnostics),
            fatal=True,
        )

    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        header = reader.fieldnames or []
        required_columns = set(source_feed.schema.required_columns())
        missing_columns = sorted(required_columns.difference(header))
        if missing_columns:
            for column_name in missing_columns:
                diagnostics.append(
                    SourceValidationDiagnostic(
                        severity="error",
                        code="missing_column",
                        message=f"required column is missing from header: {column_name}",
                        source_name=source_feed.source_name,
                        field_name=column_name,
                    )
                )
            return _SourceProcessingResult(
                source_name=source_feed.source_name,
                manifest_entry=None,
                normalized_rows=(),
                diagnostics=tuple(diagnostics),
                fatal=True,
            )

        id_mapper = namespace_mappers.setdefault(source_feed.schema.id_namespace, IDMapper())
        source_unique_ids: set[str] = set()
        seen_duplicate_keys: set[tuple[str, ...]] = set()
        last_seen_ticks: dict[str, int] = {}
        normalized_rows: list[dict[str, Any]] = []
        min_tick: int | None = None
        max_tick: int | None = None
        late_arrival_count = 0

        for row_number, row in enumerate(reader, start=2):
            row_errors: list[SourceValidationDiagnostic] = []
            normalized_row: dict[str, Any] = {
                "feed_kind": source_feed.schema.feed_kind.value,
                "source_name": source_feed.source_name,
            }

            for field_name in source_feed.schema.required_fields:
                value = _clean_cell(row, field_name)
                normalized_row[field_name] = value
                if value == "":
                    row_errors.append(
                        SourceValidationDiagnostic(
                            severity="error",
                            code="missing_field",
                            message=f"required field is blank: {field_name}",
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=field_name,
                        )
                    )

            external_id = _clean_cell(row, source_feed.schema.id_field)
            normalized_row[source_feed.schema.id_field] = external_id
            if external_id == "" and source_feed.schema.id_field not in source_feed.schema.required_fields:
                row_errors.append(
                    SourceValidationDiagnostic(
                        severity="error",
                        code="missing_field",
                        message=f"required field is blank: {source_feed.schema.id_field}",
                        source_name=source_feed.source_name,
                        row_number=row_number,
                        field_name=source_feed.schema.id_field,
                    )
                )

            duplicate_key = tuple(_clean_cell(row, field_name) for field_name in source_feed.schema.duplicate_key_fields)
            if any(value == "" for value in duplicate_key):
                pass
            elif duplicate_key in seen_duplicate_keys:
                row_errors.append(
                    SourceValidationDiagnostic(
                        severity="error",
                        code="duplicate_row_id",
                        message=f"duplicate key encountered: {duplicate_key}",
                        source_name=source_feed.source_name,
                        row_number=row_number,
                    )
                )
            else:
                seen_duplicate_keys.add(duplicate_key)

            for unit_rule in source_feed.schema.unit_rules:
                unit_value = _clean_cell(row, unit_rule.unit_field)
                normalized_row[unit_rule.unit_field] = unit_value
                if unit_value == "":
                    row_errors.append(
                        SourceValidationDiagnostic(
                            severity="error",
                            code="missing_field",
                            message=f"required field is blank: {unit_rule.unit_field}",
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=unit_rule.unit_field,
                        )
                    )
                    continue
                canonical_unit = unit_value.replace("-", "_").replace(" ", "_").upper()
                if canonical_unit != unit_rule.expected_unit:
                    row_errors.append(
                        SourceValidationDiagnostic(
                            severity="error",
                            code="unit_mismatch",
                            message=(
                                f"expected {unit_rule.unit_field}={unit_rule.expected_unit} "
                                f"for {unit_rule.value_field}, got {canonical_unit}"
                            ),
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=unit_rule.unit_field,
                        )
                    )

            normalized_temporal_fields: dict[str, int] = {}
            for temporal_rule in source_feed.schema.temporal_fields:
                raw_timestamp = _clean_cell(row, temporal_rule.field_name)
                normalized_row[temporal_rule.field_name] = raw_timestamp
                if raw_timestamp == "":
                    row_errors.append(
                        SourceValidationDiagnostic(
                            severity="error",
                            code="missing_field",
                            message=f"required field is blank: {temporal_rule.field_name}",
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=temporal_rule.field_name,
                        )
                    )
                    continue
                try:
                    timestamp_value = int(raw_timestamp)
                except ValueError:
                    row_errors.append(
                        SourceValidationDiagnostic(
                            severity="error",
                            code="invalid_temporal_value",
                            message=f"temporal field must be an integer tick value: {temporal_rule.field_name}",
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=temporal_rule.field_name,
                        )
                    )
                    continue

                raw_unit = temporal_rule.unit
                if raw_unit is None:
                    raw_unit_value = _clean_cell(row, temporal_rule.unit_field or "")
                    if raw_unit_value == "":
                        row_errors.append(
                            SourceValidationDiagnostic(
                                severity="error",
                                code="missing_field",
                                message=f"required field is blank: {temporal_rule.unit_field}",
                                source_name=source_feed.source_name,
                                row_number=row_number,
                                field_name=temporal_rule.unit_field,
                            )
                        )
                        continue
                    try:
                        raw_unit = TimeUnit.parse(raw_unit_value)
                    except ValueError as exc:
                        row_errors.append(
                            SourceValidationDiagnostic(
                                severity="error",
                                code="invalid_temporal_unit",
                                message=str(exc),
                                source_name=source_feed.source_name,
                                row_number=row_number,
                                field_name=temporal_rule.unit_field,
                            )
                        )
                        continue

                normalized_ticks = _normalize_to_engine_ticks(timestamp_value, raw_unit, engine_time_unit)
                normalized_temporal_fields[temporal_rule.field_name] = normalized_ticks
                normalized_row[temporal_rule.normalized_field_name] = normalized_ticks
                min_tick = normalized_ticks if min_tick is None else min(min_tick, normalized_ticks)
                max_tick = normalized_ticks if max_tick is None else max(max_tick, normalized_ticks)

            if row_errors:
                diagnostics.extend(row_errors)
                continue

            internal_id = id_mapper.get_or_create(external_id)
            source_unique_ids.add(external_id)
            normalized_row["external_id"] = external_id
            normalized_row["id_namespace"] = source_feed.schema.id_namespace
            normalized_row["internal_subject_id"] = internal_id

            if source_feed.schema.ordering_field is not None:
                ordering_ticks = normalized_temporal_fields[source_feed.schema.ordering_field]
                last_ticks = last_seen_ticks.get(external_id)
                if last_ticks is not None and ordering_ticks < last_ticks:
                    diagnostics.append(
                        SourceValidationDiagnostic(
                            severity="warning",
                            code="late_arrival",
                            message=(
                                f"{source_feed.schema.ordering_field} regressed from {last_ticks} "
                                f"to {ordering_ticks} for {external_id}"
                            ),
                            source_name=source_feed.source_name,
                            row_number=row_number,
                            field_name=source_feed.schema.ordering_field,
                        )
                    )
                    late_arrival_count += 1
                last_seen_ticks[external_id] = ordering_ticks if last_ticks is None else max(last_ticks, ordering_ticks)

            normalized_rows.append(normalized_row)

    fatal = any(diagnostic.severity == "ERROR" for diagnostic in diagnostics)
    if fatal:
        return _SourceProcessingResult(
            source_name=source_feed.source_name,
            manifest_entry=None,
            normalized_rows=tuple(normalized_rows),
            diagnostics=tuple(diagnostics),
            fatal=True,
        )

    warning_counter = Counter(diagnostic.code for diagnostic in diagnostics if diagnostic.severity == "WARNING")
    manifest_entry = DatasetSourceManifest(
        source_name=source_feed.source_name,
        feed_kind=source_feed.schema.feed_kind.value,
        id_namespace=source_feed.schema.id_namespace,
        row_count=len(normalized_rows),
        unique_id_count=len(source_unique_ids),
        normalized_tick_range={"max_tick": max_tick, "min_tick": min_tick},
        warning_count=sum(warning_counter.values()),
        late_arrival_count=late_arrival_count,
        diagnostic_counts=dict(warning_counter),
        content_hash=_content_hash(normalized_rows),
        temporal_fields=tuple(rule.field_name for rule in source_feed.schema.temporal_fields),
        lineage=source_feed.lineage.as_dict(),
        filters=source_feed.filters.as_dict(),
    )
    return _SourceProcessingResult(
        source_name=source_feed.source_name,
        manifest_entry=manifest_entry,
        normalized_rows=tuple(normalized_rows),
        diagnostics=tuple(diagnostics),
        fatal=False,
    )


def ingest_sources(source_feeds: Sequence[SourceFeed], engine_time_unit: TimeUnit) -> DatasetBundle:
    """Stage E1 ingest/validate sources and emit a canonical manifest-backed bundle."""

    namespace_mappers: dict[str, IDMapper] = {}
    results = [
        _build_source_result(source_feed, engine_time_unit, namespace_mappers)
        for source_feed in sorted(source_feeds, key=lambda entry: entry.source_name)
    ]

    all_diagnostics = tuple(
        diagnostic
        for result in results
        for diagnostic in result.diagnostics
    )
    if any(result.fatal for result in results):
        raise SourceValidationError(all_diagnostics)

    manifest = DatasetManifest(
        manifest_version=_MANIFEST_VERSION,
        engine_time_unit=engine_time_unit.value,
        total_row_count=sum(entry.manifest_entry.row_count for entry in results if entry.manifest_entry is not None),
        total_warning_count=sum(entry.manifest_entry.warning_count for entry in results if entry.manifest_entry is not None),
        id_namespace_cardinality={
            namespace: mapper.size()
            for namespace, mapper in sorted(namespace_mappers.items())
        },
        sources=tuple(
            entry.manifest_entry
            for entry in results
            if entry.manifest_entry is not None
        ),
    )
    return DatasetBundle(
        manifest=manifest,
        normalized_rows={
            entry.source_name: entry.normalized_rows
            for entry in results
        },
        diagnostics=tuple(
            diagnostic for diagnostic in all_diagnostics if diagnostic.severity == "WARNING"
        ),
    )


def write_dataset_manifest(manifest: DatasetManifest, output_path: Path | str) -> Path:
    """Stage E1 write `dataset_manifest.json` deterministically to disk."""

    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(manifest.to_json(), encoding="utf-8")
    return path
