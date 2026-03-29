"""Stage F2 typed contracts for joined telemetry-feedback export artifacts."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


def _normalize_optional_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


@dataclass(frozen=True)
class TelemetryEventRow:
    """Stage F2 one joined served-prediction plus outcome-feedback export row."""

    result_kind: str
    prediction_id: str
    result_set_id: str
    served_at: str
    feedback_recorded_at: str | None
    outcome_status: str
    complete: bool
    caller_hash: str
    topology_version_id: str
    model_version: str
    source_data_lineage_hash: str
    change_set_hash: str
    trait_bundle_id: str | None
    trait_hash: str | None
    execution_profile_id: str | None
    quarantine_snapshot_id: str | None
    scenario_bundle_id: str
    scenario_count: int
    scenario_ids: tuple[str, ...]
    scenario_labels: tuple[str, ...]
    scenario_probabilities: tuple[float, ...]
    departure_ticks: int
    horizon_ticks: int
    preferred_objective: str | None
    top_k_alternatives: int | None
    predicted_expected_cost_seconds: float | None
    predicted_robust_cost_seconds: float | None
    matrix_source_count: int | None
    matrix_target_count: int | None
    observed_at_ticks: int | None
    observed_arrival_ticks: int | None
    observed_cost_seconds: float | None
    observation_count: int | None
    partition_date: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "result_kind", _require_non_blank(self.result_kind, "result_kind").upper())
        object.__setattr__(self, "prediction_id", _require_non_blank(self.prediction_id, "prediction_id"))
        object.__setattr__(self, "result_set_id", _require_non_blank(self.result_set_id, "result_set_id"))
        object.__setattr__(self, "served_at", _require_non_blank(self.served_at, "served_at"))
        object.__setattr__(self, "feedback_recorded_at", _normalize_optional_text(self.feedback_recorded_at))
        object.__setattr__(self, "outcome_status", _require_non_blank(self.outcome_status, "outcome_status").upper())
        object.__setattr__(self, "caller_hash", _require_non_blank(self.caller_hash, "caller_hash"))
        object.__setattr__(self, "topology_version_id", _require_non_blank(self.topology_version_id, "topology_version_id"))
        object.__setattr__(self, "model_version", _require_non_blank(self.model_version, "model_version"))
        object.__setattr__(
            self,
            "source_data_lineage_hash",
            _require_non_blank(self.source_data_lineage_hash, "source_data_lineage_hash"),
        )
        object.__setattr__(self, "change_set_hash", _require_non_blank(self.change_set_hash, "change_set_hash"))
        object.__setattr__(self, "trait_bundle_id", _normalize_optional_text(self.trait_bundle_id))
        object.__setattr__(self, "trait_hash", _normalize_optional_text(self.trait_hash))
        object.__setattr__(self, "execution_profile_id", _normalize_optional_text(self.execution_profile_id))
        object.__setattr__(self, "quarantine_snapshot_id", _normalize_optional_text(self.quarantine_snapshot_id))
        object.__setattr__(self, "scenario_bundle_id", _require_non_blank(self.scenario_bundle_id, "scenario_bundle_id"))
        object.__setattr__(self, "scenario_ids", tuple(_require_non_blank(item, "scenario_id") for item in self.scenario_ids))
        object.__setattr__(self, "scenario_labels", tuple(str(item) for item in self.scenario_labels))
        object.__setattr__(self, "scenario_probabilities", tuple(float(item) for item in self.scenario_probabilities))
        object.__setattr__(self, "preferred_objective", _normalize_optional_text(self.preferred_objective))
        object.__setattr__(self, "partition_date", _require_non_blank(self.partition_date, "partition_date"))
        if self.scenario_count != len(self.scenario_ids):
            raise ValueError("scenario_count must match len(scenario_ids)")
        if len(self.scenario_ids) != len(self.scenario_labels):
            raise ValueError("scenario_ids and scenario_labels must have equal length")
        if len(self.scenario_ids) != len(self.scenario_probabilities):
            raise ValueError("scenario_ids and scenario_probabilities must have equal length")
        if self.departure_ticks < 0:
            raise ValueError("departure_ticks must be non-negative")
        if self.horizon_ticks < 0:
            raise ValueError("horizon_ticks must be non-negative")
        if any(probability < 0.0 or probability > 1.0 for probability in self.scenario_probabilities):
            raise ValueError("scenario_probabilities must stay within [0.0, 1.0]")
        if self.top_k_alternatives is not None and self.top_k_alternatives <= 0:
            raise ValueError("top_k_alternatives must be positive when provided")
        if self.matrix_source_count is not None and self.matrix_source_count <= 0:
            raise ValueError("matrix_source_count must be positive when provided")
        if self.matrix_target_count is not None and self.matrix_target_count <= 0:
            raise ValueError("matrix_target_count must be positive when provided")
        if self.observed_at_ticks is not None and self.observed_at_ticks < 0:
            raise ValueError("observed_at_ticks must be non-negative when provided")
        if self.observed_arrival_ticks is not None and self.observed_arrival_ticks < 0:
            raise ValueError("observed_arrival_ticks must be non-negative when provided")
        if self.predicted_expected_cost_seconds is not None and self.predicted_expected_cost_seconds < 0.0:
            raise ValueError("predicted_expected_cost_seconds must be non-negative when provided")
        if self.predicted_robust_cost_seconds is not None and self.predicted_robust_cost_seconds < 0.0:
            raise ValueError("predicted_robust_cost_seconds must be non-negative when provided")
        if self.observed_cost_seconds is not None and self.observed_cost_seconds < 0.0:
            raise ValueError("observed_cost_seconds must be non-negative when provided")
        if self.observation_count is not None and self.observation_count <= 0:
            raise ValueError("observation_count must be positive when provided")

    def to_dict(self) -> dict[str, Any]:
        return {
            "result_kind": self.result_kind,
            "prediction_id": self.prediction_id,
            "result_set_id": self.result_set_id,
            "served_at": self.served_at,
            "feedback_recorded_at": self.feedback_recorded_at,
            "outcome_status": self.outcome_status,
            "complete": self.complete,
            "caller_hash": self.caller_hash,
            "topology_version_id": self.topology_version_id,
            "model_version": self.model_version,
            "source_data_lineage_hash": self.source_data_lineage_hash,
            "change_set_hash": self.change_set_hash,
            "trait_bundle_id": self.trait_bundle_id,
            "trait_hash": self.trait_hash,
            "execution_profile_id": self.execution_profile_id,
            "quarantine_snapshot_id": self.quarantine_snapshot_id,
            "scenario_bundle_id": self.scenario_bundle_id,
            "scenario_count": self.scenario_count,
            "scenario_ids": list(self.scenario_ids),
            "scenario_labels": list(self.scenario_labels),
            "scenario_probabilities": list(self.scenario_probabilities),
            "departure_ticks": self.departure_ticks,
            "horizon_ticks": self.horizon_ticks,
            "preferred_objective": self.preferred_objective,
            "top_k_alternatives": self.top_k_alternatives,
            "predicted_expected_cost_seconds": self.predicted_expected_cost_seconds,
            "predicted_robust_cost_seconds": self.predicted_robust_cost_seconds,
            "matrix_source_count": self.matrix_source_count,
            "matrix_target_count": self.matrix_target_count,
            "observed_at_ticks": self.observed_at_ticks,
            "observed_arrival_ticks": self.observed_arrival_ticks,
            "observed_cost_seconds": self.observed_cost_seconds,
            "observation_count": self.observation_count,
            "partition_date": self.partition_date,
        }

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "TelemetryEventRow":
        return cls(
            result_kind=str(payload["result_kind"]),
            prediction_id=str(payload["prediction_id"]),
            result_set_id=str(payload["result_set_id"]),
            served_at=str(payload["served_at"]),
            feedback_recorded_at=None if payload.get("feedback_recorded_at") is None else str(payload["feedback_recorded_at"]),
            outcome_status=str(payload["outcome_status"]),
            complete=bool(payload["complete"]),
            caller_hash=str(payload["caller_hash"]),
            topology_version_id=str(payload["topology_version_id"]),
            model_version=str(payload["model_version"]),
            source_data_lineage_hash=str(payload["source_data_lineage_hash"]),
            change_set_hash=str(payload["change_set_hash"]),
            trait_bundle_id=None if payload.get("trait_bundle_id") is None else str(payload["trait_bundle_id"]),
            trait_hash=None if payload.get("trait_hash") is None else str(payload["trait_hash"]),
            execution_profile_id=None if payload.get("execution_profile_id") is None else str(payload["execution_profile_id"]),
            quarantine_snapshot_id=None if payload.get("quarantine_snapshot_id") is None else str(payload["quarantine_snapshot_id"]),
            scenario_bundle_id=str(payload["scenario_bundle_id"]),
            scenario_count=int(payload["scenario_count"]),
            scenario_ids=tuple(payload["scenario_ids"]),
            scenario_labels=tuple(payload["scenario_labels"]),
            scenario_probabilities=tuple(float(item) for item in payload["scenario_probabilities"]),
            departure_ticks=int(payload["departure_ticks"]),
            horizon_ticks=int(payload["horizon_ticks"]),
            preferred_objective=None if payload.get("preferred_objective") is None else str(payload["preferred_objective"]),
            top_k_alternatives=None if payload.get("top_k_alternatives") is None else int(payload["top_k_alternatives"]),
            predicted_expected_cost_seconds=None
            if payload.get("predicted_expected_cost_seconds") is None
            else float(payload["predicted_expected_cost_seconds"]),
            predicted_robust_cost_seconds=None
            if payload.get("predicted_robust_cost_seconds") is None
            else float(payload["predicted_robust_cost_seconds"]),
            matrix_source_count=None if payload.get("matrix_source_count") is None else int(payload["matrix_source_count"]),
            matrix_target_count=None if payload.get("matrix_target_count") is None else int(payload["matrix_target_count"]),
            observed_at_ticks=None if payload.get("observed_at_ticks") is None else int(payload["observed_at_ticks"]),
            observed_arrival_ticks=None
            if payload.get("observed_arrival_ticks") is None
            else int(payload["observed_arrival_ticks"]),
            observed_cost_seconds=None
            if payload.get("observed_cost_seconds") is None
            else float(payload["observed_cost_seconds"]),
            observation_count=None if payload.get("observation_count") is None else int(payload["observation_count"]),
            partition_date=str(payload["partition_date"]),
        )


@dataclass(frozen=True)
class TelemetryEventArtifact:
    """Stage F2 canonical telemetry-event parquet artifact metadata plus rows."""

    manifest_version: str
    exported_at: str
    result_kind_filter: str | None
    topology_version_filter: str | None
    scenario_bundle_filter: str | None
    trait_hash_filter: str | None
    complete_only: bool
    rows: tuple[TelemetryEventRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "exported_at", _require_non_blank(self.exported_at, "exported_at"))
        object.__setattr__(self, "result_kind_filter", _normalize_optional_text(self.result_kind_filter))
        object.__setattr__(self, "topology_version_filter", _normalize_optional_text(self.topology_version_filter))
        object.__setattr__(self, "scenario_bundle_filter", _normalize_optional_text(self.scenario_bundle_filter))
        object.__setattr__(self, "trait_hash_filter", _normalize_optional_text(self.trait_hash_filter))
        object.__setattr__(self, "rows", tuple(self.rows))

    def metadata(self) -> dict[str, Any]:
        return {
            "manifest_version": self.manifest_version,
            "exported_at": self.exported_at,
            "result_kind_filter": self.result_kind_filter,
            "topology_version_filter": self.topology_version_filter,
            "scenario_bundle_filter": self.scenario_bundle_filter,
            "trait_hash_filter": self.trait_hash_filter,
            "complete_only": self.complete_only,
        }

    def to_pylist(self) -> list[dict[str, Any]]:
        return [row.to_dict() for row in self.rows]
