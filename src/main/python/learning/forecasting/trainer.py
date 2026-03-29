"""Stage E3 deterministic training and probe utilities over E2 temporal artifacts."""

from __future__ import annotations

import math
from collections import defaultdict
from dataclasses import dataclass

from ..datasets import CorridorBucketFrequencyArtifact, SequenceDatasetArtifact, SequenceRow
from ..ingestion import TimeUnit
from .contracts import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalAttributeProbeCase,
    TemporalAttributeProbeReport,
    TemporalAttributeProbeRow,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
)

_MANIFEST_VERSION = "E3.v1"
_COMPETENCE_LIMITED = "LIMITED"
_COMPETENCE_PARITY = "PARITY"
_COMPETENCE_REGRESSED = "REGRESSED"
_COMPETENCE_STRONG = "STRONG"


@dataclass(frozen=True)
class _Observation:
    sequence_scope: str
    subject_id: str
    signal_kind: str
    day_of_week: int
    bucket_index: int
    timestamp_ticks: int
    signal_value: float
    persistence_run_length: int
    periodicity_bucket_count: int
    corridor_activity_count: int
    bucket_baseline_frequency: float


def _mean(values: list[float]) -> float:
    return 0.0 if not values else sum(values) / len(values)


def _weighted_mean(pairs: list[tuple[float, float]]) -> float:
    total_weight = sum(weight for _, weight in pairs)
    if total_weight <= 0:
        return 0.0
    return sum(value * weight for value, weight in pairs) / total_weight


def _clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))


def _recency_weight(age_ticks: int, half_life_ticks: int) -> float:
    if age_ticks <= 0:
        return 1.0
    safe_half_life = max(1, half_life_ticks)
    return math.exp(-math.log(2.0) * (age_ticks / safe_half_life))


def _signal_from_row(row: SequenceRow) -> tuple[str, float]:
    if row.travel_time is not None:
        return "TRAVEL_TIME", float(row.travel_time)
    if row.speed_factor_observed is not None:
        return "SLOWDOWN_FACTOR", 1.0 / max(float(row.speed_factor_observed), 1e-6)
    return "EVENT_INTENSITY", 1.0


def _corridor_frequency_lookup(
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
) -> dict[tuple[str, int, int], float]:
    return {
        (row.corridor_id, row.day_of_week, row.bucket_index): row.historical_bucket_frequency
        for row in corridor_bucket_frequency.rows
    }


def _density_ratio(observation: _Observation, max_corridor_activity_count: int) -> float:
    if observation.bucket_baseline_frequency > 0:
        return _clamp(observation.bucket_baseline_frequency)
    if max_corridor_activity_count <= 0:
        return 0.0
    return _clamp(observation.corridor_activity_count / max_corridor_activity_count)


def _build_observations(
    sequence_dataset: SequenceDatasetArtifact,
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
    config: ForecastTrainingConfig,
) -> tuple[int, int, list[_Observation]]:
    engine_time_unit = TimeUnit.parse(sequence_dataset.engine_time_unit)
    relevant_rows = [
        row
        for row in sequence_dataset.rows
        if row.sequence_scope in config.allowed_sequence_scopes
    ]
    if not relevant_rows:
        return 0, 0, []

    history_window_ticks = config.history_window_ticks(sequence_dataset.bucket_size_seconds, engine_time_unit)
    history_window_end_ticks = max(row.timestamp_ticks for row in relevant_rows)
    history_window_start_ticks = history_window_end_ticks - history_window_ticks
    corridor_lookup = _corridor_frequency_lookup(corridor_bucket_frequency)

    observations = [
        _Observation(
            sequence_scope=row.sequence_scope,
            subject_id=row.subject_id,
            signal_kind=_signal_from_row(row)[0],
            day_of_week=row.day_of_week,
            bucket_index=row.bucket_index,
            timestamp_ticks=row.timestamp_ticks,
            signal_value=_signal_from_row(row)[1],
            persistence_run_length=row.persistence_run_length,
            periodicity_bucket_count=row.periodicity_bucket_count,
            corridor_activity_count=row.corridor_activity_count,
            bucket_baseline_frequency=(
                corridor_lookup.get((row.subject_id, row.day_of_week, row.bucket_index), 0.0)
                if row.sequence_scope == "CORRIDOR"
                else 0.0
            ),
        )
        for row in relevant_rows
        if row.timestamp_ticks >= history_window_start_ticks
    ]
    observations.sort(
        key=lambda observation: (
            observation.sequence_scope,
            observation.subject_id,
            observation.timestamp_ticks,
            observation.day_of_week,
            observation.bucket_index,
        )
    )
    return history_window_start_ticks, history_window_end_ticks, observations


def _representation_confidence(
    observation_count: int,
    periodicity_signal: float,
    persistence_signal: float,
    density_signal: float,
    minimum_training_observations: int,
) -> float:
    if observation_count < minimum_training_observations:
        return _clamp(0.30 + 0.08 * observation_count, upper=0.45)
    return _clamp(
        0.45
        + 0.08 * min(observation_count, 5)
        + 0.15 * periodicity_signal
        + 0.12 * persistence_signal
        + 0.10 * density_signal
    )


def _forecast_confidence(
    subject_observation_count: int,
    bucket_observation_count: int,
    periodicity_ratio: float,
    persistence_ratio: float,
    density_ratio: float,
    minimum_training_observations: int,
) -> float:
    if subject_observation_count < minimum_training_observations:
        return _clamp(0.28 + 0.06 * bucket_observation_count, upper=0.45)
    return _clamp(
        0.42
        + 0.07 * min(subject_observation_count, 5)
        + 0.05 * min(bucket_observation_count, 4)
        + 0.14 * periodicity_ratio
        + 0.12 * persistence_ratio
        + 0.10 * density_ratio
    )


def _build_representations(
    observations: list[_Observation],
    history_window_start_ticks: int,
    history_window_end_ticks: int,
    half_life_ticks: int,
    config: ForecastTrainingConfig,
    sequence_manifest_version: str,
) -> TemporalRepresentationArtifact:
    grouped: dict[tuple[str, str], list[_Observation]] = defaultdict(list)
    max_corridor_activity_count = max((observation.corridor_activity_count for observation in observations), default=0)

    for observation in observations:
        grouped[(observation.sequence_scope, observation.subject_id)].append(observation)

    rows: list[TemporalRepresentationRow] = []
    for (sequence_scope, subject_id), subject_rows in sorted(grouped.items()):
        subject_signal_kind = subject_rows[0].signal_kind
        signal_values = [row.signal_value for row in subject_rows]
        recent_signal = _weighted_mean(
            [
                (
                    row.signal_value,
                    _recency_weight(history_window_end_ticks - row.timestamp_ticks, half_life_ticks),
                )
                for row in subject_rows
            ]
        )
        persistence_signal = _clamp(_mean([min(row.persistence_run_length, 8) / 8.0 for row in subject_rows]))
        periodicity_signal = _clamp(max(row.periodicity_bucket_count for row in subject_rows) / len(subject_rows))
        density_signal = _clamp(
            _mean(
                [
                    _density_ratio(row, max_corridor_activity_count)
                    for row in subject_rows
                ]
            )
        )
        confidence = _representation_confidence(
            observation_count=len(subject_rows),
            periodicity_signal=periodicity_signal,
            persistence_signal=persistence_signal,
            density_signal=density_signal,
            minimum_training_observations=config.minimum_training_observations,
        )
        rows.append(
            TemporalRepresentationRow(
                sequence_scope=sequence_scope,
                subject_id=subject_id,
                signal_kind=subject_signal_kind,
                observation_count=len(subject_rows),
                latest_timestamp_ticks=max(row.timestamp_ticks for row in subject_rows),
                mean_signal=_mean(signal_values),
                recent_signal=recent_signal,
                persistence_signal=persistence_signal,
                periodicity_signal=periodicity_signal,
                density_signal=density_signal,
                confidence=confidence,
            )
        )

    return TemporalRepresentationArtifact(
        manifest_version=_MANIFEST_VERSION,
        sequence_manifest_version=sequence_manifest_version,
        training_seed=config.training_seed,
        history_window_start_ticks=history_window_start_ticks,
        history_window_end_ticks=history_window_end_ticks,
        enabled_features=config.enabled_features,
        rows=tuple(rows),
    )


def _apply_enabled_features(
    baseline_prediction: float,
    bucket_mean: float,
    recent_bucket_mean: float,
    recent_subject_mean: float,
    periodicity_ratio: float,
    persistence_ratio: float,
    density_ratio: float,
    config: ForecastTrainingConfig,
) -> float:
    prediction = baseline_prediction
    if config.feature_enabled("periodicity"):
        prediction = prediction + ((bucket_mean - prediction) * (0.35 + 0.50 * periodicity_ratio))
    if config.feature_enabled("recency"):
        prediction = (0.35 * prediction) + (0.65 * recent_bucket_mean)
    if config.feature_enabled("persistence"):
        prediction = prediction + ((recent_subject_mean - prediction) * (0.30 + 0.20 * persistence_ratio))
    if config.feature_enabled("density"):
        prediction = prediction + ((bucket_mean - baseline_prediction) * (0.35 + 0.25 * density_ratio))
    return prediction


def _build_forecast_surface(
    observations: list[_Observation],
    history_window_end_ticks: int,
    half_life_ticks: int,
    config: ForecastTrainingConfig,
    sequence_manifest_version: str,
) -> ForecastSurfaceArtifact:
    grouped: dict[tuple[str, str], list[_Observation]] = defaultdict(list)
    max_corridor_activity_count = max((observation.corridor_activity_count for observation in observations), default=0)
    for observation in observations:
        grouped[(observation.sequence_scope, observation.subject_id)].append(observation)

    rows: list[ForecastSurfaceRow] = []
    for (sequence_scope, subject_id), subject_rows in sorted(grouped.items()):
        subject_signal_kind = subject_rows[0].signal_kind
        subject_mean = _mean([row.signal_value for row in subject_rows])
        recent_subject_mean = _weighted_mean(
            [
                (
                    row.signal_value,
                    _recency_weight(history_window_end_ticks - row.timestamp_ticks, half_life_ticks),
                )
                for row in subject_rows
            ]
        )
        bucket_groups: dict[tuple[int, int], list[_Observation]] = defaultdict(list)
        for row in subject_rows:
            bucket_groups[(row.day_of_week, row.bucket_index)].append(row)

        for (day_of_week, bucket_index), bucket_rows in sorted(bucket_groups.items()):
            bucket_mean = _mean([row.signal_value for row in bucket_rows])
            recent_bucket_mean = _weighted_mean(
                [
                    (
                        row.signal_value,
                        _recency_weight(history_window_end_ticks - row.timestamp_ticks, half_life_ticks),
                    )
                    for row in bucket_rows
                ]
            )
            periodicity_ratio = _clamp(len(bucket_rows) / len(subject_rows))
            persistence_ratio = _clamp(_mean([min(row.persistence_run_length, 8) / 8.0 for row in bucket_rows]))
            density_ratio = _clamp(
                _mean(
                    [
                        _density_ratio(row, max_corridor_activity_count)
                        for row in bucket_rows
                    ]
                )
            )
            baseline_frequency = _mean([row.bucket_baseline_frequency for row in bucket_rows])
            feature_rich_prediction = _apply_enabled_features(
                baseline_prediction=subject_mean,
                bucket_mean=bucket_mean,
                recent_bucket_mean=recent_bucket_mean,
                recent_subject_mean=recent_subject_mean,
                periodicity_ratio=periodicity_ratio,
                persistence_ratio=persistence_ratio,
                density_ratio=density_ratio,
                config=config,
            )
            confidence = _forecast_confidence(
                subject_observation_count=len(subject_rows),
                bucket_observation_count=len(bucket_rows),
                periodicity_ratio=periodicity_ratio,
                persistence_ratio=persistence_ratio,
                density_ratio=max(density_ratio, baseline_frequency),
                minimum_training_observations=config.minimum_training_observations,
            )
            rows.append(
                ForecastSurfaceRow(
                    sequence_scope=sequence_scope,
                    subject_id=subject_id,
                    signal_kind=subject_signal_kind,
                    day_of_week=day_of_week,
                    bucket_index=bucket_index,
                    observation_count=len(subject_rows),
                    bucket_baseline_frequency=baseline_frequency,
                    baseline_prediction=subject_mean,
                    feature_rich_prediction=feature_rich_prediction,
                    confidence=confidence,
                )
            )

    return ForecastSurfaceArtifact(
        manifest_version=_MANIFEST_VERSION,
        sequence_manifest_version=sequence_manifest_version,
        training_seed=config.training_seed,
        enabled_features=config.enabled_features,
        rows=tuple(rows),
    )


def train_forecast_bundle(
    sequence_dataset: SequenceDatasetArtifact,
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
    config: ForecastTrainingConfig,
) -> ForecastTrainingBundle:
    """Stage E3 train deterministic offline forecast artifacts. Satisfies closure criterion: learned forecast surfaces improve target temporal attributes without moving stochastic behavior into runtime."""

    history_window_start_ticks, history_window_end_ticks, observations = _build_observations(
        sequence_dataset,
        corridor_bucket_frequency,
        config,
    )
    half_life_ticks = config.recency_half_life_ticks(
        sequence_dataset.bucket_size_seconds,
        TimeUnit.parse(sequence_dataset.engine_time_unit),
    )
    representations = _build_representations(
        observations=observations,
        history_window_start_ticks=history_window_start_ticks,
        history_window_end_ticks=history_window_end_ticks,
        half_life_ticks=half_life_ticks,
        config=config,
        sequence_manifest_version=sequence_dataset.manifest_version,
    )
    forecast_surface = _build_forecast_surface(
        observations=observations,
        history_window_end_ticks=history_window_end_ticks,
        half_life_ticks=half_life_ticks,
        config=config,
        sequence_manifest_version=sequence_dataset.manifest_version,
    )
    return ForecastTrainingBundle(
        manifest_version=_MANIFEST_VERSION,
        config=config,
        representations=representations,
        forecast_surface=forecast_surface,
    )


def _competence_label(
    row: ForecastSurfaceRow,
    feature_absolute_error: float,
    baseline_absolute_error: float,
    minimum_training_observations: int,
) -> str:
    if row.observation_count < minimum_training_observations or row.confidence < 0.5:
        return _COMPETENCE_LIMITED
    if feature_absolute_error + 1e-9 < baseline_absolute_error - 0.25:
        return _COMPETENCE_STRONG
    if feature_absolute_error <= baseline_absolute_error + 0.5:
        return _COMPETENCE_PARITY
    return _COMPETENCE_REGRESSED


def run_temporal_attribute_probe(
    training_bundle: ForecastTrainingBundle,
    probe_cases: tuple[TemporalAttributeProbeCase, ...],
) -> TemporalAttributeProbeReport:
    """Stage E3 run an explicit competence probe. Satisfies closure criterion: representation probing shows where the learning layer is and is not competent before export opens."""

    lookup = {
        (row.sequence_scope, row.subject_id, row.day_of_week, row.bucket_index): row
        for row in training_bundle.forecast_surface.rows
    }
    results: list[TemporalAttributeProbeRow] = []
    for case in probe_cases:
        key = (case.sequence_scope, case.subject_id, case.day_of_week, case.bucket_index)
        forecast_row = lookup.get(key)
        if forecast_row is None:
            raise ValueError(f"no forecast row found for probe case: {case}")
        baseline_absolute_error = abs(forecast_row.baseline_prediction - case.expected_prediction)
        feature_absolute_error = abs(forecast_row.feature_rich_prediction - case.expected_prediction)
        competence = _competence_label(
            forecast_row,
            feature_absolute_error=feature_absolute_error,
            baseline_absolute_error=baseline_absolute_error,
            minimum_training_observations=training_bundle.config.minimum_training_observations,
        )
        results.append(
            TemporalAttributeProbeRow(
                attribute_name=case.attribute_name,
                sequence_scope=case.sequence_scope,
                subject_id=case.subject_id,
                day_of_week=case.day_of_week,
                bucket_index=case.bucket_index,
                expected_prediction=case.expected_prediction,
                baseline_prediction=forecast_row.baseline_prediction,
                feature_rich_prediction=forecast_row.feature_rich_prediction,
                baseline_absolute_error=baseline_absolute_error,
                feature_rich_absolute_error=feature_absolute_error,
                competence=competence,
                note=case.note,
            )
        )
    return TemporalAttributeProbeReport(
        manifest_version=_MANIFEST_VERSION,
        training_seed=training_bundle.config.training_seed,
        rows=tuple(results),
    )
