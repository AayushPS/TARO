import json
import unittest
from dataclasses import replace
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.calibration import CalibrationSelectionConfig, build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow
from src.main.python.learning.export import ReproducibilityPackArtifact, build_reproducibility_pack, read_reproducibility_pack, write_reproducibility_pack
from src.main.python.learning.forecasting import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
)
from src.main.python.learning.ingestion import DatasetManifest, DatasetSourceManifest


class LineageArtifactContractTest(unittest.TestCase):

    def test_pack_rejects_missing_lineage_fields(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()
        pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)

        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            write_reproducibility_pack(pack, temp_dir)
            manifest_path = temp_dir / "reproducibility_pack.json"
            payload = json.loads(manifest_path.read_text(encoding="utf-8"))
            payload["release_artifact"]["dataset_manifest_hash"] = "   "
            manifest_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "dataset_manifest_hash must be non-blank"):
                read_reproducibility_pack(temp_dir)

    def test_pack_artifacts_share_stable_release_lineage(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()
        pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)
        release_artifact = pack.release_artifact

        self.assertEqual(release_artifact.release_artifact_id, pack.candidate_report.release_artifact_id)
        self.assertEqual(release_artifact.release_artifact_id, pack.refinement_decisions.release_artifact_id)
        self.assertEqual(release_artifact.release_artifact_id, pack.validation_report.release_artifact_id)
        self.assertEqual(release_artifact.dataset_manifest_hash, pack.learning_config.dataset_manifest_hash)
        self.assertEqual(release_artifact.dataset_manifest_hash, pack.candidate_report.dataset_manifest_hash)
        self.assertEqual(release_artifact.dataset_manifest_hash, pack.refinement_decisions.dataset_manifest_hash)
        self.assertEqual(release_artifact.dataset_manifest_hash, pack.validation_report.dataset_manifest_hash)
        self.assertEqual(release_artifact.training_bundle_hash, pack.learning_config.training_bundle_hash)
        self.assertEqual(release_artifact.training_bundle_hash, pack.candidate_report.training_bundle_hash)
        self.assertEqual(release_artifact.calibration_bundle_hash, pack.learning_config.calibration_bundle_hash)
        self.assertEqual(release_artifact.calibration_bundle_hash, pack.candidate_report.calibration_bundle_hash)
        self.assertEqual(release_artifact.calibration_bundle_hash, pack.refinement_decisions.calibration_bundle_hash)
        self.assertEqual(release_artifact.learning_config_hash, pack.candidate_report.learning_config_hash)
        self.assertEqual(release_artifact.learning_config_hash, pack.refinement_decisions.learning_config_hash)
        self.assertEqual(release_artifact.learning_config_hash, pack.validation_report.learning_config_hash)
        self.assertEqual(release_artifact.candidate_report_hash, pack.validation_report.candidate_report_hash)
        self.assertEqual(release_artifact.refinement_decisions_hash, pack.validation_report.refinement_decisions_hash)

        with self.assertRaisesRegex(ValueError, "candidate_report.release_artifact_id must match"):
            ReproducibilityPackArtifact(
                manifest_version=pack.manifest_version,
                package_scope=pack.package_scope,
                release_artifact=pack.release_artifact,
                dataset_manifest=pack.dataset_manifest,
                learning_config=pack.learning_config,
                candidate_report=replace(pack.candidate_report, release_artifact_id="release-mismatch"),
                refinement_decisions=pack.refinement_decisions,
                validation_report=pack.validation_report,
                research_claims=pack.research_claims,
            )

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
