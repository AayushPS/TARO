import unittest

from src.main.python.learning.calibration import build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow, SequenceDatasetArtifact, SequenceRow
from src.main.python.learning.forecasting import ForecastTrainingConfig, train_forecast_bundle


class AblationDeterminismTest(unittest.TestCase):

    def test_feature_rich_forecast_improves_periodic_case_over_profile_only(self):
        baseline_bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=3,
                history_window_buckets=20,
                recency_half_life_buckets=4,
                enabled_features=(),
            ),
        )
        rich_bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=3,
                history_window_buckets=20,
                recency_half_life_buckets=4,
                enabled_features=("periodicity", "recency"),
            ),
        )

        baseline_row = self._forecast_row(baseline_bundle, "EDGE", "EDGE_PERIODIC", 3, 8)
        rich_row = self._forecast_row(rich_bundle, "EDGE", "EDGE_PERIODIC", 3, 8)
        expected_prediction = 29.0

        self.assertEqual(baseline_row.baseline_prediction, baseline_row.feature_rich_prediction)
        self.assertLess(
            abs(rich_row.feature_rich_prediction - expected_prediction),
            abs(baseline_row.feature_rich_prediction - expected_prediction),
        )

    def test_same_seed_and_input_produce_identical_training_bundle(self):
        config = ForecastTrainingConfig(
            training_seed=19,
            history_window_buckets=20,
            recency_half_life_buckets=4,
        )
        first_bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            config,
        )
        second_bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            config,
        )

        self.assertEqual(first_bundle, second_bundle)

    def test_periodicity_feature_changes_signed_e4_prior_spread_for_periodic_corridor(self):
        periodic_sequence_dataset = self._periodic_corridor_sequence_dataset()
        balanced_frequency_artifact = self._balanced_corridor_frequency_artifact()
        recency_only_bundle = train_forecast_bundle(
            periodic_sequence_dataset,
            balanced_frequency_artifact,
            ForecastTrainingConfig(
                training_seed=19,
                history_window_buckets=20,
                recency_half_life_buckets=4,
                enabled_features=("recency",),
            ),
        )
        rich_bundle = train_forecast_bundle(
            periodic_sequence_dataset,
            balanced_frequency_artifact,
            ForecastTrainingConfig(
                training_seed=19,
                history_window_buckets=20,
                recency_half_life_buckets=4,
                enabled_features=("periodicity", "recency"),
            ),
        )

        recency_only_priors = self._prior_rows(build_calibration_bundle(recency_only_bundle, balanced_frequency_artifact))
        rich_priors = self._prior_rows(build_calibration_bundle(rich_bundle, balanced_frequency_artifact))

        recency_rush = recency_only_priors[("C-PERIODIC", 7)]
        recency_offpeak = recency_only_priors[("C-PERIODIC", 11)]
        rich_rush = rich_priors[("C-PERIODIC", 7)]
        rich_offpeak = rich_priors[("C-PERIODIC", 11)]

        recency_spread = recency_rush.calibrated_prior_probability - recency_offpeak.calibrated_prior_probability
        rich_spread = rich_rush.calibrated_prior_probability - rich_offpeak.calibrated_prior_probability

        self.assertGreater(rich_spread, recency_spread + 0.05)
        self.assertGreater(rich_rush.calibrated_prior_probability, recency_rush.calibrated_prior_probability)
        self.assertLess(rich_offpeak.calibrated_prior_probability, recency_offpeak.calibrated_prior_probability)
        self.assertAlmostEqual(0.5, rich_priors[("C-FLAT", 7)].calibrated_prior_probability, delta=1.0e-9)
        self.assertAlmostEqual(0.5, rich_priors[("C-FLAT", 11)].calibrated_prior_probability, delta=1.0e-9)

    def _forecast_row(self, bundle, sequence_scope: str, subject_id: str, day_of_week: int, bucket_index: int):
        for row in bundle.forecast_surface.rows:
            if (
                row.sequence_scope == sequence_scope
                and row.subject_id == subject_id
                and row.day_of_week == day_of_week
                and row.bucket_index == bucket_index
            ):
                return row
        self.fail(f"missing forecast row for {sequence_scope} {subject_id} {day_of_week} {bucket_index}")

    def _prior_rows(self, calibration_bundle):
        return {
            (row.corridor_id, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }

    def _sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            source_names=("corridor", "edge"),
            rows=(
                self._row("EDGE", "EDGE_PERIODIC", 3, 8, 1_000, 30.0, 1, 3, 1),
                self._row("EDGE", "EDGE_PERIODIC", 3, 8, 1_300, 31.0, 2, 3, 1),
                self._row("EDGE", "EDGE_PERIODIC", 3, 8, 1_600, 29.0, 3, 3, 1),
                self._row("EDGE", "EDGE_PERIODIC", 3, 11, 1_500, 12.0, 1, 1, 1),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_000, 40.0, 1, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_300, 42.0, 2, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_600, 44.0, 3, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 9, 1_450, 18.0, 1, 1, 2),
            ),
        )

    def _periodic_corridor_sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            source_names=("corridor",),
            rows=(
                self._row("CORRIDOR", "C-PERIODIC", 3, 7, 1_000, 46.0, 1, 2, 4),
                self._row("CORRIDOR", "C-PERIODIC", 3, 7, 1_200, 47.0, 2, 2, 4),
                self._row("CORRIDOR", "C-PERIODIC", 3, 11, 1_400, 38.0, 1, 2, 4),
                self._row("CORRIDOR", "C-PERIODIC", 3, 11, 1_600, 37.0, 2, 2, 4),
                self._row("CORRIDOR", "C-FLAT", 3, 7, 1_000, 42.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT", 3, 7, 1_200, 41.5, 2, 1, 4),
                self._row("CORRIDOR", "C-FLAT", 3, 11, 1_400, 41.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT", 3, 11, 1_600, 41.5, 2, 1, 4),
            ),
        )

    def _corridor_frequency_artifact(self) -> CorridorBucketFrequencyArtifact:
        return CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow(
                    corridor_id="C-DENSE",
                    day_of_week=3,
                    bucket_index=8,
                    observation_count=3,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.75,
                ),
                CorridorBucketFrequencyRow(
                    corridor_id="C-DENSE",
                    day_of_week=3,
                    bucket_index=9,
                    observation_count=1,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.25,
                ),
            ),
        )

    def _balanced_corridor_frequency_artifact(self) -> CorridorBucketFrequencyArtifact:
        return CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-PERIODIC", 3, 7, 2, 4, 0.50),
                CorridorBucketFrequencyRow("C-PERIODIC", 3, 11, 2, 4, 0.50),
                CorridorBucketFrequencyRow("C-FLAT", 3, 7, 2, 4, 0.50),
                CorridorBucketFrequencyRow("C-FLAT", 3, 11, 2, 4, 0.50),
            ),
        )

    def _row(
        self,
        sequence_scope: str,
        subject_id: str,
        day_of_week: int,
        bucket_index: int,
        timestamp_ticks: int,
        travel_time: float,
        persistence_run_length: int,
        periodicity_bucket_count: int,
        corridor_activity_count: int,
    ) -> SequenceRow:
        return SequenceRow(
            sequence_scope=sequence_scope,
            feed_kind="telemetry" if sequence_scope == "EDGE" else "incident",
            source_name="unit_test",
            id_namespace="edge" if sequence_scope == "EDGE" else "corridor",
            subject_id=subject_id,
            timestamp_field="timestamp",
            timestamp_ticks=timestamp_ticks,
            window_start_ticks=(timestamp_ticks // 100) * 100,
            day_of_week=day_of_week,
            bucket_index=bucket_index,
            sequence_index=0,
            recency_gap_ticks=0,
            persistence_run_length=persistence_run_length,
            periodicity_bucket_count=periodicity_bucket_count,
            corridor_activity_count=corridor_activity_count,
            travel_time=travel_time,
            speed_factor_observed=None,
            event_code=None,
        )


if __name__ == "__main__":
    unittest.main()
