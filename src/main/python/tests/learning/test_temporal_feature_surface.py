import unittest

from src.main.python.learning.datasets import SequenceBuilderConfig, build_sequence_dataset
from src.main.python.learning.ingestion import (
    DatasetBundle,
    DatasetManifest,
    DatasetSourceManifest,
    TimeUnit,
)

TelemetryRow = dict[str, str | int | float]


class TemporalFeatureSurfaceTest(unittest.TestCase):

    def test_build_sequence_dataset_publishes_recency_persistence_periodicity_and_corridor_activity(self):
        bundle = self._feature_surface_bundle()
        sequence_dataset = build_sequence_dataset(
            bundle,
            SequenceBuilderConfig(bucket_size_seconds=3_600, timezone_id="UTC"),
        )

        corridor_rows = [
            row
            for row in sequence_dataset.rows
            if row.sequence_scope == "CORRIDOR" and row.subject_id == "C-7"
        ]

        self.assertEqual(
            [28_800, 30_600, 633_600, 635_400, 637_200, 638_100],
            [row.timestamp_ticks for row in corridor_rows],
        )
        self.assertEqual([0, 1_800, 603_000, 1_800, 1_800, 900], [row.recency_gap_ticks for row in corridor_rows])
        self.assertEqual([1, 2, 1, 2, 3, 4], [row.persistence_run_length for row in corridor_rows])
        self.assertEqual([4, 4, 4, 4, 2, 2], [row.periodicity_bucket_count for row in corridor_rows])
        self.assertEqual([6, 6, 6, 6, 2, 2], [row.corridor_activity_count for row in corridor_rows])
        self.assertTrue(all(row.day_of_week == 3 for row in corridor_rows))
        self.assertEqual(["start_timestamp", "end_timestamp", "start_timestamp", "end_timestamp", "start_timestamp", "end_timestamp"], [row.timestamp_field for row in corridor_rows])

    def test_recency_gap_monotonicity_holds_across_sparse_medium_and_dense_histories(self):
        sequence_dataset = build_sequence_dataset(
            self._density_recency_bundle(),
            SequenceBuilderConfig(bucket_size_seconds=100, timezone_id="UTC"),
        )

        sparse_recent = self._corridor_rows(sequence_dataset, "C-SPARSE-RECENT")
        sparse_stale = self._corridor_rows(sequence_dataset, "C-SPARSE-STALE")
        medium_recent = self._corridor_rows(sequence_dataset, "C-MEDIUM-RECENT")
        medium_stale = self._corridor_rows(sequence_dataset, "C-MEDIUM-STALE")
        dense_recent = self._corridor_rows(sequence_dataset, "C-DENSE-RECENT")
        dense_stale = self._corridor_rows(sequence_dataset, "C-DENSE-STALE")

        self.assertLess(sparse_recent[-1].recency_gap_ticks, sparse_stale[-1].recency_gap_ticks)
        self.assertLess(medium_recent[-1].recency_gap_ticks, medium_stale[-1].recency_gap_ticks)
        self.assertLess(dense_recent[-1].recency_gap_ticks, dense_stale[-1].recency_gap_ticks)
        self.assertEqual(100, sparse_recent[-1].recency_gap_ticks)
        self.assertEqual(900, sparse_stale[-1].recency_gap_ticks)
        self.assertEqual(100, medium_recent[-1].recency_gap_ticks)
        self.assertEqual(700, medium_stale[-1].recency_gap_ticks)
        self.assertEqual(50, dense_recent[-1].recency_gap_ticks)
        self.assertEqual(700, dense_stale[-1].recency_gap_ticks)

    def test_persistence_run_reaches_consecutive_length_and_resets_after_low_activity_gap(self):
        sequence_dataset = build_sequence_dataset(
            self._persistence_pattern_bundle(),
            SequenceBuilderConfig(bucket_size_seconds=100, timezone_id="UTC"),
        )

        consecutive_rows = self._corridor_rows(sequence_dataset, "C-CONSECUTIVE")
        alternating_rows = self._corridor_rows(sequence_dataset, "C-ALTERNATING")

        self.assertEqual([1, 2, 3, 4], [row.persistence_run_length for row in consecutive_rows])
        self.assertEqual([1, 1, 1], [row.persistence_run_length for row in alternating_rows])

    def test_periodicity_feature_distinguishes_period_two_persistent_and_random_histories(self):
        sequence_dataset = build_sequence_dataset(
            self._periodicity_pattern_bundle(),
            SequenceBuilderConfig(bucket_size_seconds=100, timezone_id="UTC"),
        )

        period_two_rows = self._corridor_rows(sequence_dataset, "C-PERIOD-TWO")
        persistent_rows = self._corridor_rows(sequence_dataset, "C-PERSISTENT")
        random_rows = self._corridor_rows(sequence_dataset, "C-RANDOM")

        self.assertEqual({2}, {row.periodicity_bucket_count for row in period_two_rows})
        self.assertEqual({4}, {row.periodicity_bucket_count for row in persistent_rows})
        self.assertEqual({1}, {row.periodicity_bucket_count for row in random_rows})

    def _feature_surface_bundle(self) -> DatasetBundle:
        incident_rows = (
            {
                "feed_kind": "incident",
                "source_name": "incident_source",
                "id_namespace": "corridor",
                "external_id": "C-7",
                "start_timestamp_ticks": 28_800,
                "end_timestamp_ticks": 30_600,
                "incident_code": "lane_closure",
            },
            {
                "feed_kind": "incident",
                "source_name": "incident_source",
                "id_namespace": "corridor",
                "external_id": "C-7",
                "start_timestamp_ticks": 633_600,
                "end_timestamp_ticks": 635_400,
                "incident_code": "lane_closure",
            },
            {
                "feed_kind": "incident",
                "source_name": "incident_source",
                "id_namespace": "corridor",
                "external_id": "C-7",
                "start_timestamp_ticks": 637_200,
                "end_timestamp_ticks": 638_100,
                "incident_code": "clearance_tail",
            },
            {
                "feed_kind": "incident",
                "source_name": "incident_source",
                "id_namespace": "corridor",
                "external_id": "C-9",
                "start_timestamp_ticks": 633_900,
                "end_timestamp_ticks": 635_100,
                "incident_code": "signal_fault",
            },
        )
        return DatasetBundle(
            manifest=DatasetManifest(
                manifest_version="E1.v1",
                engine_time_unit=TimeUnit.SECONDS.value,
                total_row_count=4,
                total_warning_count=0,
                id_namespace_cardinality={"corridor": 2},
                sources=(
                    self._source_manifest(),
                ),
            ),
            normalized_rows={"incident_source": incident_rows},
        )

    def _source_manifest(self) -> DatasetSourceManifest:
        return DatasetSourceManifest(
            source_name="incident_source",
            feed_kind="incident",
            id_namespace="corridor",
            row_count=4,
            unique_id_count=2,
            normalized_tick_range={"min_tick": 28_800, "max_tick": 638_100},
            warning_count=0,
            late_arrival_count=0,
            diagnostic_counts={},
            content_hash="incident-source-hash",
            temporal_fields=("start_timestamp", "end_timestamp"),
            lineage={
                "source_uri": "s3://unit-test/incident.csv",
                "snapshot_id": "incident:v1",
                "schema_version": "1.0",
            },
            filters={"geography": "blr-core"},
        )

    def _density_recency_bundle(self) -> DatasetBundle:
        telemetry_rows = (
            self._telemetry_row("C-SPARSE-RECENT", 900),
            self._telemetry_row("C-SPARSE-RECENT", 1_000),
            self._telemetry_row("C-SPARSE-STALE", 100),
            self._telemetry_row("C-SPARSE-STALE", 1_000),
            self._telemetry_row("C-MEDIUM-RECENT", 700),
            self._telemetry_row("C-MEDIUM-RECENT", 800),
            self._telemetry_row("C-MEDIUM-RECENT", 900),
            self._telemetry_row("C-MEDIUM-RECENT", 1_000),
            self._telemetry_row("C-MEDIUM-STALE", 100),
            self._telemetry_row("C-MEDIUM-STALE", 200),
            self._telemetry_row("C-MEDIUM-STALE", 300),
            self._telemetry_row("C-MEDIUM-STALE", 1_000),
            self._telemetry_row("C-DENSE-RECENT", 800),
            self._telemetry_row("C-DENSE-RECENT", 850),
            self._telemetry_row("C-DENSE-RECENT", 900),
            self._telemetry_row("C-DENSE-RECENT", 950),
            self._telemetry_row("C-DENSE-RECENT", 1_000),
            self._telemetry_row("C-DENSE-STALE", 100),
            self._telemetry_row("C-DENSE-STALE", 150),
            self._telemetry_row("C-DENSE-STALE", 200),
            self._telemetry_row("C-DENSE-STALE", 300),
            self._telemetry_row("C-DENSE-STALE", 1_000),
        )
        return self._telemetry_bundle(telemetry_rows)

    def _persistence_pattern_bundle(self) -> DatasetBundle:
        telemetry_rows = (
            self._telemetry_row("C-CONSECUTIVE", 100),
            self._telemetry_row("C-CONSECUTIVE", 200),
            self._telemetry_row("C-CONSECUTIVE", 300),
            self._telemetry_row("C-CONSECUTIVE", 400),
            self._telemetry_row("C-ALTERNATING", 100),
            self._telemetry_row("C-ALTERNATING", 300),
            self._telemetry_row("C-ALTERNATING", 500),
        )
        return self._telemetry_bundle(telemetry_rows)

    def _periodicity_pattern_bundle(self) -> DatasetBundle:
        telemetry_rows = (
            self._telemetry_row("C-PERIOD-TWO", 100),
            self._telemetry_row("C-PERIOD-TWO", 300),
            self._telemetry_row("C-PERIOD-TWO", 604_900),
            self._telemetry_row("C-PERIOD-TWO", 605_100),
            self._telemetry_row("C-PERSISTENT", 100),
            self._telemetry_row("C-PERSISTENT", 604_900),
            self._telemetry_row("C-PERSISTENT", 1_209_700),
            self._telemetry_row("C-PERSISTENT", 1_814_500),
            self._telemetry_row("C-RANDOM", 100),
            self._telemetry_row("C-RANDOM", 300),
            self._telemetry_row("C-RANDOM", 500),
            self._telemetry_row("C-RANDOM", 700),
        )
        return self._telemetry_bundle(telemetry_rows)

    def _telemetry_bundle(self, telemetry_rows: tuple[TelemetryRow, ...]) -> DatasetBundle:
        return DatasetBundle(
            manifest=DatasetManifest(
                manifest_version="E1.v1",
                engine_time_unit=TimeUnit.SECONDS.value,
                total_row_count=len(telemetry_rows),
                total_warning_count=0,
                id_namespace_cardinality={"corridor": len({row["external_id"] for row in telemetry_rows})},
                sources=(self._telemetry_source_manifest(len(telemetry_rows), telemetry_rows),),
            ),
            normalized_rows={"telemetry_source": telemetry_rows},
        )

    def _telemetry_source_manifest(
        self,
        row_count: int,
        telemetry_rows: tuple[TelemetryRow, ...],
    ) -> DatasetSourceManifest:
        timestamps = [int(row["timestamp_ticks"]) for row in telemetry_rows]
        return DatasetSourceManifest(
            source_name="telemetry_source",
            feed_kind="telemetry",
            id_namespace="corridor",
            row_count=row_count,
            unique_id_count=len({row["external_id"] for row in telemetry_rows}),
            normalized_tick_range={"min_tick": min(timestamps), "max_tick": max(timestamps)},
            warning_count=0,
            late_arrival_count=0,
            diagnostic_counts={},
            content_hash="telemetry-source-hash",
            temporal_fields=("timestamp",),
            lineage={
                "source_uri": "s3://unit-test/telemetry.csv",
                "snapshot_id": "telemetry:v1",
                "schema_version": "1.0",
            },
            filters={"geography": "blr-core"},
        )

    def _telemetry_row(self, corridor_id: str, timestamp_ticks: int) -> TelemetryRow:
        return {
            "feed_kind": "telemetry",
            "source_name": "telemetry_source",
            "id_namespace": "corridor",
            "external_id": corridor_id,
            "timestamp_ticks": timestamp_ticks,
            "travel_time": 1.0,
        }

    def _corridor_rows(self, sequence_dataset, corridor_id: str):
        return [
            row
            for row in sequence_dataset.rows
            if row.sequence_scope == "CORRIDOR" and row.subject_id == corridor_id
        ]


if __name__ == "__main__":
    unittest.main()
