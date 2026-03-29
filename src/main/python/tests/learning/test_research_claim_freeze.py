import json
import unittest
from dataclasses import replace
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.calibration import CalibrationSelectionConfig, build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow
from src.main.python.learning.export import (
    ResearchClaimRecord,
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


class ResearchClaimFreezeTest(unittest.TestCase):

    def test_release_only_pack_omits_research_claims(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()
        pack = build_reproducibility_pack(
            dataset_manifest,
            training_bundle,
            calibration_bundle,
            package_scope="release_only",
        )

        self.assertIsNone(pack.research_claims)
        self.assertEqual("release_only", pack.package_scope)
        self.assertIsNone(pack.release_artifact.research_claims_hash)

        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            write_reproducibility_pack(pack, temp_dir)
            reloaded_pack = read_reproducibility_pack(temp_dir)
            payload = json.loads((temp_dir / "reproducibility_pack.json").read_text(encoding="utf-8"))

        self.assertIsNone(reloaded_pack.research_claims)
        self.assertIsNone(payload["artifact_files"]["research_claims"])

    def test_research_pack_rejects_claims_that_diverge_from_validation_report(self):
        dataset_manifest, training_bundle, calibration_bundle = self._pack_inputs()
        release_only_pack = build_reproducibility_pack(
            dataset_manifest,
            training_bundle,
            calibration_bundle,
            package_scope="release_only",
        )
        mean_candidate_confidence = release_only_pack.validation_report.quality_metrics["mean_candidate_confidence"]
        matching_claim = ResearchClaimRecord(
            claim_id="claim-mean-confidence",
            claim_text="Mean candidate confidence is frozen into the release evidence pack.",
            metric_name="mean_candidate_confidence",
            metric_value=mean_candidate_confidence,
            validation_metric_key="quality.mean_candidate_confidence",
        )

        research_pack = build_reproducibility_pack(
            dataset_manifest,
            training_bundle,
            calibration_bundle,
            research_claims=(matching_claim,),
            package_scope="release_and_research",
        )

        self.assertIsNotNone(research_pack.research_claims)
        self.assertEqual("release_and_research", research_pack.package_scope)
        self.assertEqual(
            matching_claim.metric_value,
            research_pack.research_claims.claims[0].metric_value,
        )

        divergent_claim = replace(matching_claim, metric_value=matching_claim.metric_value + 0.10)
        with self.assertRaisesRegex(ValueError, "research claim diverges from validation report"):
            build_reproducibility_pack(
                dataset_manifest,
                training_bundle,
                calibration_bundle,
                research_claims=(divergent_claim,),
                package_scope="release_and_research",
            )

    def test_research_pack_rejects_stale_direction_probe_claim_after_probe_metric_changes(self):
        dataset_manifest, training_bundle, calibration_bundle = self._direction_probe_pack_inputs(edge_gap=24.0)
        baseline_pack = build_reproducibility_pack(
            dataset_manifest,
            training_bundle,
            calibration_bundle,
            package_scope="release_only",
        )
        direction_probe_score = baseline_pack.validation_report.quality_metrics["direction_probe_score"]
        direction_claim = ResearchClaimRecord(
            claim_id="claim-direction-probe-score",
            claim_text="Directional probe score is frozen into the release evidence pack.",
            metric_name="direction_probe_score",
            metric_value=direction_probe_score,
            validation_metric_key="quality.direction_probe_score",
        )

        research_pack = build_reproducibility_pack(
            dataset_manifest,
            training_bundle,
            calibration_bundle,
            research_claims=(direction_claim,),
            package_scope="release_and_research",
        )
        self.assertEqual(
            direction_probe_score,
            research_pack.research_claims.claims[0].metric_value,
        )

        shifted_training_bundle = self._direction_probe_pack_inputs(edge_gap=10.0)[1]
        shifted_calibration_bundle = build_calibration_bundle(
            shifted_training_bundle,
            CorridorBucketFrequencyArtifact(
                manifest_version="E2.v1",
                engine_time_unit="SECONDS",
                bucket_size_seconds=100,
                timezone_id="UTC",
                rows=(
                    CorridorBucketFrequencyRow("C-QUALIFIED", 3, 8, 4, 10, 0.40),
                    CorridorBucketFrequencyRow("C-REJECTED", 3, 8, 4, 10, 0.40),
                ),
            ),
            CalibrationSelectionConfig(
                minimum_confidence=0.60,
                minimum_relative_improvement=0.05,
                maximum_prior_adjustment=0.20,
                preferential_attachment_range=0.10,
            ),
        )
        with self.assertRaisesRegex(ValueError, "research claim diverges from validation report"):
            build_reproducibility_pack(
                dataset_manifest,
                shifted_training_bundle,
                shifted_calibration_bundle,
                research_claims=(direction_claim,),
                package_scope="release_and_research",
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

    def _direction_probe_pack_inputs(
        self,
        edge_gap: float,
    ) -> tuple[DatasetManifest, ForecastTrainingBundle, object]:
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
                    "EDGE",
                    "EDGE_NB",
                    "TRAVEL_TIME",
                    4,
                    1_000,
                    18.0,
                    18.0 + (edge_gap / 2.0),
                    0.3,
                    0.5,
                    0.2,
                    0.82,
                ),
                TemporalRepresentationRow(
                    "EDGE",
                    "EDGE_SB",
                    "TRAVEL_TIME",
                    4,
                    1_000,
                    18.0,
                    18.0 - (edge_gap / 2.0),
                    0.3,
                    0.5,
                    0.2,
                    0.82,
                ),
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
                ForecastSurfaceRow("EDGE", "EDGE_NB", "TRAVEL_TIME", 3, 8, 4, 0.0, 18.0, 18.0 + (edge_gap / 2.0), 0.82),
                ForecastSurfaceRow("EDGE", "EDGE_SB", "TRAVEL_TIME", 3, 8, 4, 0.0, 18.0, 18.0 - (edge_gap / 2.0), 0.82),
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


if __name__ == "__main__":
    unittest.main()
