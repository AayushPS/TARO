"""Stage E4 deterministic calibration and selection routines."""

from __future__ import annotations

from ..datasets import CorridorBucketFrequencyArtifact
from ..forecasting import ForecastTrainingBundle
from .contracts import (
    CalibrationSelectionBundle,
    CalibrationSelectionConfig,
    RefinedProfileSelectionRow,
    ScenarioPriorCalibrationRow,
)

_MANIFEST_VERSION = "E4.v1"
_LOW_CONFIDENCE_REASON = "low_confidence"
_INSUFFICIENT_IMPROVEMENT_REASON = "insufficient_improvement"


def _clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


def _relative_improvement(baseline_prediction: float, feature_rich_prediction: float) -> float:
    return abs(feature_rich_prediction - baseline_prediction) / max(abs(baseline_prediction), 1.0)


def _rejection_reason(
    confidence: float,
    relative_improvement: float,
    config: CalibrationSelectionConfig,
) -> str | None:
    if confidence < config.minimum_confidence:
        return _LOW_CONFIDENCE_REASON
    if relative_improvement < config.minimum_relative_improvement:
        return _INSUFFICIENT_IMPROVEMENT_REASON
    return None


def _corridor_baseline_lookup(
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
) -> dict[tuple[str, int, int], float]:
    return {
        (row.corridor_id, row.day_of_week, row.bucket_index): row.historical_bucket_frequency
        for row in corridor_bucket_frequency.rows
    }


def _corridor_density_lookup(training_bundle: ForecastTrainingBundle) -> dict[str, float]:
    return {
        row.subject_id: row.density_signal
        for row in training_bundle.representations.rows
        if row.sequence_scope == "CORRIDOR"
    }


def _corridor_representation_lookup(
    training_bundle: ForecastTrainingBundle,
) -> dict[str, object]:
    return {
        row.subject_id: row
        for row in training_bundle.representations.rows
        if row.sequence_scope == "CORRIDOR"
    }


def _peer_cluster_evidence_lookup(
    provisional_rows: list[dict[str, object]],
) -> dict[tuple[str, str, int, int], float]:
    maxima: dict[tuple[str, str, int, int], float] = {}
    for row in provisional_rows:
        cluster_id = row["structural_cluster_id"]
        if cluster_id is None:
            continue
        key = (
            str(cluster_id),
            str(row["signal_kind"]),
            int(row["day_of_week"]),
            int(row["bucket_index"]),
        )
        maxima[key] = max(maxima.get(key, 0.0), float(row["evidence_response"]))
    return maxima


def build_calibration_bundle(
    training_bundle: ForecastTrainingBundle,
    corridor_bucket_frequency: CorridorBucketFrequencyArtifact,
    config: CalibrationSelectionConfig | None = None,
) -> CalibrationSelectionBundle:
    """Stage E4 build deterministic profile/prior calibration decisions from E2/E3 artifacts."""

    calibration_config = CalibrationSelectionConfig() if config is None else config
    baseline_lookup = _corridor_baseline_lookup(corridor_bucket_frequency)
    density_lookup = _corridor_density_lookup(training_bundle)
    representation_lookup = _corridor_representation_lookup(training_bundle)

    refined_profile_rows: list[RefinedProfileSelectionRow] = []
    provisional_prior_rows: list[dict[str, object]] = []

    ordered_rows = sorted(
        training_bundle.forecast_surface.rows,
        key=lambda row: (row.sequence_scope, row.subject_id, row.day_of_week, row.bucket_index),
    )
    for row in ordered_rows:
        relative_improvement = _relative_improvement(
            row.baseline_prediction,
            row.feature_rich_prediction,
        )
        rejection_reason = _rejection_reason(
            row.confidence,
            relative_improvement,
            calibration_config,
        )
        accepted = rejection_reason is None
        calibrated_prediction = row.feature_rich_prediction if accepted else row.baseline_prediction
        refined_profile_rows.append(
            RefinedProfileSelectionRow(
                sequence_scope=row.sequence_scope,
                subject_id=row.subject_id,
                day_of_week=row.day_of_week,
                bucket_index=row.bucket_index,
                baseline_prediction=row.baseline_prediction,
                feature_rich_prediction=row.feature_rich_prediction,
                calibrated_prediction=calibrated_prediction,
                confidence=row.confidence,
                relative_improvement=relative_improvement,
                accepted=accepted,
                rejection_reason=rejection_reason,
            )
        )

        if row.sequence_scope != "CORRIDOR":
            continue

        historical_bucket_frequency = baseline_lookup.get(
            (row.subject_id, row.day_of_week, row.bucket_index),
            row.bucket_baseline_frequency,
        )
        density_signal = density_lookup.get(row.subject_id, 0.5)
        corridor_representation = representation_lookup.get(row.subject_id)
        periodicity_signal = 0.0 if corridor_representation is None else float(corridor_representation.periodicity_signal)
        persistence_signal = 0.0 if corridor_representation is None else float(corridor_representation.persistence_signal)
        directional_improvement = (
            (row.feature_rich_prediction - row.baseline_prediction)
            / max(abs(row.baseline_prediction), 1.0)
        )
        movement_sign = 1.0 if directional_improvement >= 0.0 else -1.0
        feature_multiplier = 1.0
        if accepted and training_bundle.config.feature_enabled("periodicity"):
            feature_multiplier += 0.30 * periodicity_signal
        if accepted and movement_sign > 0.0 and training_bundle.config.feature_enabled("persistence"):
            feature_multiplier += 0.20 * persistence_signal
        evidence_response = (
            _clamp(
                relative_improvement * row.confidence * feature_multiplier,
                0.0,
                calibration_config.maximum_prior_adjustment,
            )
            if accepted
            else 0.0
        )
        centered_density_signal = (2.0 * density_signal) - 1.0
        preferential_attachment_adjustment = (
            evidence_response
            * calibration_config.preferential_attachment_range
            * centered_density_signal
            if accepted and movement_sign > 0.0
            else 0.0
        )
        calibrated_prior_probability = historical_bucket_frequency
        if accepted:
            calibrated_prior_probability = _clamp(
                historical_bucket_frequency
                + (movement_sign * evidence_response)
                + preferential_attachment_adjustment,
                calibration_config.prior_floor,
                calibration_config.prior_ceiling,
            )

        provisional_prior_rows.append(
            {
                "corridor_id": row.subject_id,
                "signal_kind": row.signal_kind,
                "structural_cluster_id": row.structural_cluster_id,
                "day_of_week": row.day_of_week,
                "bucket_index": row.bucket_index,
                "historical_bucket_frequency": historical_bucket_frequency,
                "calibrated_prior_probability": calibrated_prior_probability,
                "confidence": row.confidence,
                "density_signal": density_signal,
                "evidence_response": evidence_response,
                "preferential_attachment_adjustment": preferential_attachment_adjustment,
                "confidence_qualified": accepted,
                "rejection_reason": rejection_reason,
            }
        )

    peer_cluster_lookup = _peer_cluster_evidence_lookup(provisional_prior_rows)
    scenario_prior_rows: list[ScenarioPriorCalibrationRow] = []
    for row in provisional_prior_rows:
        cluster_id = row["structural_cluster_id"]
        homophily_adjustment = 0.0
        if cluster_id is not None:
            cluster_key = (
                str(cluster_id),
                str(row["signal_kind"]),
                int(row["day_of_week"]),
                int(row["bucket_index"]),
            )
            peer_max_evidence = peer_cluster_lookup.get(cluster_key, 0.0)
            evidence_gap = max(0.0, peer_max_evidence - float(row["evidence_response"]))
            homophily_adjustment = min(
                evidence_gap * 0.50,
                calibration_config.maximum_prior_adjustment * 0.25,
            )

        calibrated_prior_probability = _clamp(
            float(row["calibrated_prior_probability"]) + homophily_adjustment,
            calibration_config.prior_floor,
            calibration_config.prior_ceiling,
        )
        scenario_prior_rows.append(
            ScenarioPriorCalibrationRow(
                corridor_id=str(row["corridor_id"]),
                signal_kind=str(row["signal_kind"]),
                structural_cluster_id=None if cluster_id is None else str(cluster_id),
                day_of_week=int(row["day_of_week"]),
                bucket_index=int(row["bucket_index"]),
                historical_bucket_frequency=float(row["historical_bucket_frequency"]),
                calibrated_prior_probability=calibrated_prior_probability,
                confidence=float(row["confidence"]),
                density_signal=float(row["density_signal"]),
                evidence_response=float(row["evidence_response"]),
                preferential_attachment_adjustment=float(row["preferential_attachment_adjustment"]),
                homophily_adjustment=homophily_adjustment,
                confidence_qualified=bool(row["confidence_qualified"]),
                rejection_reason=None if row["rejection_reason"] is None else str(row["rejection_reason"]),
            )
        )

    return CalibrationSelectionBundle(
        manifest_version=_MANIFEST_VERSION,
        sequence_manifest_version=training_bundle.forecast_surface.sequence_manifest_version,
        training_manifest_version=training_bundle.manifest_version,
        config=calibration_config,
        published_corridor_bucket_frequency=corridor_bucket_frequency,
        refined_profile_selections=tuple(refined_profile_rows),
        scenario_prior_calibrations=tuple(scenario_prior_rows),
    )
