"""Stage E4 typed contracts for deterministic calibration and selection."""

from __future__ import annotations

from dataclasses import dataclass

from ..datasets import CorridorBucketFrequencyArtifact


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


def _require_fraction(value: float, field_name: str) -> float:
    if not 0.0 <= value <= 1.0:
        raise ValueError(f"{field_name} must be within [0.0, 1.0]")
    return value


def _require_non_negative(value: float, field_name: str) -> float:
    if value < 0.0:
        raise ValueError(f"{field_name} must be >= 0.0")
    return value


@dataclass(frozen=True)
class CalibrationSelectionConfig:
    """Stage E4 deterministic policy surface for profile and prior selection."""

    minimum_confidence: float = 0.60
    minimum_relative_improvement: float = 0.05
    maximum_prior_adjustment: float = 0.20
    preferential_attachment_range: float = 0.10
    prior_floor: float = 0.0
    prior_ceiling: float = 1.0

    def __post_init__(self) -> None:
        minimum_confidence = _require_fraction(self.minimum_confidence, "minimum_confidence")
        minimum_relative_improvement = _require_fraction(
            self.minimum_relative_improvement,
            "minimum_relative_improvement",
        )
        maximum_prior_adjustment = _require_fraction(
            self.maximum_prior_adjustment,
            "maximum_prior_adjustment",
        )
        preferential_attachment_range = _require_fraction(
            self.preferential_attachment_range,
            "preferential_attachment_range",
        )
        prior_floor = _require_fraction(self.prior_floor, "prior_floor")
        prior_ceiling = _require_fraction(self.prior_ceiling, "prior_ceiling")
        if prior_floor > prior_ceiling:
            raise ValueError("prior_floor must be <= prior_ceiling")
        if preferential_attachment_range > maximum_prior_adjustment:
            raise ValueError("preferential_attachment_range must be <= maximum_prior_adjustment")

        object.__setattr__(self, "minimum_confidence", minimum_confidence)
        object.__setattr__(self, "minimum_relative_improvement", minimum_relative_improvement)
        object.__setattr__(self, "maximum_prior_adjustment", maximum_prior_adjustment)
        object.__setattr__(self, "preferential_attachment_range", preferential_attachment_range)
        object.__setattr__(self, "prior_floor", prior_floor)
        object.__setattr__(self, "prior_ceiling", prior_ceiling)


@dataclass(frozen=True)
class RefinedProfileSelectionRow:
    """Stage E4 one confidence-gated refined profile decision."""

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
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "confidence", _require_fraction(self.confidence, "confidence"))
        object.__setattr__(
            self,
            "relative_improvement",
            _require_non_negative(self.relative_improvement, "relative_improvement"),
        )
        if self.rejection_reason is not None:
            object.__setattr__(
                self,
                "rejection_reason",
                _require_non_blank(self.rejection_reason, "rejection_reason").lower(),
            )


@dataclass(frozen=True)
class ScenarioPriorCalibrationRow:
    """Stage E4 one auditable calibrated scenario-prior row."""

    corridor_id: str
    signal_kind: str
    structural_cluster_id: str | None
    day_of_week: int
    bucket_index: int
    historical_bucket_frequency: float
    calibrated_prior_probability: float
    confidence: float
    density_signal: float
    evidence_response: float
    preferential_attachment_adjustment: float
    homophily_adjustment: float
    confidence_qualified: bool
    rejection_reason: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "corridor_id", _require_non_blank(self.corridor_id, "corridor_id"))
        object.__setattr__(self, "signal_kind", _require_non_blank(self.signal_kind, "signal_kind").upper())
        if self.structural_cluster_id is not None:
            object.__setattr__(
                self,
                "structural_cluster_id",
                _require_non_blank(self.structural_cluster_id, "structural_cluster_id"),
            )
        object.__setattr__(
            self,
            "historical_bucket_frequency",
            _require_fraction(self.historical_bucket_frequency, "historical_bucket_frequency"),
        )
        object.__setattr__(
            self,
            "calibrated_prior_probability",
            _require_fraction(self.calibrated_prior_probability, "calibrated_prior_probability"),
        )
        object.__setattr__(self, "confidence", _require_fraction(self.confidence, "confidence"))
        object.__setattr__(self, "density_signal", _require_fraction(self.density_signal, "density_signal"))
        object.__setattr__(self, "evidence_response", _require_fraction(self.evidence_response, "evidence_response"))
        if not -1.0 <= self.preferential_attachment_adjustment <= 1.0:
            raise ValueError("preferential_attachment_adjustment must be within [-1.0, 1.0]")
        object.__setattr__(
            self,
            "homophily_adjustment",
            _require_fraction(self.homophily_adjustment, "homophily_adjustment"),
        )
        if self.rejection_reason is not None:
            object.__setattr__(
                self,
                "rejection_reason",
                _require_non_blank(self.rejection_reason, "rejection_reason").lower(),
            )


@dataclass(frozen=True)
class CalibrationSelectionBundle:
    """Stage E4 deterministic calibration bundle built on E2/E3 artifacts."""

    manifest_version: str
    sequence_manifest_version: str
    training_manifest_version: str
    config: CalibrationSelectionConfig
    published_corridor_bucket_frequency: CorridorBucketFrequencyArtifact
    refined_profile_selections: tuple[RefinedProfileSelectionRow, ...]
    scenario_prior_calibrations: tuple[ScenarioPriorCalibrationRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(
            self,
            "sequence_manifest_version",
            _require_non_blank(self.sequence_manifest_version, "sequence_manifest_version"),
        )
        object.__setattr__(
            self,
            "training_manifest_version",
            _require_non_blank(self.training_manifest_version, "training_manifest_version"),
        )
        object.__setattr__(self, "refined_profile_selections", tuple(self.refined_profile_selections))
        object.__setattr__(self, "scenario_prior_calibrations", tuple(self.scenario_prior_calibrations))
