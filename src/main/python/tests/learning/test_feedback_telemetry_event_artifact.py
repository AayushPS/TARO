import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.feedback import (
    TelemetryEventArtifact,
    TelemetryEventRow,
    read_telemetry_event_artifact,
    write_telemetry_event_artifact,
)


class TelemetryEventArtifactTest(unittest.TestCase):

    def test_telemetry_event_artifact_round_trips_with_filter_metadata(self):
        artifact = TelemetryEventArtifact(
            manifest_version="F2.v1",
            exported_at="2026-03-28T12:00:00Z",
            result_kind_filter="ROUTE",
            topology_version_filter="topo-api",
            scenario_bundle_filter="bundle-api",
            trait_hash_filter="trait-hash-api",
            complete_only=True,
            rows=(
                TelemetryEventRow(
                    result_kind="ROUTE",
                    prediction_id="ROUTE:route-1",
                    result_set_id="route-1",
                    served_at="2026-03-28T12:00:00Z",
                    feedback_recorded_at="2026-03-28T12:01:00Z",
                    outcome_status="COMPLETE",
                    complete=True,
                    caller_hash="hash-a",
                    topology_version_id="topo-api",
                    model_version="model-api",
                    source_data_lineage_hash="lineage-topo-api",
                    change_set_hash="change-topo-api",
                    trait_bundle_id="bundle-default",
                    trait_hash="trait-hash-api",
                    execution_profile_id="dijkstra",
                    quarantine_snapshot_id="quarantine-topo-api:0",
                    scenario_bundle_id="bundle-api",
                    scenario_count=2,
                    scenario_ids=("baseline", "incident_persists"),
                    scenario_labels=("baseline", "incident_persists"),
                    scenario_probabilities=(0.6, 0.4),
                    departure_ticks=0,
                    horizon_ticks=3_600,
                    preferred_objective="EXPECTED_ETA",
                    top_k_alternatives=2,
                    predicted_expected_cost_seconds=100.0,
                    predicted_robust_cost_seconds=130.0,
                    matrix_source_count=None,
                    matrix_target_count=None,
                    observed_at_ticks=480,
                    observed_arrival_ticks=540,
                    observed_cost_seconds=120.5,
                    observation_count=1,
                    partition_date="2026-03-28",
                ),
            ),
        )

        with TemporaryDirectory() as temp_dir_name:
            output_path = Path(temp_dir_name) / "telemetry_event.parquet"
            written_path = write_telemetry_event_artifact(artifact, output_path)
            round_tripped = read_telemetry_event_artifact(output_path)

        self.assertEqual("telemetry_event.parquet", written_path.name)
        self.assertEqual(artifact, round_tripped)

    def test_empty_complete_only_export_stays_valid(self):
        artifact = TelemetryEventArtifact(
            manifest_version="F2.v1",
            exported_at="2026-03-28T12:05:00Z",
            result_kind_filter=None,
            topology_version_filter="topo-api",
            scenario_bundle_filter=None,
            trait_hash_filter=None,
            complete_only=True,
            rows=(),
        )

        with TemporaryDirectory() as temp_dir_name:
            output_path = Path(temp_dir_name) / "telemetry_event.parquet"
            write_telemetry_event_artifact(artifact, output_path)
            round_tripped = read_telemetry_event_artifact(output_path)

        self.assertEqual(artifact, round_tripped)


if __name__ == "__main__":
    unittest.main()
