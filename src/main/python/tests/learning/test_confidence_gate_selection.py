import unittest

from src.main.python.learning.calibration import build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow
from src.main.python.learning.forecasting import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
)


class ConfidenceGateSelectionTest(unittest.TestCase):

    def test_confidence_gate_rejects_low_support_candidates_and_falls_back_to_baseline(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-QUALIFIED", 3, 8, 4, 10, 0.40),
                CorridorBucketFrequencyRow("C-REJECTED", 3, 8, 4, 10, 0.40),
            ),
        )
        training_bundle = self._training_bundle(
            representation_rows=(
                TemporalRepresentationRow("CORRIDOR", "C-QUALIFIED", "EVENT_INTENSITY", 6, 1_000, 1.0, 1.2, 0.6, 0.5, 0.80, 0.78),
                TemporalRepresentationRow("CORRIDOR", "C-REJECTED", "EVENT_INTENSITY", 1, 1_000, 1.0, 1.3, 0.2, 0.2, 0.95, 0.35),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-QUALIFIED", "EVENT_INTENSITY", 3, 8, 6, 0.40, 20.0, 25.0, 0.78),
                ForecastSurfaceRow("CORRIDOR", "C-REJECTED", "EVENT_INTENSITY", 3, 8, 1, 0.40, 20.0, 26.0, 0.35),
            ),
        )

        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)

        profile_by_subject = {
            row.subject_id: row
            for row in calibration_bundle.refined_profile_selections
        }
        prior_by_subject = {
            row.corridor_id: row
            for row in calibration_bundle.scenario_prior_calibrations
        }

        qualified_profile = profile_by_subject["C-QUALIFIED"]
        rejected_profile = profile_by_subject["C-REJECTED"]
        qualified_prior = prior_by_subject["C-QUALIFIED"]
        rejected_prior = prior_by_subject["C-REJECTED"]

        self.assertTrue(qualified_profile.accepted)
        self.assertIsNone(qualified_profile.rejection_reason)
        self.assertEqual(qualified_profile.feature_rich_prediction, qualified_profile.calibrated_prediction)
        self.assertTrue(qualified_prior.confidence_qualified)
        self.assertGreater(qualified_prior.calibrated_prior_probability, qualified_prior.historical_bucket_frequency)

        self.assertFalse(rejected_profile.accepted)
        self.assertEqual("low_confidence", rejected_profile.rejection_reason)
        self.assertEqual(rejected_profile.baseline_prediction, rejected_profile.calibrated_prediction)
        self.assertFalse(rejected_prior.confidence_qualified)
        self.assertEqual("low_confidence", rejected_prior.rejection_reason)
        self.assertEqual(0.0, rejected_prior.evidence_response)
        self.assertEqual(
            rejected_prior.historical_bucket_frequency,
            rejected_prior.calibrated_prior_probability,
        )

    def _training_bundle(
        self,
        representation_rows: tuple[TemporalRepresentationRow, ...],
        forecast_rows: tuple[ForecastSurfaceRow, ...],
    ) -> ForecastTrainingBundle:
        config = ForecastTrainingConfig(
            training_seed=11,
            history_window_buckets=10,
            recency_half_life_buckets=3,
        )
        return ForecastTrainingBundle(
            manifest_version="E3.v1",
            config=config,
            representations=TemporalRepresentationArtifact(
                manifest_version="E3.v1",
                sequence_manifest_version="E2.v1",
                training_seed=config.training_seed,
                history_window_start_ticks=0,
                history_window_end_ticks=1_000,
                enabled_features=config.enabled_features,
                rows=representation_rows,
            ),
            forecast_surface=ForecastSurfaceArtifact(
                manifest_version="E3.v1",
                sequence_manifest_version="E2.v1",
                training_seed=config.training_seed,
                enabled_features=config.enabled_features,
                rows=forecast_rows,
            ),
        )


if __name__ == "__main__":
    unittest.main()
