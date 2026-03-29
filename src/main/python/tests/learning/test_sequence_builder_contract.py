import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.datasets import (
    SequenceBuilderConfig,
    build_sequence_dataset,
    read_sequence_dataset,
    write_sequence_dataset,
)
from src.main.python.learning.ingestion import (
    DatasetBundle,
    DatasetManifest,
    DatasetSourceManifest,
    TimeUnit,
)


class SequenceBuilderContractTest(unittest.TestCase):

    def test_build_sequence_dataset_is_deterministic_across_all_sequence_scopes(self):
        bundle = self._sequence_contract_bundle()
        config = SequenceBuilderConfig(bucket_size_seconds=3_600, timezone_id="UTC")

        sequence_dataset = build_sequence_dataset(bundle, config)
        rebuilt_dataset = build_sequence_dataset(bundle, config)

        self.assertEqual(sequence_dataset, rebuilt_dataset)
        self.assertEqual("E2.v1", sequence_dataset.manifest_version)
        self.assertEqual(TimeUnit.SECONDS.value, sequence_dataset.engine_time_unit)
        self.assertEqual(("incident_source", "telemetry_source", "topology_source"), sequence_dataset.source_names)
        self.assertEqual(9, len(sequence_dataset.rows))
        self.assertEqual({"CORRIDOR", "EDGE", "TIME_WINDOW"}, {row.sequence_scope for row in sequence_dataset.rows})

        edge_rows = [row for row in sequence_dataset.rows if row.sequence_scope == "EDGE"]
        self.assertEqual([0, 1], [row.sequence_index for row in edge_rows])
        self.assertEqual(["timestamp", "timestamp"], [row.timestamp_field for row in edge_rows])
        self.assertEqual([28_800, 32_400], [row.timestamp_ticks for row in edge_rows])

        time_window_subjects = {row.subject_id for row in sequence_dataset.rows if row.sequence_scope == "TIME_WINDOW"}
        self.assertEqual({"28800", "32400"}, time_window_subjects)

        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            output_path = write_sequence_dataset(sequence_dataset, temp_dir / "sequence_dataset.parquet")
            reloaded_dataset = read_sequence_dataset(output_path)

        self.assertEqual(sequence_dataset, reloaded_dataset)

    def test_sequence_builder_config_requires_auditable_bucket_policy(self):
        with self.assertRaises(ValueError):
            SequenceBuilderConfig(bucket_size_seconds=1_000, timezone_id="UTC")

        with self.assertRaises(ValueError):
            SequenceBuilderConfig(bucket_size_seconds=3_600, timezone_id=" ")

    def _sequence_contract_bundle(self) -> DatasetBundle:
        incident_rows = (
            {
                "feed_kind": "incident",
                "source_name": "incident_source",
                "id_namespace": "corridor",
                "external_id": "C-7",
                "start_timestamp_ticks": 28_800,
                "end_timestamp_ticks": 32_400,
                "incident_code": "lane_closure",
            },
        )
        telemetry_rows = (
            {
                "feed_kind": "telemetry",
                "source_name": "telemetry_source",
                "id_namespace": "edge",
                "external_id": "E-100",
                "timestamp_ticks": 28_800,
                "travel_time": "42",
                "speed_factor_observed": "0.95",
            },
            {
                "feed_kind": "telemetry",
                "source_name": "telemetry_source",
                "id_namespace": "edge",
                "external_id": "E-100",
                "timestamp_ticks": 32_400,
                "travel_time": "44",
                "speed_factor_observed": "0.90",
            },
        )
        topology_rows = (
            {
                "feed_kind": "topology",
                "source_name": "topology_source",
                "id_namespace": "topology_subject",
                "external_id": "EDGE-9",
                "observed_at_ticks": 28_800,
                "change_type": "profile_update",
            },
        )
        return DatasetBundle(
            manifest=DatasetManifest(
                manifest_version="E1.v1",
                engine_time_unit=TimeUnit.SECONDS.value,
                total_row_count=4,
                total_warning_count=0,
                id_namespace_cardinality={"corridor": 1, "edge": 1, "topology_subject": 1},
                sources=(
                    self._source_manifest("incident_source", "incident", "corridor", 1, 28_800, 32_400, ("start_timestamp", "end_timestamp")),
                    self._source_manifest("telemetry_source", "telemetry", "edge", 2, 28_800, 32_400, ("timestamp",)),
                    self._source_manifest("topology_source", "topology", "topology_subject", 1, 28_800, 28_800, ("observed_at",)),
                ),
            ),
            normalized_rows={
                "incident_source": incident_rows,
                "telemetry_source": telemetry_rows,
                "topology_source": topology_rows,
            },
        )

    def _source_manifest(
        self,
        source_name: str,
        feed_kind: str,
        id_namespace: str,
        row_count: int,
        min_tick: int,
        max_tick: int,
        temporal_fields: tuple[str, ...],
    ) -> DatasetSourceManifest:
        return DatasetSourceManifest(
            source_name=source_name,
            feed_kind=feed_kind,
            id_namespace=id_namespace,
            row_count=row_count,
            unique_id_count=1,
            normalized_tick_range={"min_tick": min_tick, "max_tick": max_tick},
            warning_count=0,
            late_arrival_count=0,
            diagnostic_counts={},
            content_hash=f"{source_name}-hash",
            temporal_fields=temporal_fields,
            lineage={
                "source_uri": f"s3://unit-test/{source_name}.csv",
                "snapshot_id": f"{source_name}:v1",
                "schema_version": "1.0",
            },
            filters={"geography": "blr-core"},
        )


if __name__ == "__main__":
    unittest.main()
