import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.datasets import (
    CorridorBucketFrequencyRow,
    SequenceDatasetArtifact,
    SequenceRow,
    build_corridor_bucket_frequency,
    read_corridor_bucket_frequency,
    write_corridor_bucket_frequency,
)


class CorridorBucketFrequencyArtifactTest(unittest.TestCase):

    def test_build_corridor_bucket_frequency_emits_dense_and_sparse_corridor_baselines(self):
        sequence_dataset = self._sequence_dataset()

        corridor_bucket_frequency = build_corridor_bucket_frequency(sequence_dataset)

        self.assertEqual(
            {
                ("C-local", 3, 8): CorridorBucketFrequencyRow(
                    corridor_id="C-local",
                    day_of_week=3,
                    bucket_index=8,
                    observation_count=1,
                    total_corridor_observations=1,
                    historical_bucket_frequency=1.0,
                ),
                ("C-major", 3, 8): CorridorBucketFrequencyRow(
                    corridor_id="C-major",
                    day_of_week=3,
                    bucket_index=8,
                    observation_count=3,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.75,
                ),
                ("C-major", 3, 9): CorridorBucketFrequencyRow(
                    corridor_id="C-major",
                    day_of_week=3,
                    bucket_index=9,
                    observation_count=1,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.25,
                ),
            },
            {
                (row.corridor_id, row.day_of_week, row.bucket_index): row
                for row in corridor_bucket_frequency.rows
            },
        )

        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            output_path = write_corridor_bucket_frequency(
                corridor_bucket_frequency,
                temp_dir / "corridor_bucket_frequency.parquet",
            )
            reloaded_artifact = read_corridor_bucket_frequency(output_path)

        self.assertEqual(corridor_bucket_frequency, reloaded_artifact)

    def _sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=3_600,
            timezone_id="UTC",
            source_names=("incident_source", "telemetry_source"),
            rows=(
                self._corridor_row("C-major", 28_800, 28_800, 8, 0, 0, 1, 3, 4),
                self._corridor_row("C-major", 30_600, 28_800, 8, 1, 1_800, 2, 3, 4),
                self._corridor_row("C-major", 633_600, 633_600, 8, 2, 603_000, 1, 3, 4),
                self._corridor_row("C-major", 637_200, 637_200, 9, 3, 3_600, 2, 1, 1),
                self._corridor_row("C-local", 633_900, 633_600, 8, 0, 0, 1, 1, 4),
                SequenceRow(
                    sequence_scope="EDGE",
                    feed_kind="telemetry",
                    source_name="telemetry_source",
                    id_namespace="edge",
                    subject_id="E-100",
                    timestamp_field="timestamp",
                    timestamp_ticks=28_800,
                    window_start_ticks=28_800,
                    day_of_week=3,
                    bucket_index=8,
                    sequence_index=0,
                    recency_gap_ticks=0,
                    persistence_run_length=1,
                    periodicity_bucket_count=1,
                    corridor_activity_count=4,
                    travel_time=42.0,
                    speed_factor_observed=0.95,
                    event_code=None,
                ),
            ),
        )

    def _corridor_row(
        self,
        corridor_id: str,
        timestamp_ticks: int,
        window_start_ticks: int,
        bucket_index: int,
        sequence_index: int,
        recency_gap_ticks: int,
        persistence_run_length: int,
        periodicity_bucket_count: int,
        corridor_activity_count: int,
    ) -> SequenceRow:
        return SequenceRow(
            sequence_scope="CORRIDOR",
            feed_kind="incident",
            source_name="incident_source",
            id_namespace="corridor",
            subject_id=corridor_id,
            timestamp_field="start_timestamp",
            timestamp_ticks=timestamp_ticks,
            window_start_ticks=window_start_ticks,
            day_of_week=3,
            bucket_index=bucket_index,
            sequence_index=sequence_index,
            recency_gap_ticks=recency_gap_ticks,
            persistence_run_length=persistence_run_length,
            periodicity_bucket_count=periodicity_bucket_count,
            corridor_activity_count=corridor_activity_count,
            travel_time=None,
            speed_factor_observed=None,
            event_code="incident",
        )


if __name__ == "__main__":
    unittest.main()
