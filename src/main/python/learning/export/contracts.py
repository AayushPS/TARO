"""Stage E5 typed contracts for reproducibility and evidence packaging."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

from ..ingestion import DatasetManifest

_PACKAGE_SCOPE_RELEASE_ONLY = "release_only"
_PACKAGE_SCOPE_RELEASE_AND_RESEARCH = "release_and_research"


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


def _require_fraction(value: float, field_name: str) -> float:
    if not 0.0 <= value <= 1.0:
        raise ValueError(f"{field_name} must be within [0.0, 1.0]")
    return value


def _freeze_mapping(mapping: Mapping[str, Any]) -> dict[str, Any]:
    return {
        str(key): value
        for key, value in mapping.items()
    }


def _normalize_package_scope(value: str) -> str:
    normalized = _require_non_blank(value, "package_scope").lower()
    if normalized not in {_PACKAGE_SCOPE_RELEASE_ONLY, _PACKAGE_SCOPE_RELEASE_AND_RESEARCH}:
        raise ValueError(f"unsupported package_scope: {value}")
    return normalized


@dataclass(frozen=True)
class LearningConfigArtifact:
    """Stage E5 frozen learning/calibration config artifact."""

    manifest_version: str
    model_class: str
    learning_mode: str
    package_scope: str
    training_seed: int
    history_window_buckets: int
    recency_half_life_buckets: int
    minimum_training_observations: int
    enabled_features: tuple[str, ...]
    allowed_sequence_scopes: tuple[str, ...]
    minimum_confidence: float
    minimum_relative_improvement: float
    maximum_prior_adjustment: float
    preferential_attachment_range: float
    prior_floor: float
    prior_ceiling: float
    dataset_manifest_hash: str
    training_bundle_hash: str
    calibration_bundle_hash: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "model_class", _require_non_blank(self.model_class, "model_class"))
        object.__setattr__(self, "learning_mode", _require_non_blank(self.learning_mode, "learning_mode"))
        object.__setattr__(self, "package_scope", _normalize_package_scope(self.package_scope))
        if self.training_seed < 0:
            raise ValueError("training_seed must be non-negative")
        if self.history_window_buckets <= 0:
            raise ValueError("history_window_buckets must be positive")
        if self.recency_half_life_buckets <= 0:
            raise ValueError("recency_half_life_buckets must be positive")
        if self.minimum_training_observations <= 0:
            raise ValueError("minimum_training_observations must be positive")
        object.__setattr__(self, "enabled_features", tuple(self.enabled_features))
        object.__setattr__(self, "allowed_sequence_scopes", tuple(self.allowed_sequence_scopes))
        object.__setattr__(self, "minimum_confidence", _require_fraction(self.minimum_confidence, "minimum_confidence"))
        object.__setattr__(
            self,
            "minimum_relative_improvement",
            _require_fraction(self.minimum_relative_improvement, "minimum_relative_improvement"),
        )
        object.__setattr__(
            self,
            "maximum_prior_adjustment",
            _require_fraction(self.maximum_prior_adjustment, "maximum_prior_adjustment"),
        )
        object.__setattr__(
            self,
            "preferential_attachment_range",
            _require_fraction(self.preferential_attachment_range, "preferential_attachment_range"),
        )
        object.__setattr__(self, "prior_floor", _require_fraction(self.prior_floor, "prior_floor"))
        object.__setattr__(self, "prior_ceiling", _require_fraction(self.prior_ceiling, "prior_ceiling"))
        object.__setattr__(
            self,
            "dataset_manifest_hash",
            _require_non_blank(self.dataset_manifest_hash, "dataset_manifest_hash"),
        )
        object.__setattr__(
            self,
            "training_bundle_hash",
            _require_non_blank(self.training_bundle_hash, "training_bundle_hash"),
        )
        object.__setattr__(
            self,
            "calibration_bundle_hash",
            _require_non_blank(self.calibration_bundle_hash, "calibration_bundle_hash"),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "manifest_version": self.manifest_version,
            "model_class": self.model_class,
            "learning_mode": self.learning_mode,
            "package_scope": self.package_scope,
            "training_seed": self.training_seed,
            "history_window_buckets": self.history_window_buckets,
            "recency_half_life_buckets": self.recency_half_life_buckets,
            "minimum_training_observations": self.minimum_training_observations,
            "enabled_features": list(self.enabled_features),
            "allowed_sequence_scopes": list(self.allowed_sequence_scopes),
            "minimum_confidence": self.minimum_confidence,
            "minimum_relative_improvement": self.minimum_relative_improvement,
            "maximum_prior_adjustment": self.maximum_prior_adjustment,
            "preferential_attachment_range": self.preferential_attachment_range,
            "prior_floor": self.prior_floor,
            "prior_ceiling": self.prior_ceiling,
            "dataset_manifest_hash": self.dataset_manifest_hash,
            "training_bundle_hash": self.training_bundle_hash,
            "calibration_bundle_hash": self.calibration_bundle_hash,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "LearningConfigArtifact":
        return cls(
            manifest_version=str(payload["manifest_version"]),
            model_class=str(payload["model_class"]),
            learning_mode=str(payload["learning_mode"]),
            package_scope=str(payload["package_scope"]),
            training_seed=int(payload["training_seed"]),
            history_window_buckets=int(payload["history_window_buckets"]),
            recency_half_life_buckets=int(payload["recency_half_life_buckets"]),
            minimum_training_observations=int(payload["minimum_training_observations"]),
            enabled_features=tuple(payload["enabled_features"]),
            allowed_sequence_scopes=tuple(payload["allowed_sequence_scopes"]),
            minimum_confidence=float(payload["minimum_confidence"]),
            minimum_relative_improvement=float(payload["minimum_relative_improvement"]),
            maximum_prior_adjustment=float(payload["maximum_prior_adjustment"]),
            preferential_attachment_range=float(payload["preferential_attachment_range"]),
            prior_floor=float(payload["prior_floor"]),
            prior_ceiling=float(payload["prior_ceiling"]),
            dataset_manifest_hash=str(payload["dataset_manifest_hash"]),
            training_bundle_hash=str(payload["training_bundle_hash"]),
            calibration_bundle_hash=str(payload["calibration_bundle_hash"]),
        )


@dataclass(frozen=True)
class CandidateReportRow:
    """Stage E5 one proposal-level candidate report row."""

    candidate_id: str
    sequence_scope: str
    subject_id: str
    day_of_week: int
    bucket_index: int
    baseline_prediction: float
    feature_rich_prediction: float
    calibrated_prediction: float
    confidence: float
    relative_improvement: float
    accepted: bool
    rejection_reason: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "candidate_id", _require_non_blank(self.candidate_id, "candidate_id"))
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "confidence", _require_fraction(self.confidence, "confidence"))
        if self.relative_improvement < 0.0:
            raise ValueError("relative_improvement must be >= 0.0")
        if self.rejection_reason is not None:
            object.__setattr__(
                self,
                "rejection_reason",
                _require_non_blank(self.rejection_reason, "rejection_reason").lower(),
            )

    def to_dict(self) -> dict[str, Any]:
        return {
            "candidate_id": self.candidate_id,
            "sequence_scope": self.sequence_scope,
            "subject_id": self.subject_id,
            "day_of_week": self.day_of_week,
            "bucket_index": self.bucket_index,
            "baseline_prediction": self.baseline_prediction,
            "feature_rich_prediction": self.feature_rich_prediction,
            "calibrated_prediction": self.calibrated_prediction,
            "confidence": self.confidence,
            "relative_improvement": self.relative_improvement,
            "accepted": self.accepted,
            "rejection_reason": self.rejection_reason,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "CandidateReportRow":
        return cls(
            candidate_id=str(payload["candidate_id"]),
            sequence_scope=str(payload["sequence_scope"]),
            subject_id=str(payload["subject_id"]),
            day_of_week=int(payload["day_of_week"]),
            bucket_index=int(payload["bucket_index"]),
            baseline_prediction=float(payload["baseline_prediction"]),
            feature_rich_prediction=float(payload["feature_rich_prediction"]),
            calibrated_prediction=float(payload["calibrated_prediction"]),
            confidence=float(payload["confidence"]),
            relative_improvement=float(payload["relative_improvement"]),
            accepted=bool(payload["accepted"]),
            rejection_reason=None if payload.get("rejection_reason") is None else str(payload["rejection_reason"]),
        )


@dataclass(frozen=True)
class CandidateReportArtifact:
    """Stage E5 proposal-level candidate report artifact."""

    manifest_version: str
    release_artifact_id: str
    dataset_manifest_hash: str
    training_bundle_hash: str
    calibration_bundle_hash: str
    learning_config_hash: str
    rows: tuple[CandidateReportRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(
            self,
            "release_artifact_id",
            _require_non_blank(self.release_artifact_id, "release_artifact_id"),
        )
        object.__setattr__(
            self,
            "dataset_manifest_hash",
            _require_non_blank(self.dataset_manifest_hash, "dataset_manifest_hash"),
        )
        object.__setattr__(
            self,
            "training_bundle_hash",
            _require_non_blank(self.training_bundle_hash, "training_bundle_hash"),
        )
        object.__setattr__(
            self,
            "calibration_bundle_hash",
            _require_non_blank(self.calibration_bundle_hash, "calibration_bundle_hash"),
        )
        object.__setattr__(
            self,
            "learning_config_hash",
            _require_non_blank(self.learning_config_hash, "learning_config_hash"),
        )
        object.__setattr__(self, "rows", tuple(self.rows))


@dataclass(frozen=True)
class RefinementDecisionRow:
    """Stage E5 accepted/rejected refinement decision row."""

    decision_id: str
    decision_type: str
    subject_id: str
    day_of_week: int
    bucket_index: int
    accepted: bool
    rejection_reason: str | None
    baseline_value: float
    calibrated_value: float
    confidence: float
    evidence_response: float | None = None
    preferential_attachment_adjustment: float | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "decision_id", _require_non_blank(self.decision_id, "decision_id"))
        object.__setattr__(self, "decision_type", _require_non_blank(self.decision_type, "decision_type").lower())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "confidence", _require_fraction(self.confidence, "confidence"))
        if self.evidence_response is not None:
            object.__setattr__(
                self,
                "evidence_response",
                _require_fraction(self.evidence_response, "evidence_response"),
            )
        if self.preferential_attachment_adjustment is not None and not -1.0 <= self.preferential_attachment_adjustment <= 1.0:
            raise ValueError("preferential_attachment_adjustment must be within [-1.0, 1.0]")
        if self.rejection_reason is not None:
            object.__setattr__(
                self,
                "rejection_reason",
                _require_non_blank(self.rejection_reason, "rejection_reason").lower(),
            )

    def to_dict(self) -> dict[str, Any]:
        return {
            "decision_id": self.decision_id,
            "decision_type": self.decision_type,
            "subject_id": self.subject_id,
            "day_of_week": self.day_of_week,
            "bucket_index": self.bucket_index,
            "accepted": self.accepted,
            "rejection_reason": self.rejection_reason,
            "baseline_value": self.baseline_value,
            "calibrated_value": self.calibrated_value,
            "confidence": self.confidence,
            "evidence_response": self.evidence_response,
            "preferential_attachment_adjustment": self.preferential_attachment_adjustment,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "RefinementDecisionRow":
        return cls(
            decision_id=str(payload["decision_id"]),
            decision_type=str(payload["decision_type"]),
            subject_id=str(payload["subject_id"]),
            day_of_week=int(payload["day_of_week"]),
            bucket_index=int(payload["bucket_index"]),
            accepted=bool(payload["accepted"]),
            rejection_reason=None if payload.get("rejection_reason") is None else str(payload["rejection_reason"]),
            baseline_value=float(payload["baseline_value"]),
            calibrated_value=float(payload["calibrated_value"]),
            confidence=float(payload["confidence"]),
            evidence_response=None if payload.get("evidence_response") is None else float(payload["evidence_response"]),
            preferential_attachment_adjustment=(
                None
                if payload.get("preferential_attachment_adjustment") is None
                else float(payload["preferential_attachment_adjustment"])
            ),
        )


@dataclass(frozen=True)
class RefinementDecisionArtifact:
    """Stage E5 final accepted/rejected decision artifact."""

    manifest_version: str
    release_artifact_id: str
    dataset_manifest_hash: str
    calibration_bundle_hash: str
    learning_config_hash: str
    rows: tuple[RefinementDecisionRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(
            self,
            "release_artifact_id",
            _require_non_blank(self.release_artifact_id, "release_artifact_id"),
        )
        object.__setattr__(
            self,
            "dataset_manifest_hash",
            _require_non_blank(self.dataset_manifest_hash, "dataset_manifest_hash"),
        )
        object.__setattr__(
            self,
            "calibration_bundle_hash",
            _require_non_blank(self.calibration_bundle_hash, "calibration_bundle_hash"),
        )
        object.__setattr__(
            self,
            "learning_config_hash",
            _require_non_blank(self.learning_config_hash, "learning_config_hash"),
        )
        object.__setattr__(self, "rows", tuple(self.rows))


@dataclass(frozen=True)
class ValidationReportArtifact:
    """Stage E5 validation report with quality, correctness, and system metrics."""

    manifest_version: str
    package_scope: str
    release_artifact_id: str
    dataset_manifest_hash: str
    learning_config_hash: str
    candidate_report_hash: str
    refinement_decisions_hash: str
    quality_metrics: Mapping[str, float]
    correctness_gates: Mapping[str, bool]
    system_metrics: Mapping[str, float]
    failures: tuple[str, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "package_scope", _normalize_package_scope(self.package_scope))
        object.__setattr__(
            self,
            "release_artifact_id",
            _require_non_blank(self.release_artifact_id, "release_artifact_id"),
        )
        object.__setattr__(
            self,
            "dataset_manifest_hash",
            _require_non_blank(self.dataset_manifest_hash, "dataset_manifest_hash"),
        )
        object.__setattr__(
            self,
            "learning_config_hash",
            _require_non_blank(self.learning_config_hash, "learning_config_hash"),
        )
        object.__setattr__(
            self,
            "candidate_report_hash",
            _require_non_blank(self.candidate_report_hash, "candidate_report_hash"),
        )
        object.__setattr__(
            self,
            "refinement_decisions_hash",
            _require_non_blank(self.refinement_decisions_hash, "refinement_decisions_hash"),
        )
        object.__setattr__(self, "quality_metrics", _freeze_mapping(self.quality_metrics))
        object.__setattr__(self, "correctness_gates", _freeze_mapping(self.correctness_gates))
        object.__setattr__(self, "system_metrics", _freeze_mapping(self.system_metrics))
        object.__setattr__(self, "failures", tuple(self.failures))

    def to_dict(self) -> dict[str, Any]:
        return {
            "manifest_version": self.manifest_version,
            "package_scope": self.package_scope,
            "release_artifact_id": self.release_artifact_id,
            "dataset_manifest_hash": self.dataset_manifest_hash,
            "learning_config_hash": self.learning_config_hash,
            "candidate_report_hash": self.candidate_report_hash,
            "refinement_decisions_hash": self.refinement_decisions_hash,
            "quality_metrics": dict(self.quality_metrics),
            "correctness_gates": dict(self.correctness_gates),
            "system_metrics": dict(self.system_metrics),
            "failures": list(self.failures),
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "ValidationReportArtifact":
        return cls(
            manifest_version=str(payload["manifest_version"]),
            package_scope=str(payload["package_scope"]),
            release_artifact_id=str(payload["release_artifact_id"]),
            dataset_manifest_hash=str(payload["dataset_manifest_hash"]),
            learning_config_hash=str(payload["learning_config_hash"]),
            candidate_report_hash=str(payload["candidate_report_hash"]),
            refinement_decisions_hash=str(payload["refinement_decisions_hash"]),
            quality_metrics={str(key): float(value) for key, value in dict(payload["quality_metrics"]).items()},
            correctness_gates={str(key): bool(value) for key, value in dict(payload["correctness_gates"]).items()},
            system_metrics={str(key): float(value) for key, value in dict(payload["system_metrics"]).items()},
            failures=tuple(str(value) for value in payload["failures"]),
        )


@dataclass(frozen=True)
class ResearchClaimRecord:
    """Stage E5 one frozen research claim bound to validation evidence."""

    claim_id: str
    claim_text: str
    metric_name: str
    metric_value: float
    validation_metric_key: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "claim_id", _require_non_blank(self.claim_id, "claim_id"))
        object.__setattr__(self, "claim_text", _require_non_blank(self.claim_text, "claim_text"))
        object.__setattr__(self, "metric_name", _require_non_blank(self.metric_name, "metric_name"))
        object.__setattr__(
            self,
            "validation_metric_key",
            _require_non_blank(self.validation_metric_key, "validation_metric_key"),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "claim_id": self.claim_id,
            "claim_text": self.claim_text,
            "metric_name": self.metric_name,
            "metric_value": self.metric_value,
            "validation_metric_key": self.validation_metric_key,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "ResearchClaimRecord":
        return cls(
            claim_id=str(payload["claim_id"]),
            claim_text=str(payload["claim_text"]),
            metric_name=str(payload["metric_name"]),
            metric_value=float(payload["metric_value"]),
            validation_metric_key=str(payload["validation_metric_key"]),
        )


@dataclass(frozen=True)
class ResearchClaimArtifact:
    """Stage E5 research-claim artifact frozen against release evidence."""

    manifest_version: str
    release_artifact_id: str
    validation_report_hash: str
    candidate_report_hash: str
    refinement_decisions_hash: str
    claims: tuple[ResearchClaimRecord, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(
            self,
            "release_artifact_id",
            _require_non_blank(self.release_artifact_id, "release_artifact_id"),
        )
        object.__setattr__(
            self,
            "validation_report_hash",
            _require_non_blank(self.validation_report_hash, "validation_report_hash"),
        )
        object.__setattr__(
            self,
            "candidate_report_hash",
            _require_non_blank(self.candidate_report_hash, "candidate_report_hash"),
        )
        object.__setattr__(
            self,
            "refinement_decisions_hash",
            _require_non_blank(self.refinement_decisions_hash, "refinement_decisions_hash"),
        )
        object.__setattr__(self, "claims", tuple(self.claims))

    def to_dict(self) -> dict[str, Any]:
        return {
            "manifest_version": self.manifest_version,
            "release_artifact_id": self.release_artifact_id,
            "validation_report_hash": self.validation_report_hash,
            "candidate_report_hash": self.candidate_report_hash,
            "refinement_decisions_hash": self.refinement_decisions_hash,
            "claims": [claim.to_dict() for claim in self.claims],
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "ResearchClaimArtifact":
        return cls(
            manifest_version=str(payload["manifest_version"]),
            release_artifact_id=str(payload["release_artifact_id"]),
            validation_report_hash=str(payload["validation_report_hash"]),
            candidate_report_hash=str(payload["candidate_report_hash"]),
            refinement_decisions_hash=str(payload["refinement_decisions_hash"]),
            claims=tuple(ResearchClaimRecord.from_dict(entry) for entry in payload["claims"]),
        )


@dataclass(frozen=True)
class ReleaseArtifactDescriptor:
    """Stage E5 logical release-artifact lineage descriptor."""

    manifest_version: str
    package_scope: str
    release_artifact_id: str
    model_class: str
    learning_mode: str
    dataset_manifest_hash: str
    training_bundle_hash: str
    calibration_bundle_hash: str
    learning_config_hash: str
    candidate_report_hash: str
    refinement_decisions_hash: str
    validation_report_hash: str
    research_claims_hash: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "package_scope", _normalize_package_scope(self.package_scope))
        object.__setattr__(
            self,
            "release_artifact_id",
            _require_non_blank(self.release_artifact_id, "release_artifact_id"),
        )
        object.__setattr__(self, "model_class", _require_non_blank(self.model_class, "model_class"))
        object.__setattr__(self, "learning_mode", _require_non_blank(self.learning_mode, "learning_mode"))
        object.__setattr__(
            self,
            "dataset_manifest_hash",
            _require_non_blank(self.dataset_manifest_hash, "dataset_manifest_hash"),
        )
        object.__setattr__(
            self,
            "training_bundle_hash",
            _require_non_blank(self.training_bundle_hash, "training_bundle_hash"),
        )
        object.__setattr__(
            self,
            "calibration_bundle_hash",
            _require_non_blank(self.calibration_bundle_hash, "calibration_bundle_hash"),
        )
        object.__setattr__(
            self,
            "learning_config_hash",
            _require_non_blank(self.learning_config_hash, "learning_config_hash"),
        )
        object.__setattr__(
            self,
            "candidate_report_hash",
            _require_non_blank(self.candidate_report_hash, "candidate_report_hash"),
        )
        object.__setattr__(
            self,
            "refinement_decisions_hash",
            _require_non_blank(self.refinement_decisions_hash, "refinement_decisions_hash"),
        )
        object.__setattr__(
            self,
            "validation_report_hash",
            _require_non_blank(self.validation_report_hash, "validation_report_hash"),
        )
        if self.package_scope == _PACKAGE_SCOPE_RELEASE_AND_RESEARCH and self.research_claims_hash is None:
            raise ValueError("research_claims_hash is required for release_and_research scope")
        if self.research_claims_hash is not None:
            object.__setattr__(
                self,
                "research_claims_hash",
                _require_non_blank(self.research_claims_hash, "research_claims_hash"),
            )

    def to_dict(self) -> dict[str, Any]:
        return {
            "manifest_version": self.manifest_version,
            "package_scope": self.package_scope,
            "release_artifact_id": self.release_artifact_id,
            "model_class": self.model_class,
            "learning_mode": self.learning_mode,
            "dataset_manifest_hash": self.dataset_manifest_hash,
            "training_bundle_hash": self.training_bundle_hash,
            "calibration_bundle_hash": self.calibration_bundle_hash,
            "learning_config_hash": self.learning_config_hash,
            "candidate_report_hash": self.candidate_report_hash,
            "refinement_decisions_hash": self.refinement_decisions_hash,
            "validation_report_hash": self.validation_report_hash,
            "research_claims_hash": self.research_claims_hash,
        }

    @classmethod
    def from_dict(cls, payload: Mapping[str, Any]) -> "ReleaseArtifactDescriptor":
        return cls(
            manifest_version=str(payload["manifest_version"]),
            package_scope=str(payload["package_scope"]),
            release_artifact_id=str(payload["release_artifact_id"]),
            model_class=str(payload["model_class"]),
            learning_mode=str(payload["learning_mode"]),
            dataset_manifest_hash=str(payload["dataset_manifest_hash"]),
            training_bundle_hash=str(payload["training_bundle_hash"]),
            calibration_bundle_hash=str(payload["calibration_bundle_hash"]),
            learning_config_hash=str(payload["learning_config_hash"]),
            candidate_report_hash=str(payload["candidate_report_hash"]),
            refinement_decisions_hash=str(payload["refinement_decisions_hash"]),
            validation_report_hash=str(payload["validation_report_hash"]),
            research_claims_hash=(
                None if payload.get("research_claims_hash") is None else str(payload["research_claims_hash"])
            ),
        )


@dataclass(frozen=True)
class ReproducibilityPackArtifact:
    """Stage E5 top-level reproducibility pack assembled from E1-E4 artifacts."""

    manifest_version: str
    package_scope: str
    release_artifact: ReleaseArtifactDescriptor
    dataset_manifest: DatasetManifest
    learning_config: LearningConfigArtifact
    candidate_report: CandidateReportArtifact
    refinement_decisions: RefinementDecisionArtifact
    validation_report: ValidationReportArtifact
    research_claims: ResearchClaimArtifact | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "package_scope", _normalize_package_scope(self.package_scope))
        if self.package_scope != self.release_artifact.package_scope:
            raise ValueError("package_scope must match release_artifact.package_scope")
        if self.package_scope != self.learning_config.package_scope:
            raise ValueError("package_scope must match learning_config.package_scope")
        if self.package_scope != self.validation_report.package_scope:
            raise ValueError("package_scope must match validation_report.package_scope")
        if self.release_artifact.release_artifact_id != self.candidate_report.release_artifact_id:
            raise ValueError("candidate_report.release_artifact_id must match release_artifact.release_artifact_id")
        if self.release_artifact.release_artifact_id != self.refinement_decisions.release_artifact_id:
            raise ValueError("refinement_decisions.release_artifact_id must match release_artifact.release_artifact_id")
        if self.release_artifact.release_artifact_id != self.validation_report.release_artifact_id:
            raise ValueError("validation_report.release_artifact_id must match release_artifact.release_artifact_id")
        if self.release_artifact.dataset_manifest_hash != self.learning_config.dataset_manifest_hash:
            raise ValueError("learning_config.dataset_manifest_hash must match release_artifact.dataset_manifest_hash")
        if self.release_artifact.dataset_manifest_hash != self.candidate_report.dataset_manifest_hash:
            raise ValueError("candidate_report.dataset_manifest_hash must match release_artifact.dataset_manifest_hash")
        if self.release_artifact.dataset_manifest_hash != self.refinement_decisions.dataset_manifest_hash:
            raise ValueError("refinement_decisions.dataset_manifest_hash must match release_artifact.dataset_manifest_hash")
        if self.release_artifact.dataset_manifest_hash != self.validation_report.dataset_manifest_hash:
            raise ValueError("validation_report.dataset_manifest_hash must match release_artifact.dataset_manifest_hash")
        if self.release_artifact.training_bundle_hash != self.learning_config.training_bundle_hash:
            raise ValueError("learning_config.training_bundle_hash must match release_artifact.training_bundle_hash")
        if self.release_artifact.training_bundle_hash != self.candidate_report.training_bundle_hash:
            raise ValueError("candidate_report.training_bundle_hash must match release_artifact.training_bundle_hash")
        if self.release_artifact.calibration_bundle_hash != self.learning_config.calibration_bundle_hash:
            raise ValueError("learning_config.calibration_bundle_hash must match release_artifact.calibration_bundle_hash")
        if self.release_artifact.calibration_bundle_hash != self.candidate_report.calibration_bundle_hash:
            raise ValueError("candidate_report.calibration_bundle_hash must match release_artifact.calibration_bundle_hash")
        if self.release_artifact.calibration_bundle_hash != self.refinement_decisions.calibration_bundle_hash:
            raise ValueError("refinement_decisions.calibration_bundle_hash must match release_artifact.calibration_bundle_hash")
        if self.release_artifact.learning_config_hash != self.candidate_report.learning_config_hash:
            raise ValueError("candidate_report.learning_config_hash must match release_artifact.learning_config_hash")
        if self.release_artifact.learning_config_hash != self.refinement_decisions.learning_config_hash:
            raise ValueError("refinement_decisions.learning_config_hash must match release_artifact.learning_config_hash")
        if self.release_artifact.learning_config_hash != self.validation_report.learning_config_hash:
            raise ValueError("validation_report.learning_config_hash must match release_artifact.learning_config_hash")
        if self.release_artifact.candidate_report_hash != self.validation_report.candidate_report_hash:
            raise ValueError("validation_report.candidate_report_hash must match release_artifact.candidate_report_hash")
        if self.release_artifact.refinement_decisions_hash != self.validation_report.refinement_decisions_hash:
            raise ValueError(
                "validation_report.refinement_decisions_hash must match release_artifact.refinement_decisions_hash"
            )
        if self.package_scope == _PACKAGE_SCOPE_RELEASE_ONLY and self.research_claims is not None:
            raise ValueError("release_only packs must not carry research_claims")
        if self.package_scope == _PACKAGE_SCOPE_RELEASE_AND_RESEARCH and self.research_claims is None:
            raise ValueError("release_and_research packs must carry research_claims")
        if self.research_claims is not None:
            if self.release_artifact.release_artifact_id != self.research_claims.release_artifact_id:
                raise ValueError("research_claims.release_artifact_id must match release_artifact.release_artifact_id")
            if self.release_artifact.validation_report_hash != self.research_claims.validation_report_hash:
                raise ValueError("research_claims.validation_report_hash must match release_artifact.validation_report_hash")
            if self.release_artifact.candidate_report_hash != self.research_claims.candidate_report_hash:
                raise ValueError("research_claims.candidate_report_hash must match release_artifact.candidate_report_hash")
            if self.release_artifact.refinement_decisions_hash != self.research_claims.refinement_decisions_hash:
                raise ValueError(
                    "research_claims.refinement_decisions_hash must match release_artifact.refinement_decisions_hash"
                )
