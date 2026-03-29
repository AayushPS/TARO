"""Stage E1 canonical dataset-manifest objects and serialization helpers."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, Mapping

from .contracts import SourceValidationDiagnostic, _freeze_mapping


def _json_ready(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {key: _json_ready(sub_value) for key, sub_value in value.items()}
    if isinstance(value, tuple):
        return [_json_ready(item) for item in value]
    return value


@dataclass(frozen=True)
class DatasetSourceManifest:
    """Stage E1 per-source manifest entry."""

    source_name: str
    feed_kind: str
    id_namespace: str
    row_count: int
    unique_id_count: int
    normalized_tick_range: Mapping[str, int | None]
    warning_count: int
    late_arrival_count: int
    diagnostic_counts: Mapping[str, int]
    content_hash: str
    temporal_fields: tuple[str, ...]
    lineage: Mapping[str, Any]
    filters: Mapping[str, Any]

    def __post_init__(self) -> None:
        object.__setattr__(self, "lineage", _freeze_mapping(self.lineage))
        object.__setattr__(self, "filters", _freeze_mapping(self.filters))
        object.__setattr__(self, "diagnostic_counts", _freeze_mapping(self.diagnostic_counts))
        object.__setattr__(self, "normalized_tick_range", _freeze_mapping(self.normalized_tick_range))
        object.__setattr__(self, "temporal_fields", tuple(self.temporal_fields))

    def to_dict(self) -> dict[str, Any]:
        return {
            "content_hash": self.content_hash,
            "diagnostic_counts": dict(self.diagnostic_counts),
            "feed_kind": self.feed_kind,
            "filters": dict(self.filters),
            "id_namespace": self.id_namespace,
            "late_arrival_count": self.late_arrival_count,
            "lineage": dict(self.lineage),
            "normalized_tick_range": dict(self.normalized_tick_range),
            "row_count": self.row_count,
            "source_name": self.source_name,
            "temporal_fields": list(self.temporal_fields),
            "unique_id_count": self.unique_id_count,
            "warning_count": self.warning_count,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "DatasetSourceManifest":
        return cls(
            content_hash=str(payload["content_hash"]),
            diagnostic_counts=dict(payload["diagnostic_counts"]),
            feed_kind=str(payload["feed_kind"]),
            filters=dict(payload["filters"]),
            id_namespace=str(payload["id_namespace"]),
            late_arrival_count=int(payload["late_arrival_count"]),
            lineage=dict(payload["lineage"]),
            normalized_tick_range=dict(payload["normalized_tick_range"]),
            row_count=int(payload["row_count"]),
            source_name=str(payload["source_name"]),
            temporal_fields=tuple(payload["temporal_fields"]),
            unique_id_count=int(payload["unique_id_count"]),
            warning_count=int(payload["warning_count"]),
        )


@dataclass(frozen=True)
class DatasetManifest:
    """Stage E1 manifest artifact consumed by later learning/calibration stages."""

    manifest_version: str
    engine_time_unit: str
    total_row_count: int
    total_warning_count: int
    id_namespace_cardinality: Mapping[str, int]
    sources: tuple[DatasetSourceManifest, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "id_namespace_cardinality", _freeze_mapping(self.id_namespace_cardinality))
        object.__setattr__(self, "sources", tuple(self.sources))

    def to_dict(self) -> dict[str, Any]:
        return {
            "engine_time_unit": self.engine_time_unit,
            "id_namespace_cardinality": dict(self.id_namespace_cardinality),
            "manifest_version": self.manifest_version,
            "sources": [source.to_dict() for source in self.sources],
            "total_row_count": self.total_row_count,
            "total_warning_count": self.total_warning_count,
        }

    def to_json(self) -> str:
        return json.dumps(
            _json_ready(self.to_dict()),
            indent=2,
            sort_keys=True,
        ) + "\n"

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "DatasetManifest":
        return cls(
            engine_time_unit=str(payload["engine_time_unit"]),
            id_namespace_cardinality=dict(payload["id_namespace_cardinality"]),
            manifest_version=str(payload["manifest_version"]),
            sources=tuple(DatasetSourceManifest.from_dict(entry) for entry in payload["sources"]),
            total_row_count=int(payload["total_row_count"]),
            total_warning_count=int(payload["total_warning_count"]),
        )

    @classmethod
    def from_json(cls, payload: str) -> "DatasetManifest":
        return cls.from_dict(json.loads(payload))


@dataclass(frozen=True)
class DatasetBundle:
    """Stage E1 validated source bundle plus canonical manifest."""

    manifest: DatasetManifest
    normalized_rows: Mapping[str, tuple[Mapping[str, Any], ...]]
    diagnostics: tuple[SourceValidationDiagnostic, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        frozen_rows = {
            source_name: tuple(dict(row) for row in rows)
            for source_name, rows in self.normalized_rows.items()
        }
        object.__setattr__(self, "normalized_rows", frozen_rows)
        object.__setattr__(self, "diagnostics", tuple(self.diagnostics))
