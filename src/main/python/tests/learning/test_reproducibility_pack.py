import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.calibration import CalibrationSelectionConfig, build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow
from src.main.python.learning.export import (
    build_reproducibility_pack,
    read_reproducibility_pack,
    write_reproducibility_pack,
)
from src.main.python.learning.forecasting import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
)
from src.main.python.learning.ingestion import DatasetManifest, DatasetSourceManifest


class ReproducibilityPackTest(unittest.TestCase):

    def test_same_input_produces_identical_release_artifact_and_round_trip_pack(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()

        first_pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)
        second_pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)

        self.assertEqual(first_pack, second_pack)
        self.assertEqual(
            first_pack.release_artifact.release_artifact_id,
            second_pack.release_artifact.release_artifact_id,
        )

        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            manifest_path = write_reproducibility_pack(first_pack, temp_dir)
            reloaded_pack = read_reproducibility_pack(temp_dir)

        self.assertEqual("reproducibility_pack.json", manifest_path.name)
        self.assertEqual(first_pack, reloaded_pack)

    def test_candidate_and_decision_reports_separate_accepted_and_rejected_evidence(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()
        pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)

        candidate_by_subject = {
            row.subject_id: row
            for row in pack.candidate_report.rows
        }
        accepted_candidate = candidate_by_subject["C-QUALIFIED"]
        rejected_candidate = candidate_by_subject["C-REJECTED"]

        self.assertTrue(accepted_candidate.accepted)
        self.assertIsNone(accepted_candidate.rejection_reason)
        self.assertEqual(
            accepted_candidate.feature_rich_prediction,
            accepted_candidate.calibrated_prediction,
        )
        self.assertFalse(rejected_candidate.accepted)
        self.assertEqual("low_confidence", rejected_candidate.rejection_reason)
        self.assertEqual(
            rejected_candidate.baseline_prediction,
            rejected_candidate.calibrated_prediction,
        )

        decisions_by_key = {
            (row.decision_type, row.subject_id): row
            for row in pack.refinement_decisions.rows
        }
        accepted_profile = decisions_by_key[("profile_refinement", "C-QUALIFIED")]
        rejected_profile = decisions_by_key[("profile_refinement", "C-REJECTED")]
        accepted_prior = decisions_by_key[("scenario_prior", "C-QUALIFIED")]
        rejected_prior = decisions_by_key[("scenario_prior", "C-REJECTED")]

        self.assertTrue(accepted_profile.accepted)
        self.assertIsNone(accepted_profile.rejection_reason)
        self.assertFalse(rejected_profile.accepted)
        self.assertEqual("low_confidence", rejected_profile.rejection_reason)
        self.assertTrue(accepted_prior.accepted)
        self.assertGreater(accepted_prior.calibrated_value, accepted_prior.baseline_value)
        self.assertFalse(rejected_prior.accepted)
        self.assertEqual("low_confidence", rejected_prior.rejection_reason)
        self.assertEqual(0.0, rejected_prior.evidence_response)
        self.assertEqual(rejected_prior.baseline_value, rejected_prior.calibrated_value)
        self.assertTrue(pack.validation_report.correctness_gates["accepted_rejected_separation"])

    def _pack_inputs(self) -> tuple[DatasetManifest, ForecastTrainingBundle, object]:
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
                TemporalRepresentationRow(
                    "CORRIDOR",
                    "C-QUALIFIED",
                    "EVENT_INTENSITY",
                    6,
                    1_000,
                    1.0,
                    1.2,
                    0.6,
                    0.5,
                    0.80,
                    0.78,
                ),
                TemporalRepresentationRow(
                    "CORRIDOR",
                    "C-REJECTED",
                    "EVENT_INTENSITY",
                    1,
                    1_000,
                    1.0,
                    1.3,
                    0.2,
                    0.2,
                    0.95,
                    0.35,
                ),
            ),
            forecast_rows=(
                ForecastSurfaceRow("CORRIDOR", "C-QUALIFIED", "EVENT_INTENSITY", 3, 8, 6, 0.40, 20.0, 25.0, 0.78),
                ForecastSurfaceRow("CORRIDOR", "C-REJECTED", "EVENT_INTENSITY", 3, 8, 1, 0.40, 20.0, 26.0, 0.35),
            ),
        )
        calibration_bundle = build_calibration_bundle(
            training_bundle,
            corridor_bucket_frequency,
            CalibrationSelectionConfig(
                minimum_confidence=0.60,
                minimum_relative_improvement=0.05,
                maximum_prior_adjustment=0.20,
                preferential_attachment_range=0.10,
            ),
        )
        return self._dataset_manifest(), training_bundle, calibration_bundle

    def _dataset_manifest(self) -> DatasetManifest:
        return DatasetManifest(
            manifest_version="E1.v1",
            engine_time_unit="SECONDS",
            total_row_count=2,
            total_warning_count=0,
            id_namespace_cardinality={"edge": 2},
            sources=(
                DatasetSourceManifest(
                    source_name="telemetry_source",
                    feed_kind="telemetry",
                    id_namespace="edge",
                    row_count=2,
                    unique_id_count=2,
                    normalized_tick_range={"min": 100, "max": 130},
                    warning_count=0,
                    late_arrival_count=0,
                    diagnostic_counts={"error": 0, "warning": 0},
                    content_hash="telemetry-hash-v1",
                    temporal_fields=("timestamp",),
                    lineage={
                        "source_uri": "s3://unit-test/telemetry.csv",
                        "snapshot_id": "telemetry:v1",
                        "schema_version": "1.0",
                    },
                    filters={"geography": "blr-core"},
                ),
            ),
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
