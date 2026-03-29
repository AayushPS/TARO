"""Stage E3 typed contracts for deterministic forecast and representation learning."""

from __future__ import annotations

from dataclasses import dataclass

from ..ingestion import TimeUnit

_SUPPORTED_FEATURES = ("periodicity", "recency", "persistence", "density")


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


def _normalize_feature_name(value: str) -> str:
    normalized = _require_non_blank(value, "feature_name").lower()
    if normalized not in _SUPPORTED_FEATURES:
        raise ValueError(f"unsupported feature toggle: {value}")
    return normalized


@dataclass(frozen=True)
class ForecastTrainingConfig:
    """Stage E3 deterministic builder config for learned representations and forecast surfaces."""

    training_seed: int
    history_window_buckets: int
    recency_half_life_buckets: int
    minimum_training_observations: int = 2
    enabled_features: tuple[str, ...] = _SUPPORTED_FEATURES
    allowed_sequence_scopes: tuple[str, ...] = ("EDGE", "CORRIDOR")

    def __post_init__(self) -> None:
        if self.training_seed < 0:
            raise ValueError("training_seed must be non-negative")
        if self.history_window_buckets <= 0:
            raise ValueError("history_window_buckets must be positive")
        if self.recency_half_life_buckets <= 0:
            raise ValueError("recency_half_life_buckets must be positive")
        if self.minimum_training_observations <= 0:
            raise ValueError("minimum_training_observations must be positive")

        enabled = tuple(dict.fromkeys(_normalize_feature_name(value) for value in self.enabled_features))
        scopes = tuple(_require_non_blank(value, "allowed_sequence_scope").upper() for value in self.allowed_sequence_scopes)
        object.__setattr__(self, "enabled_features", enabled)
        object.__setattr__(self, "allowed_sequence_scopes", scopes)

    def history_window_ticks(self, bucket_size_seconds: int, engine_time_unit: TimeUnit) -> int:
        """Stage E3 bounded training window. Satisfies closure criterion: reproducible training under a pinned data window."""

        if bucket_size_seconds <= 0:
            raise ValueError("bucket_size_seconds must be positive")
        multiplier = 1 if engine_time_unit == TimeUnit.SECONDS else 1_000
        return self.history_window_buckets * bucket_size_seconds * multiplier

    def recency_half_life_ticks(self, bucket_size_seconds: int, engine_time_unit: TimeUnit) -> int:
        """Stage E3 recency weighting policy. Satisfies closure criterion: forecast surfaces improve recency-sensitive behavior deterministically."""

        if bucket_size_seconds <= 0:
            raise ValueError("bucket_size_seconds must be positive")
        multiplier = 1 if engine_time_unit == TimeUnit.SECONDS else 1_000
        return self.recency_half_life_buckets * bucket_size_seconds * multiplier

    def feature_enabled(self, feature_name: str) -> bool:
        """Stage E3 feature-toggle query. Satisfies closure criterion: auditable ablations over the learned surface."""

        return _normalize_feature_name(feature_name) in self.enabled_features


@dataclass(frozen=True)
class TemporalRepresentationRow:
    """Stage E3 one learned subject-level temporal representation over E2 edge/corridor sequences."""

    sequence_scope: str
    subject_id: str
    signal_kind: str
    observation_count: int
    latest_timestamp_ticks: int
    mean_signal: float
    recent_signal: float
    persistence_signal: float
    periodicity_signal: float
    density_signal: float
    confidence: float

    def __post_init__(self) -> None:
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "signal_kind", _require_non_blank(self.signal_kind, "signal_kind").upper())


@dataclass(frozen=True)
class TemporalRepresentationArtifact:
    """Stage E3 canonical learned representation artifact for later offline calibration stages."""

    manifest_version: str
    sequence_manifest_version: str
    training_seed: int
    history_window_start_ticks: int
    history_window_end_ticks: int
    enabled_features: tuple[str, ...]
    rows: tuple[TemporalRepresentationRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "sequence_manifest_version", _require_non_blank(self.sequence_manifest_version, "sequence_manifest_version"))
        object.__setattr__(self, "enabled_features", tuple(self.enabled_features))
        object.__setattr__(self, "rows", tuple(self.rows))


@dataclass(frozen=True)
class ForecastSurfaceRow:
    """Stage E3 one learned forecast surface row for a subject/day/bucket posture."""

    sequence_scope: str
    subject_id: str
    signal_kind: str
    day_of_week: int
    bucket_index: int
    observation_count: int
    bucket_baseline_frequency: float
    baseline_prediction: float
    feature_rich_prediction: float
    confidence: float
    structural_cluster_id: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "signal_kind", _require_non_blank(self.signal_kind, "signal_kind").upper())
        if self.structural_cluster_id is not None:
            object.__setattr__(
                self,
                "structural_cluster_id",
                _require_non_blank(self.structural_cluster_id, "structural_cluster_id"),
            )


@dataclass(frozen=True)
class ForecastSurfaceArtifact:
    """Stage E3 canonical forecast surface artifact used by later offline calibration stages."""

    manifest_version: str
    sequence_manifest_version: str
    training_seed: int
    enabled_features: tuple[str, ...]
    rows: tuple[ForecastSurfaceRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "sequence_manifest_version", _require_non_blank(self.sequence_manifest_version, "sequence_manifest_version"))
        object.__setattr__(self, "enabled_features", tuple(self.enabled_features))
        object.__setattr__(self, "rows", tuple(self.rows))


@dataclass(frozen=True)
class ForecastTrainingBundle:
    """Stage E3 combined learned representation and forecast artifact bundle."""

    manifest_version: str
    config: ForecastTrainingConfig
    representations: TemporalRepresentationArtifact
    forecast_surface: ForecastSurfaceArtifact

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))


@dataclass(frozen=True)
class TemporalAttributeProbeCase:
    """Stage E3 one explicit attribute-probe expectation over the learned forecast surface."""

    attribute_name: str
    sequence_scope: str
    subject_id: str
    day_of_week: int
    bucket_index: int
    expected_prediction: float
    note: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "attribute_name", _require_non_blank(self.attribute_name, "attribute_name").lower())
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))


@dataclass(frozen=True)
class TemporalAttributeProbeRow:
    """Stage E3 one measured competence result comparing baseline and richer forecast behavior."""

    attribute_name: str
    sequence_scope: str
    subject_id: str
    day_of_week: int
    bucket_index: int
    expected_prediction: float
    baseline_prediction: float
    feature_rich_prediction: float
    baseline_absolute_error: float
    feature_rich_absolute_error: float
    competence: str
    note: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "attribute_name", _require_non_blank(self.attribute_name, "attribute_name").lower())
        object.__setattr__(self, "sequence_scope", _require_non_blank(self.sequence_scope, "sequence_scope").upper())
        object.__setattr__(self, "subject_id", _require_non_blank(self.subject_id, "subject_id"))
        object.__setattr__(self, "competence", _require_non_blank(self.competence, "competence").upper())


@dataclass(frozen=True)
class TemporalAttributeProbeReport:
    """Stage E3 canonical competence report showing where the learned layer is and is not reliable."""

    manifest_version: str
    training_seed: int
    rows: tuple[TemporalAttributeProbeRow, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "manifest_version", _require_non_blank(self.manifest_version, "manifest_version"))
        object.__setattr__(self, "rows", tuple(self.rows))
