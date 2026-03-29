"""Stage E2 typed contracts for sequence and feature artifacts."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from zoneinfo import ZoneInfo

from ..ingestion import TimeUnit


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


@dataclass(frozen=True)
class SequenceBuilderConfig:
    """Stage E2 explicit temporal-binning configuration."""

    bucket_size_seconds: int
    timezone_id: str

    def __post_init__(self) -> None:
        if self.bucket_size_seconds <= 0:
            raise ValueError("bucket_size_seconds must be positive")
        if 86_400 % self.bucket_size_seconds != 0:
            raise ValueError("bucket_size_seconds must divide 86,400 exactly for auditable bucket indexing")
        timezone_id = _require_non_blank(self.timezone_id, "timezone_id")
        ZoneInfo(timezone_id)
        object.__setattr__(self, "timezone_id", timezone_id)

    def bucket_size_ticks(self, engine_time_unit: TimeUnit) -> int:
        if engine_time_unit == TimeUnit.SECONDS:
            return self.bucket_size_seconds
        if engine_time_unit == TimeUnit.MILLISECONDS:
            return self.bucket_size_seconds * 1_000
        raise ValueError(f"unsupported engine_time_unit: {engine_time_unit}")


@dataclass(frozen=True)
class SequenceRow:
    """Stage E2 one sequence observation enriched with deterministic feature columns."""

    sequence_scope: str
    feed_kind: str
    source_name: str
    id_namespace: str
    subject_id: str
    timestamp_field: str
    timestamp_ticks: int
    window_start_ticks: int
    day_of_week: int
    bucket_index: int
    sequence_index: int
    recency_gap_ticks: int
    persistence_run_length: int
    periodicity_bucket_count: int
    corridor_activity_count: int
    travel_time: float | None = None
    speed_factor_observed: float | None = None
    event_code: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope"))
        object.__setattr__(self, "feed_kind", _require_non_blank(self.feed_kind, "feed_kind"))
        object.__setattr__(self, "source_name", _require_non_blank(self.source_name, "source_name"))
        object.__setattr__(self, "id_namespace", _require_non_blank(self.id_namespace, "id_namespace"))
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "timestamp_field", _require_non_blank(self.timestamp_field, "timestamp_field"))

    def to_dict(self) -> dict[str, Any]:
        return {
            "bucket_index": self.bucket_index,
            "corridor_activity_count": self.corridor_activity_count,
            "day_of_week": self.day_of_week,
            "event_code": self.event_code,
            "feed_kind": self.feed_kind,
            "id_namespace": self.id_namespace,
            "periodicity_bucket_count": self.periodicity_bucket_count,
            "persistence_run_length": self.persistence_run_length,
            "recency_gap_ticks": self.recency_gap_ticks,
            "sequence_index": self.sequence_index,
            "sequence_scope": self.sequence_scope,
            "source_name": self.source_name,
            "speed_factor_observed": self.speed_factor_observed,
            "subject_id": self.subject_id,
            "timestamp_field": self.timestamp_field,
            "timestamp_ticks": self.timestamp_ticks,
            "travel_time": self.travel_time,
            "window_start_ticks": self.window_start_ticks,
        }

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "SequenceRow":
        return cls(
            bucket_index=int(payload["bucket_index"]),
            corridor_activity_count=int(payload["corridor_activity_count"]),
            day_of_week=int(payload["day_of_week"]),
            event_code=payload.get("event_code"),
            feed_kind=str(payload["feed_kind"]),
            id_namespace=str(payload["id_namespace"]),
            periodicity_bucket_count=int(payload["periodicity_bucket_count"]),
            persistence_run_length=int(payload["persistence_run_length"]),
            recency_gap_ticks=int(payload["recency_gap_ticks"]),
            sequence_index=int(payload["sequence_index"]),
            sequence_scope=str(payload["sequence_scope"]),
            source_name=str(payload["source_name"]),
            speed_factor_observed=None if payload.get("speed_factor_observed") is None else float(payload["speed_factor_observed"]),
            subject_id=str(payload["subject_id"]),
            timestamp_field=str(payload["timestamp_field"]),
            timestamp_ticks=int(payload["timestamp_ticks"]),
            travel_time=None if payload.get("travel_time") is None else float(payload["travel_time"]),
            window_start_ticks=int(payload["window_start_ticks"]),
        )


@dataclass(frozen=True)
class SequenceDatasetArtifact:
    """Stage E2 canonical sequence dataset artifact."""

    manifest_version: str
    engine_time_unit: str
    bucket_size_seconds: int
    timezone_id: str
    source_names: tuple[str, ...]
    rows: tuple[SequenceRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "engine_time_unit", _require_non_blank(self.engine_time_unit, "engine_time_unit"))
        object.__setattr__(self, "timezone_id", _require_non_blank(self.timezone_id, "timezone_id"))
        object.__setattr__(self, "source_names", tuple(self.source_names))
        object.__setattr__(self, "rows", tuple(self.rows))

    def to_pylist(self) -> list[dict[str, Any]]:
        return [row.to_dict() for row in self.rows]


@dataclass(frozen=True)
class CorridorBucketFrequencyRow:
    """Stage E2 canonical historical corridor-bucket frequency row."""

    corridor_id: str
    day_of_week: int
    bucket_index: int
    observation_count: int
    total_corridor_observations: int
    historical_bucket_frequency: float

    def __post_init__(self) -> None:
        object.__setattr__(self, "corridor_id", _require_non_blank(self.corridor_id, "corridor_id"))

    def to_dict(self) -> dict[str, Any]:
        return {
            "bucket_index": self.bucket_index,
            "corridor_id": self.corridor_id,
            "day_of_week": self.day_of_week,
            "historical_bucket_frequency": self.historical_bucket_frequency,
            "observation_count": self.observation_count,
            "total_corridor_observations": self.total_corridor_observations,
        }

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "CorridorBucketFrequencyRow":
        return cls(
            bucket_index=int(payload["bucket_index"]),
            corridor_id=str(payload["corridor_id"]),
            day_of_week=int(payload["day_of_week"]),
            historical_bucket_frequency=float(payload["historical_bucket_frequency"]),
            observation_count=int(payload["observation_count"]),
            total_corridor_observations=int(payload["total_corridor_observations"]),
        )


@dataclass(frozen=True)
class CorridorBucketFrequencyArtifact:
    """Stage E2 canonical corridor-bucket frequency artifact."""

    manifest_version: str
    engine_time_unit: str
    bucket_size_seconds: int
    timezone_id: str
    rows: tuple[CorridorBucketFrequencyRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "engine_time_unit", _require_non_blank(self.engine_time_unit, "engine_time_unit"))
        object.__setattr__(self, "timezone_id", _require_non_blank(self.timezone_id, "timezone_id"))
        object.__setattr__(self, "rows", tuple(self.rows))

    def to_pylist(self) -> list[dict[str, Any]]:
        return [row.to_dict() for row in self.rows]
