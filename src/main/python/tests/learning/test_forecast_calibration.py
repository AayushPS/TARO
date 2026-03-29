import unittest

from src.main.python.learning.calibration import CalibrationSelectionConfig, build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow
from src.main.python.learning.forecasting import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
)


class ForecastCalibrationTest(unittest.TestCase):

    def test_calibration_bundle_publishes_baseline_table_and_evidence_responsive_priors(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-ARTERIAL", 3, 8, 6, 10, 0.60),
                CorridorBucketFrequencyRow("C-LOCAL", 3, 8, 6, 10, 0.60),
            ),
        )
        training_bundle = self._training_bundle(
            representation_rows=(
                TemporalRepresentationRow("CORRIDOR", "C-ARTERIAL", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.3, 0.6, 0.5, 0.90, 0.85),
                TemporalRepresentationRow("CORRIDOR", "C-LOCAL", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.3, 0.6, 0.5, 0.20, 0.85),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-ARTERIAL", "EVENT_INTENSITY", 3, 8, 8, 0.60, 30.0, 39.0, 0.85),
                ForecastSurfaceRow("CORRIDOR", "C-LOCAL", "EVENT_INTENSITY", 3, 8, 8, 0.60, 30.0, 39.0, 0.85),
            ),
        )

        calibration_bundle = build_calibration_bundle(
            training_bundle,
            corridor_bucket_frequency,
            CalibrationSelectionConfig(
                minimum_confidence=0.60,
                minimum_relative_improvement=0.05,
                maximum_prior_adjustment=0.25,
                preferential_attachment_range=0.10,
            ),
        )

        by_corridor = {
            (row.corridor_id, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }
        arterial = by_corridor[("C-ARTERIAL", 3, 8)]
        local = by_corridor[("C-LOCAL", 3, 8)]

        self.assertEqual("E4.v1", calibration_bundle.manifest_version)
        self.assertEqual(corridor_bucket_frequency, calibration_bundle.published_corridor_bucket_frequency)
        self.assertTrue(arterial.confidence_qualified)
        self.assertTrue(local.confidence_qualified)
        self.assertGreater(arterial.evidence_response, 0.0)
        self.assertAlmostEqual(arterial.evidence_response, local.evidence_response)
        self.assertGreater(arterial.preferential_attachment_adjustment, 0.0)
        self.assertGreater(arterial.preferential_attachment_adjustment, local.preferential_attachment_adjustment)
        self.assertGreater(
            arterial.calibrated_prior_probability - arterial.historical_bucket_frequency,
            local.calibrated_prior_probability - local.historical_bucket_frequency,
        )

    def test_preferential_attachment_disappears_without_evidence(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(CorridorBucketFrequencyRow("C-STATIC", 3, 8, 3, 4, 0.75),),
        )
        training_bundle = self._training_bundle(
            representation_rows=(
                TemporalRepresentationRow("CORRIDOR", "C-STATIC", "EVENT_INTENSITY", 6, 1_000, 1.0, 1.0, 0.6, 0.4, 0.95, 0.90),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-STATIC", "EVENT_INTENSITY", 3, 8, 6, 0.75, 30.0, 30.0, 0.90),
            ),
        )

        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)
        prior_row = calibration_bundle.scenario_prior_calibrations[0]

        self.assertFalse(prior_row.confidence_qualified)
        self.assertEqual("insufficient_improvement", prior_row.rejection_reason)
        self.assertEqual(0.0, prior_row.evidence_response)
        self.assertEqual(0.0, prior_row.preferential_attachment_adjustment)
        self.assertEqual(prior_row.historical_bucket_frequency, prior_row.calibrated_prior_probability)

    def test_negative_forecast_shift_lowers_prior_below_historical_baseline(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-PEAK", 3, 8, 4, 8, 0.50),
                CorridorBucketFrequencyRow("C-OFFPEAK", 3, 8, 4, 8, 0.50),
            ),
        )
        training_bundle = self._training_bundle(
            representation_rows=(
                TemporalRepresentationRow("CORRIDOR", "C-PEAK", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.2, 0.4, 0.5, 0.5, 0.90),
                TemporalRepresentationRow("CORRIDOR", "C-OFFPEAK", "EVENT_INTENSITY", 8, 1_000, 1.0, 0.8, 0.4, 0.5, 0.5, 0.90),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-PEAK", "EVENT_INTENSITY", 3, 8, 8, 0.50, 40.0, 46.0, 0.90),
                ForecastSurfaceRow("CORRIDOR", "C-OFFPEAK", "EVENT_INTENSITY", 3, 8, 8, 0.50, 40.0, 34.0, 0.90),
            ),
        )

        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)
        by_corridor = {
            (row.corridor_id, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }
        peak = by_corridor[("C-PEAK", 3, 8)]
        offpeak = by_corridor[("C-OFFPEAK", 3, 8)]

        self.assertTrue(peak.confidence_qualified)
        self.assertTrue(offpeak.confidence_qualified)
        self.assertGreater(peak.calibrated_prior_probability, peak.historical_bucket_frequency)
        self.assertLess(offpeak.calibrated_prior_probability, offpeak.historical_bucket_frequency)
        self.assertAlmostEqual(
            peak.calibrated_prior_probability - peak.historical_bucket_frequency,
            offpeak.historical_bucket_frequency - offpeak.calibrated_prior_probability,
            delta=0.02,
        )

    def test_same_zone_corridor_receives_bounded_homophily_uplift_without_exceeding_primary_override(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-ZONE-A1", 3, 8, 4, 10, 0.40),
                CorridorBucketFrequencyRow("C-ZONE-A2", 3, 8, 4, 10, 0.40),
                CorridorBucketFrequencyRow("C-ZONE-B1", 3, 8, 4, 10, 0.40),
            ),
        )
        training_bundle = self._training_bundle(
            representation_rows=(
                TemporalRepresentationRow("CORRIDOR", "C-ZONE-A1", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.6, 0.5, 0.4, 0.50, 0.90),
                TemporalRepresentationRow("CORRIDOR", "C-ZONE-A2", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.0, 0.5, 0.4, 0.50, 0.90),
                TemporalRepresentationRow("CORRIDOR", "C-ZONE-B1", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.0, 0.5, 0.4, 0.50, 0.90),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-ZONE-A1", "EVENT_INTENSITY", 3, 8, 8, 0.40, 20.0, 26.0, 0.90, "ZONE-A"),
                ForecastSurfaceRow("CORRIDOR", "C-ZONE-A2", "EVENT_INTENSITY", 3, 8, 8, 0.40, 20.0, 20.4, 0.90, "ZONE-A"),
                ForecastSurfaceRow("CORRIDOR", "C-ZONE-B1", "EVENT_INTENSITY", 3, 8, 8, 0.40, 20.0, 20.4, 0.90, "ZONE-B"),
            ),
        )

        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)
        by_corridor = {
            (row.corridor_id, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }
        primary = by_corridor[("C-ZONE-A1", 3, 8)]
        same_zone_peer = by_corridor[("C-ZONE-A2", 3, 8)]
        other_zone = by_corridor[("C-ZONE-B1", 3, 8)]

        self.assertGreater(primary.evidence_response, 0.0)
        self.assertEqual(0.0, primary.homophily_adjustment)
        self.assertFalse(same_zone_peer.confidence_qualified)
        self.assertFalse(other_zone.confidence_qualified)
        self.assertGreater(same_zone_peer.homophily_adjustment, 0.0)
        self.assertEqual(0.0, other_zone.homophily_adjustment)
        self.assertGreater(
            same_zone_peer.calibrated_prior_probability,
            other_zone.calibrated_prior_probability,
        )
        self.assertGreater(
            primary.calibrated_prior_probability - primary.historical_bucket_frequency,
            same_zone_peer.calibrated_prior_probability - same_zone_peer.historical_bucket_frequency,
        )

    def _training_bundle(
        self,
        representation_rows: tuple[TemporalRepresentationRow, ...],
        forecast_rows: tuple[ForecastSurfaceRow, ...],
    ) -> ForecastTrainingBundle:
        config = ForecastTrainingConfig(
            training_seed=7,
            history_window_buckets=12,
            recency_half_life_buckets=4,
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
