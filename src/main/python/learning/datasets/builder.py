"""Stage E2 sequence construction, feature derivation, and Parquet artifact helpers."""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from ..ingestion import DatasetBundle, TimeUnit
from .contracts import (
    CorridorBucketFrequencyArtifact,
    CorridorBucketFrequencyRow,
    SequenceBuilderConfig,
    SequenceDatasetArtifact,
    SequenceRow,
)

_MANIFEST_VERSION = "E2.v1"
_SEQUENCE_SCOPE_EDGE = "EDGE"
_SEQUENCE_SCOPE_CORRIDOR = "CORRIDOR"
_SEQUENCE_SCOPE_TIME_WINDOW = "TIME_WINDOW"


def _to_epoch_seconds(timestamp_ticks: int, engine_time_unit: TimeUnit) -> int:
    if engine_time_unit == TimeUnit.SECONDS:
        return timestamp_ticks
    return timestamp_ticks // 1_000 if timestamp_ticks >= 0 else -((-timestamp_ticks + 999) // 1_000)


def _from_epoch_seconds(epoch_seconds: int, engine_time_unit: TimeUnit) -> int:
    if engine_time_unit == TimeUnit.SECONDS:
        return epoch_seconds
    return epoch_seconds * 1_000


def _context_for_timestamp(
    timestamp_ticks: int,
    engine_time_unit: TimeUnit,
    config: SequenceBuilderConfig,
) -> tuple[int, int, int]:
    epoch_seconds = _to_epoch_seconds(timestamp_ticks, engine_time_unit)
    zone = ZoneInfo(config.timezone_id)
    localized = datetime.fromtimestamp(epoch_seconds, zone)
    offset_seconds = int(localized.utcoffset().total_seconds()) if localized.utcoffset() is not None else 0
    seconds_since_midnight = (
        localized.hour * 3_600
        + localized.minute * 60
        + localized.second
    )
    bucket_index = seconds_since_midnight // config.bucket_size_seconds
    local_epoch_seconds = epoch_seconds + offset_seconds
    local_window_start = (local_epoch_seconds // config.bucket_size_seconds) * config.bucket_size_seconds
    window_start_epoch_seconds = local_window_start - offset_seconds
    return localized.weekday(), bucket_index, _from_epoch_seconds(window_start_epoch_seconds, engine_time_unit)


def _primary_scope(id_namespace: str) -> str | None:
    if id_namespace == "edge":
        return _SEQUENCE_SCOPE_EDGE
    if id_namespace == "corridor":
        return _SEQUENCE_SCOPE_CORRIDOR
    return None


def _normalized_tick_fields(row: dict[str, Any]) -> tuple[str, ...]:
    return tuple(
        key
        for key in sorted(row)
        if key.endswith("_ticks") and key not in {"window_start_ticks"}
    )


def _optional_float(row: dict[str, Any], field_name: str) -> float | None:
    raw = row.get(field_name)
    if raw in (None, ""):
        return None
    return float(raw)


def _event_code(row: dict[str, Any]) -> str | None:
    for field_name in ("incident_code", "change_type"):
        value = row.get(field_name)
        if value not in (None, ""):
            return str(value)
    return None


def build_sequence_dataset(bundle: DatasetBundle, config: SequenceBuilderConfig) -> SequenceDatasetArtifact:
    """Stage E2 build the canonical sequence dataset artifact from an E1 bundle."""

    engine_time_unit = TimeUnit.parse(bundle.manifest.engine_time_unit)
    observations: list[dict[str, Any]] = []

    for source_name in sorted(bundle.normalized_rows):
        for row in bundle.normalized_rows[source_name]:
            primary_scope = _primary_scope(str(row["id_namespace"]))
            subject_id = str(row["external_id"])
            for timestamp_field in _normalized_tick_fields(row):
                timestamp_ticks = int(row[timestamp_field])
                base_timestamp_field = timestamp_field.removesuffix("_ticks")
                day_of_week, bucket_index, window_start_ticks = _context_for_timestamp(
                    timestamp_ticks,
                    engine_time_unit,
                    config,
                )
                base_observation = {
                    "bucket_index": bucket_index,
                    "day_of_week": day_of_week,
                    "event_code": _event_code(row),
                    "feed_kind": str(row["feed_kind"]),
                    "id_namespace": str(row["id_namespace"]),
                    "source_name": str(row["source_name"]),
                    "speed_factor_observed": _optional_float(row, "speed_factor_observed"),
                    "timestamp_field": base_timestamp_field,
                    "timestamp_ticks": timestamp_ticks,
                    "travel_time": _optional_float(row, "travel_time"),
                    "window_start_ticks": window_start_ticks,
                }
                if primary_scope is not None:
                    observations.append(
                        {
                            **base_observation,
                            "sequence_scope": primary_scope,
                            "subject_id": subject_id,
                        }
                    )
                observations.append(
                    {
                        **base_observation,
                        "sequence_scope": _SEQUENCE_SCOPE_TIME_WINDOW,
                        "subject_id": str(window_start_ticks),
                    }
                )

    periodicity_counts = Counter(
        (
            observation["sequence_scope"],
            observation["subject_id"],
            observation["day_of_week"],
            observation["bucket_index"],
        )
        for observation in observations
    )
    corridor_activity_counts = Counter(
        (observation["day_of_week"], observation["bucket_index"])
        for observation in observations
        if observation["sequence_scope"] == _SEQUENCE_SCOPE_CORRIDOR
    )

    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for observation in observations:
        grouped[(str(observation["sequence_scope"]), str(observation["subject_id"]))].append(observation)

    rows: list[SequenceRow] = []
    bucket_size_ticks = config.bucket_size_ticks(engine_time_unit)
    for (sequence_scope, subject_id), subject_observations in sorted(grouped.items()):
        subject_observations.sort(
            key=lambda observation: (
                int(observation["timestamp_ticks"]),
                str(observation["source_name"]),
                str(observation["timestamp_field"]),
                str(observation["feed_kind"]),
            )
        )
        previous_timestamp_ticks: int | None = None
        previous_window_start_ticks: int | None = None
        persistence_run_length = 0

        for sequence_index, observation in enumerate(subject_observations):
            timestamp_ticks = int(observation["timestamp_ticks"])
            window_start_ticks = int(observation["window_start_ticks"])
            if previous_timestamp_ticks is None:
                recency_gap_ticks = 0
            else:
                recency_gap_ticks = timestamp_ticks - previous_timestamp_ticks

            if previous_window_start_ticks is None:
                persistence_run_length = 1
            elif window_start_ticks <= previous_window_start_ticks + bucket_size_ticks:
                persistence_run_length += 1
            else:
                persistence_run_length = 1

            rows.append(
                SequenceRow(
                    sequence_scope=sequence_scope,
                    feed_kind=str(observation["feed_kind"]),
                    source_name=str(observation["source_name"]),
                    id_namespace=str(observation["id_namespace"]),
                    subject_id=subject_id,
                    timestamp_field=str(observation["timestamp_field"]),
                    timestamp_ticks=timestamp_ticks,
                    window_start_ticks=window_start_ticks,
                    day_of_week=int(observation["day_of_week"]),
                    bucket_index=int(observation["bucket_index"]),
                    sequence_index=sequence_index,
                    recency_gap_ticks=recency_gap_ticks,
                    persistence_run_length=persistence_run_length,
                    periodicity_bucket_count=periodicity_counts[
                        (
                            sequence_scope,
                            subject_id,
                            int(observation["day_of_week"]),
                            int(observation["bucket_index"]),
                        )
                    ],
                    corridor_activity_count=corridor_activity_counts[
                        (int(observation["day_of_week"]), int(observation["bucket_index"]))
                    ],
                    travel_time=observation["travel_time"],
                    speed_factor_observed=observation["speed_factor_observed"],
                    event_code=observation["event_code"],
                )
            )
            previous_timestamp_ticks = timestamp_ticks
            previous_window_start_ticks = window_start_ticks

    return SequenceDatasetArtifact(
        manifest_version=_MANIFEST_VERSION,
        engine_time_unit=engine_time_unit.value,
        bucket_size_seconds=config.bucket_size_seconds,
        timezone_id=config.timezone_id,
        source_names=tuple(source.source_name for source in bundle.manifest.sources),
        rows=tuple(rows),
    )


def build_corridor_bucket_frequency(sequence_dataset: SequenceDatasetArtifact) -> CorridorBucketFrequencyArtifact:
    """Stage E2 derive the canonical historical corridor-bucket baseline artifact."""

    corridor_rows = [
        row
        for row in sequence_dataset.rows
        if row.sequence_scope == _SEQUENCE_SCOPE_CORRIDOR
    ]
    corridor_totals = Counter(row.subject_id for row in corridor_rows)
    bucket_counts = Counter(
        (row.subject_id, row.day_of_week, row.bucket_index)
        for row in corridor_rows
    )

    rows = [
        CorridorBucketFrequencyRow(
            corridor_id=corridor_id,
            day_of_week=day_of_week,
            bucket_index=bucket_index,
            observation_count=observation_count,
            total_corridor_observations=corridor_totals[corridor_id],
            historical_bucket_frequency=observation_count / corridor_totals[corridor_id],
        )
        for (corridor_id, day_of_week, bucket_index), observation_count in sorted(bucket_counts.items())
    ]
    return CorridorBucketFrequencyArtifact(
        manifest_version=_MANIFEST_VERSION,
        engine_time_unit=sequence_dataset.engine_time_unit,
        bucket_size_seconds=sequence_dataset.bucket_size_seconds,
        timezone_id=sequence_dataset.timezone_id,
        rows=tuple(rows),
    )


def _parquet_modules():
    import pyarrow as pa
    import pyarrow.parquet as pq

    return pa, pq


def _sequence_schema(pa):
    return pa.schema(
        [
            ("sequence_scope", pa.string()),
            ("feed_kind", pa.string()),
            ("source_name", pa.string()),
            ("id_namespace", pa.string()),
            ("subject_id", pa.string()),
            ("timestamp_field", pa.string()),
            ("timestamp_ticks", pa.int64()),
            ("window_start_ticks", pa.int64()),
            ("day_of_week", pa.int32()),
            ("bucket_index", pa.int32()),
            ("sequence_index", pa.int32()),
            ("recency_gap_ticks", pa.int64()),
            ("persistence_run_length", pa.int32()),
            ("periodicity_bucket_count", pa.int32()),
            ("corridor_activity_count", pa.int32()),
            ("travel_time", pa.float64()),
            ("speed_factor_observed", pa.float64()),
            ("event_code", pa.string()),
        ]
    )


def _corridor_frequency_schema(pa):
    return pa.schema(
        [
            ("corridor_id", pa.string()),
            ("day_of_week", pa.int32()),
            ("bucket_index", pa.int32()),
            ("observation_count", pa.int32()),
            ("total_corridor_observations", pa.int32()),
            ("historical_bucket_frequency", pa.float64()),
        ]
    )


def _attach_metadata(table, metadata: dict[str, Any]):
    encoded = {
        key.encode("utf-8"): json.dumps(value).encode("utf-8")
        for key, value in metadata.items()
    }
    return table.replace_schema_metadata(encoded)


def _read_metadata(table) -> dict[str, Any]:
    metadata = table.schema.metadata or {}
    return {
        key.decode("utf-8"): json.loads(value.decode("utf-8"))
        for key, value in metadata.items()
    }


def write_sequence_dataset(sequence_dataset: SequenceDatasetArtifact, output_path: Path | str) -> Path:
    """Stage E2 write the canonical `sequence_dataset.parquet` artifact."""

    pa, pq = _parquet_modules()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(sequence_dataset.to_pylist(), schema=_sequence_schema(pa))
    table = _attach_metadata(
        table,
        {
            "manifest_version": sequence_dataset.manifest_version,
            "engine_time_unit": sequence_dataset.engine_time_unit,
            "bucket_size_seconds": sequence_dataset.bucket_size_seconds,
            "timezone_id": sequence_dataset.timezone_id,
            "source_names": list(sequence_dataset.source_names),
        },
    )
    pq.write_table(table, path)
    return path


def read_sequence_dataset(input_path: Path | str) -> SequenceDatasetArtifact:
    """Stage E2 read the canonical `sequence_dataset.parquet` artifact."""

    _, pq = _parquet_modules()
    table = pq.read_table(input_path)
    metadata = _read_metadata(table)
    rows = tuple(SequenceRow.from_dict(row) for row in table.to_pylist())
    return SequenceDatasetArtifact(
        manifest_version=str(metadata["manifest_version"]),
        engine_time_unit=str(metadata["engine_time_unit"]),
        bucket_size_seconds=int(metadata["bucket_size_seconds"]),
        timezone_id=str(metadata["timezone_id"]),
        source_names=tuple(metadata["source_names"]),
        rows=rows,
    )


def write_corridor_bucket_frequency(
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
    output_path: Path | str,
) -> Path:
    """Stage E2 write the canonical `corridor_bucket_frequency.parquet` artifact."""

    pa, pq = _parquet_modules()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(
        corridor_bucket_frequency.to_pylist(),
        schema=_corridor_frequency_schema(pa),
    )
    table = _attach_metadata(
        table,
        {
            "manifest_version": corridor_bucket_frequency.manifest_version,
            "engine_time_unit": corridor_bucket_frequency.engine_time_unit,
            "bucket_size_seconds": corridor_bucket_frequency.bucket_size_seconds,
            "timezone_id": corridor_bucket_frequency.timezone_id,
        },
    )
    pq.write_table(table, path)
    return path


def read_corridor_bucket_frequency(input_path: Path | str) -> CorridorBucketFrequencyArtifact:
    """Stage E2 read the canonical `corridor_bucket_frequency.parquet` artifact."""

    _, pq = _parquet_modules()
    table = pq.read_table(input_path)
    metadata = _read_metadata(table)
    rows = tuple(CorridorBucketFrequencyRow.from_dict(row) for row in table.to_pylist())
    return CorridorBucketFrequencyArtifact(
        manifest_version=str(metadata["manifest_version"]),
        engine_time_unit=str(metadata["engine_time_unit"]),
        bucket_size_seconds=int(metadata["bucket_size_seconds"]),
        timezone_id=str(metadata["timezone_id"]),
        rows=rows,
    )
