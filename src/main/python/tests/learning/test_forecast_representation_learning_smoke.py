import unittest

from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow, SequenceDatasetArtifact, SequenceRow
from src.main.python.learning.forecasting import ForecastTrainingConfig, train_forecast_bundle


class ForecastRepresentationLearningSmokeTest(unittest.TestCase):

    def test_train_forecast_bundle_builds_representations_and_bucket_surfaces(self):
        bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=7,
                history_window_buckets=20,
                recency_half_life_buckets=4,
            ),
        )

        self.assertEqual("E3.v1", bundle.manifest_version)
        self.assertEqual(4, len(bundle.representations.rows))
        self.assertEqual(5, len(bundle.forecast_surface.rows))
        self.assertEqual({"density", "periodicity", "persistence", "recency"}, set(bundle.config.enabled_features))

        representation_by_subject = {
            row.subject_id: row
            for row in bundle.representations.rows
        }
        self.assertEqual({"C-MAIN", "C-SPARSE", "EDGE_NB", "EDGE_SB"}, set(representation_by_subject))
        self.assertGreater(representation_by_subject["EDGE_NB"].recent_signal, representation_by_subject["EDGE_NB"].mean_signal)
        self.assertLess(representation_by_subject["C-SPARSE"].confidence, representation_by_subject["C-MAIN"].confidence)

        main_bucket = self._forecast_row(bundle, "CORRIDOR", "C-MAIN", 3, 8)
        self.assertGreater(main_bucket.feature_rich_prediction, main_bucket.baseline_prediction)
        self.assertGreater(main_bucket.bucket_baseline_frequency, 0.70)
        self.assertTrue(0.0 <= main_bucket.confidence <= 1.0)

    def test_train_forecast_bundle_respects_history_window(self):
        bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=11,
                history_window_buckets=3,
                recency_half_life_buckets=2,
            ),
        )

        representation_by_subject = {
            row.subject_id: row
            for row in bundle.representations.rows
        }
        self.assertEqual(2, representation_by_subject["EDGE_NB"].observation_count)
        self.assertEqual(2, representation_by_subject["C-MAIN"].observation_count)
        self.assertEqual(1_400, bundle.representations.history_window_end_ticks)
        self.assertEqual(1_100, bundle.representations.history_window_start_ticks)

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

    def _sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            source_names=("corridor", "edge"),
            rows=(
                self._row("EDGE", "EDGE_NB", 3, 8, 900, 20.0, 1, 2, 2),
                self._row("EDGE", "EDGE_NB", 3, 8, 1_200, 22.0, 2, 2, 3),
                self._row("EDGE", "EDGE_NB", 3, 8, 1_400, 35.0, 3, 2, 5),
                self._row("EDGE", "EDGE_SB", 3, 8, 1_180, 43.0, 1, 1, 2),
                self._row("EDGE", "EDGE_SB", 3, 8, 1_380, 45.0, 2, 1, 2),
                self._row("CORRIDOR", "C-MAIN", 3, 8, 950, 28.0, 1, 3, 6),
                self._row("CORRIDOR", "C-MAIN", 3, 8, 1_250, 30.0, 2, 3, 6),
                self._row("CORRIDOR", "C-MAIN", 3, 9, 1_400, 18.0, 1, 1, 2),
                self._row("CORRIDOR", "C-SPARSE", 3, 10, 1_350, 17.0, 1, 1, 1),
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
                    corridor_id="C-MAIN",
                    day_of_week=3,
                    bucket_index=8,
                    observation_count=3,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.75,
                ),
                CorridorBucketFrequencyRow(
                    corridor_id="C-MAIN",
                    day_of_week=3,
                    bucket_index=9,
                    observation_count=1,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.25,
                ),
                CorridorBucketFrequencyRow(
                    corridor_id="C-SPARSE",
                    day_of_week=3,
                    bucket_index=10,
                    observation_count=1,
                    total_corridor_observations=1,
                    historical_bucket_frequency=1.0,
                ),
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
