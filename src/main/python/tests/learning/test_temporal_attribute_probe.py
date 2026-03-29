import math
import unittest

from src.main.python.learning.calibration import build_calibration_bundle
from src.main.python.learning.datasets import CorridorBucketFrequencyArtifact, CorridorBucketFrequencyRow, SequenceDatasetArtifact, SequenceRow
from src.main.python.learning.export import build_reproducibility_pack
from src.main.python.learning.forecasting import (
    ForecastSurfaceArtifact,
    ForecastSurfaceRow,
    ForecastTrainingBundle,
    ForecastTrainingConfig,
    TemporalAttributeProbeCase,
    TemporalRepresentationArtifact,
    TemporalRepresentationRow,
    run_temporal_attribute_probe,
    train_forecast_bundle,
)
from src.main.python.learning.ingestion import DatasetManifest, DatasetSourceManifest


class TemporalAttributeProbeTest(unittest.TestCase):

    def test_ingestion_stage_probe_preserves_manifest_lineage_and_non_flat_temporal_bounds(self):
        manifest = self._dataset_manifest()
        source = manifest.sources[0]

        self.assertEqual("SECONDS", manifest.engine_time_unit)
        self.assertEqual(("timestamp",), source.temporal_fields)
        self.assertEqual("telemetry:v1", source.lineage["snapshot_id"])
        self.assertEqual("s3://unit-test/telemetry.csv", source.lineage["source_uri"])
        self.assertLess(source.normalized_tick_range["min_tick"], source.normalized_tick_range["max_tick"])

    def test_run_temporal_attribute_probe_reports_strength_and_limits(self):
        bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=5,
                history_window_buckets=20,
                recency_half_life_buckets=4,
            ),
        )

        report = run_temporal_attribute_probe(
            bundle,
            (
                TemporalAttributeProbeCase("periodicity", "EDGE", "EDGE_PERIODIC", 3, 8, 29.0),
                TemporalAttributeProbeCase("recency", "EDGE", "EDGE_RECENT", 3, 8, 34.0),
                TemporalAttributeProbeCase("direction", "EDGE", "EDGE_NORTH", 3, 9, 19.0, note="northbound"),
                TemporalAttributeProbeCase("direction", "EDGE", "EDGE_SOUTH", 3, 9, 43.0, note="southbound"),
                TemporalAttributeProbeCase("density", "CORRIDOR", "C-DENSE", 3, 8, 43.0),
                TemporalAttributeProbeCase("persistence", "CORRIDOR", "C-PERSIST", 3, 10, 50.0),
                TemporalAttributeProbeCase("recency", "CORRIDOR", "C-SPARSE", 3, 10, 26.0, note="low_support"),
            ),
        )

        rows = {
            (row.attribute_name, row.subject_id, row.bucket_index): row
            for row in report.rows
        }

        periodicity_row = rows[("periodicity", "EDGE_PERIODIC", 8)]
        recency_row = rows[("recency", "EDGE_RECENT", 8)]
        north_row = rows[("direction", "EDGE_NORTH", 9)]
        south_row = rows[("direction", "EDGE_SOUTH", 9)]
        density_row = rows[("density", "C-DENSE", 8)]
        persistence_row = rows[("persistence", "C-PERSIST", 10)]
        sparse_row = rows[("recency", "C-SPARSE", 10)]

        self.assertEqual("STRONG", periodicity_row.competence)
        self.assertLess(periodicity_row.feature_rich_absolute_error, periodicity_row.baseline_absolute_error)

        self.assertEqual("STRONG", recency_row.competence)
        self.assertLess(recency_row.feature_rich_absolute_error, recency_row.baseline_absolute_error)

        self.assertIn(north_row.competence, {"PARITY", "STRONG"})
        self.assertIn(south_row.competence, {"PARITY", "STRONG"})
        self.assertGreater(abs(north_row.feature_rich_prediction - south_row.feature_rich_prediction), 20.0)

        self.assertEqual("STRONG", density_row.competence)
        self.assertLess(density_row.feature_rich_absolute_error, density_row.baseline_absolute_error)

        self.assertEqual("STRONG", persistence_row.competence)
        self.assertLess(persistence_row.feature_rich_absolute_error, persistence_row.baseline_absolute_error)

        self.assertEqual("LIMITED", sparse_row.competence)

    def test_representation_forecast_stage_probe_separates_direction_recency_and_density_cases(self):
        bundle = train_forecast_bundle(
            self._sequence_dataset(),
            self._corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=5,
                history_window_buckets=20,
                recency_half_life_buckets=4,
            ),
        )
        representation_by_subject = {
            row.subject_id: row
            for row in bundle.representations.rows
        }

        north_vector, south_vector = self._centered_vectors(
            representation_by_subject["EDGE_NORTH"],
            representation_by_subject["EDGE_SOUTH"],
            corpus=tuple(bundle.representations.rows),
        )
        self.assertLess(self._cosine_similarity(north_vector, south_vector), 0.95)

        recent_bundle = self._recency_calibration_training_bundle()
        recent_representation = {
            (row.subject_id, row.signal_kind): row
            for row in recent_bundle.representations.rows
        }
        recent_vector, stale_vector = self._centered_vectors(
            recent_representation[("C-RECENT-CLUSTER", "INCIDENT_PERSISTS")],
            recent_representation[("C-STALE-BASELINE", "INCIDENT_PERSISTS")],
            corpus=tuple(recent_bundle.representations.rows),
        )
        self.assertGreater(
            self._euclidean_distance(recent_vector, stale_vector),
            0.50,
        )

        dense_vector, sparse_vector = self._centered_vectors(
            representation_by_subject["C-DENSE"],
            representation_by_subject["C-SPARSE"],
            corpus=tuple(bundle.representations.rows),
        )
        self.assertGreater(self._vector_norm(dense_vector), 0.10)
        self.assertGreater(self._vector_norm(sparse_vector), 0.10)

    def test_recency_clusters_raise_e4_prior_mass_for_incident_and_worsening_surfaces(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-RECENT-CLUSTER", 3, 8, 6, 10, 0.55),
                CorridorBucketFrequencyRow("C-STALE-BASELINE", 3, 8, 6, 10, 0.55),
            ),
        )
        training_bundle = self._recency_calibration_training_bundle()

        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)
        rows = {
            (row.corridor_id, row.signal_kind, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }

        recent_incident = rows[("C-RECENT-CLUSTER", "INCIDENT_PERSISTS", 3, 8)]
        stale_incident = rows[("C-STALE-BASELINE", "INCIDENT_PERSISTS", 3, 8)]
        recent_worsening = rows[("C-RECENT-CLUSTER", "CONGESTION_WORSENING", 3, 8)]
        stale_worsening = rows[("C-STALE-BASELINE", "CONGESTION_WORSENING", 3, 8)]

        self.assertTrue(recent_incident.confidence_qualified)
        self.assertFalse(stale_incident.confidence_qualified)
        self.assertTrue(recent_worsening.confidence_qualified)
        self.assertFalse(stale_worsening.confidence_qualified)
        self.assertGreaterEqual(
            recent_incident.calibrated_prior_probability - stale_incident.calibrated_prior_probability,
            0.10,
        )
        self.assertGreaterEqual(
            recent_worsening.calibrated_prior_probability - stale_worsening.calibrated_prior_probability,
            0.10,
        )
        self.assertGreater(recent_incident.evidence_response, stale_incident.evidence_response)
        self.assertGreater(recent_worsening.evidence_response, stale_worsening.evidence_response)

    def test_calibration_preferential_stage_probe_keeps_density_nonzero_and_priors_bounded(self):
        recent_calibration_bundle = build_calibration_bundle(
            self._recency_calibration_training_bundle(),
            CorridorBucketFrequencyArtifact(
                manifest_version="E2.v1",
                engine_time_unit="SECONDS",
                bucket_size_seconds=100,
                timezone_id="UTC",
                rows=(
                    CorridorBucketFrequencyRow("C-RECENT-CLUSTER", 3, 8, 6, 10, 0.55),
                    CorridorBucketFrequencyRow("C-STALE-BASELINE", 3, 8, 6, 10, 0.55),
                ),
            ),
        )
        qualified_recent_rows = [
            row
            for row in recent_calibration_bundle.scenario_prior_calibrations
            if row.confidence_qualified
        ]
        self.assertTrue(qualified_recent_rows)
        self.assertTrue(all(row.density_signal > 0.0 for row in qualified_recent_rows))
        self.assertTrue(
            all(
                row.calibrated_prior_probability <= 0.99
                for row in qualified_recent_rows
                if row.signal_kind == "INCIDENT_PERSISTS"
            )
        )

        preferential_bundle = build_calibration_bundle(
            self._preferential_training_bundle(),
            CorridorBucketFrequencyArtifact(
                manifest_version="E2.v1",
                engine_time_unit="SECONDS",
                bucket_size_seconds=100,
                timezone_id="UTC",
                rows=(
                    CorridorBucketFrequencyRow("C-ARTERIAL", 3, 8, 6, 10, 0.60),
                    CorridorBucketFrequencyRow("C-LOCAL", 3, 8, 6, 10, 0.60),
                ),
            ),
        )
        preferential_rows = {
            row.corridor_id: row
            for row in preferential_bundle.scenario_prior_calibrations
        }
        arterial = preferential_rows["C-ARTERIAL"]
        local = preferential_rows["C-LOCAL"]

        self.assertGreater(arterial.preferential_attachment_adjustment, 0.0)
        self.assertGreater(arterial.preferential_attachment_adjustment, local.preferential_attachment_adjustment)
        self.assertGreater(
            arterial.calibrated_prior_probability - arterial.historical_bucket_frequency,
            local.calibrated_prior_probability - local.historical_bucket_frequency,
        )

    def test_persistent_corridor_remains_distinct_from_same_mean_volatile_corridor_through_e4(self):
        training_bundle = train_forecast_bundle(
            self._persistence_sequence_dataset(),
            self._persistence_corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=17,
                history_window_buckets=20,
                recency_half_life_buckets=4,
            ),
        )

        representations = {
            row.subject_id: row
            for row in training_bundle.representations.rows
        }
        persistent_representation = representations["C-PERSIST-CONSTANT"]
        volatile_representation = representations["C-PERSIST-VARIANCE"]

        self.assertAlmostEqual(
            persistent_representation.mean_signal,
            volatile_representation.mean_signal,
            delta=0.01,
        )
        self.assertGreater(
            persistent_representation.persistence_signal,
            volatile_representation.persistence_signal + 0.40,
        )
        self.assertGreater(
            persistent_representation.recent_signal,
            volatile_representation.recent_signal,
        )

        forecast_rows = {
            (row.subject_id, row.bucket_index): row
            for row in training_bundle.forecast_surface.rows
            if row.sequence_scope == "CORRIDOR"
        }
        for bucket_index in (8, 9, 10):
            persistent_forecast = forecast_rows[("C-PERSIST-CONSTANT", bucket_index)]
            volatile_forecast = forecast_rows[("C-PERSIST-VARIANCE", bucket_index)]
            self.assertGreater(
                persistent_forecast.feature_rich_prediction,
                persistent_forecast.baseline_prediction,
            )
            self.assertLessEqual(
                volatile_forecast.feature_rich_prediction,
                volatile_forecast.baseline_prediction + 0.5,
            )

        calibration_bundle = build_calibration_bundle(
            training_bundle,
            self._persistence_corridor_frequency_artifact(),
        )
        priors = {
            (row.corridor_id, row.signal_kind, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }

        for bucket_index in (8, 9, 10):
            persistent_prior = priors[("C-PERSIST-CONSTANT", "TRAVEL_TIME", 3, bucket_index)]
            volatile_prior = priors[("C-PERSIST-VARIANCE", "TRAVEL_TIME", 3, bucket_index)]

            self.assertTrue(persistent_prior.confidence_qualified)
            self.assertFalse(volatile_prior.confidence_qualified)
            self.assertGreaterEqual(persistent_prior.calibrated_prior_probability, 0.80)
            self.assertGreater(
                persistent_prior.calibrated_prior_probability,
                volatile_prior.calibrated_prior_probability + 0.20,
            )
            self.assertGreater(
                persistent_prior.evidence_response,
                volatile_prior.evidence_response,
            )

    def test_periodic_corridor_keeps_distinct_bucket_surfaces_and_non_flat_priors(self):
        training_bundle = train_forecast_bundle(
            self._periodicity_sequence_dataset(),
            self._periodicity_corridor_frequency_artifact(),
            ForecastTrainingConfig(
                training_seed=19,
                history_window_buckets=20,
                recency_half_life_buckets=4,
            ),
        )

        representations = {
            row.subject_id: row
            for row in training_bundle.representations.rows
        }
        periodic_representation = representations["C-PERIODIC-RUSH"]
        flat_representation = representations["C-FLAT-MEAN"]

        self.assertGreater(
            periodic_representation.periodicity_signal,
            flat_representation.periodicity_signal + 0.40,
        )

        forecast_rows = {
            (row.subject_id, row.bucket_index): row
            for row in training_bundle.forecast_surface.rows
            if row.sequence_scope == "CORRIDOR"
        }
        periodic_rush = forecast_rows[("C-PERIODIC-RUSH", 7)]
        periodic_offpeak = forecast_rows[("C-PERIODIC-RUSH", 11)]
        flat_rush = forecast_rows[("C-FLAT-MEAN", 7)]
        flat_offpeak = forecast_rows[("C-FLAT-MEAN", 11)]

        self.assertGreater(
            periodic_rush.feature_rich_prediction - periodic_offpeak.feature_rich_prediction,
            25.0,
        )
        self.assertLess(
            abs(flat_rush.feature_rich_prediction - flat_offpeak.feature_rich_prediction),
            3.0,
        )

        calibration_bundle = build_calibration_bundle(
            training_bundle,
            self._periodicity_corridor_frequency_artifact(),
        )
        priors = {
            (row.corridor_id, row.signal_kind, row.day_of_week, row.bucket_index): row
            for row in calibration_bundle.scenario_prior_calibrations
        }
        periodic_rush_prior = priors[("C-PERIODIC-RUSH", "TRAVEL_TIME", 3, 7)]
        periodic_offpeak_prior = priors[("C-PERIODIC-RUSH", "TRAVEL_TIME", 3, 11)]
        flat_rush_prior = priors[("C-FLAT-MEAN", "TRAVEL_TIME", 3, 7)]
        flat_offpeak_prior = priors[("C-FLAT-MEAN", "TRAVEL_TIME", 3, 11)]

        self.assertTrue(periodic_rush_prior.confidence_qualified)
        self.assertTrue(periodic_offpeak_prior.confidence_qualified)
        self.assertGreater(
            periodic_rush_prior.calibrated_prior_probability - periodic_offpeak_prior.calibrated_prior_probability,
            0.20,
        )
        self.assertLess(
            abs(flat_rush_prior.calibrated_prior_probability - flat_offpeak_prior.calibrated_prior_probability),
            0.05,
        )

    def test_reproducibility_lineage_stage_probe_preserves_pack_identity_and_hash_lineage(self):
        dataset_manifest, training_bundle, calibration_bundle = self._reproducibility_pack_inputs()
        first_pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)
        second_pack = build_reproducibility_pack(dataset_manifest, training_bundle, calibration_bundle)

        self.assertEqual(
            first_pack.release_artifact.release_artifact_id,
            second_pack.release_artifact.release_artifact_id,
        )
        self.assertEqual(
            first_pack.release_artifact.dataset_manifest_hash,
            first_pack.learning_config.dataset_manifest_hash,
        )
        self.assertEqual(
            first_pack.release_artifact.training_bundle_hash,
            first_pack.learning_config.training_bundle_hash,
        )
        self.assertEqual(
            first_pack.release_artifact.calibration_bundle_hash,
            first_pack.learning_config.calibration_bundle_hash,
        )
        self.assertEqual(
            first_pack.release_artifact.learning_config_hash,
            first_pack.candidate_report.learning_config_hash,
        )

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
                    normalized_tick_range={"min_tick": 100, "max_tick": 130},
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
                self._row("EDGE", "EDGE_RECENT", 3, 8, 900, 10.0, 1, 3, 1),
                self._row("EDGE", "EDGE_RECENT", 3, 8, 1_100, 12.0, 2, 3, 1),
                self._row("EDGE", "EDGE_RECENT", 3, 8, 1_600, 48.0, 3, 3, 1),
                self._row("EDGE", "EDGE_NORTH", 3, 9, 1_200, 18.0, 1, 2, 1),
                self._row("EDGE", "EDGE_NORTH", 3, 9, 1_500, 20.0, 2, 2, 1),
                self._row("EDGE", "EDGE_SOUTH", 3, 9, 1_200, 42.0, 1, 2, 1),
                self._row("EDGE", "EDGE_SOUTH", 3, 9, 1_500, 44.0, 2, 2, 1),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_000, 40.0, 1, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_300, 42.0, 2, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 8, 1_600, 44.0, 3, 3, 6),
                self._row("CORRIDOR", "C-DENSE", 3, 9, 1_450, 18.0, 1, 1, 2),
                self._row("CORRIDOR", "C-PERSIST", 3, 10, 1_100, 46.0, 2, 3, 5),
                self._row("CORRIDOR", "C-PERSIST", 3, 10, 1_400, 48.0, 3, 3, 5),
                self._row("CORRIDOR", "C-PERSIST", 3, 10, 1_600, 50.0, 4, 3, 5),
                self._row("CORRIDOR", "C-PERSIST", 3, 12, 1_200, 24.0, 1, 1, 1),
                self._row("CORRIDOR", "C-SPARSE", 3, 10, 1_550, 26.0, 1, 1, 1),
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
                CorridorBucketFrequencyRow(
                    corridor_id="C-PERSIST",
                    day_of_week=3,
                    bucket_index=10,
                    observation_count=3,
                    total_corridor_observations=4,
                    historical_bucket_frequency=0.75,
                ),
                CorridorBucketFrequencyRow(
                    corridor_id="C-PERSIST",
                    day_of_week=3,
                    bucket_index=12,
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

    def _persistence_sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            source_names=("corridor",),
            rows=(
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 12, 700, 20.0, 1, 1, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 8, 900, 55.0, 4, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 8, 1_000, 55.0, 5, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 9, 1_100, 55.0, 6, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 9, 1_200, 55.0, 7, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 10, 1_300, 55.0, 8, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-CONSTANT", 3, 10, 1_400, 55.0, 8, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 12, 700, 50.0, 1, 1, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 8, 900, 80.0, 1, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 8, 1_000, 20.0, 1, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 9, 1_100, 80.0, 1, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 9, 1_200, 20.0, 1, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 10, 1_300, 80.0, 1, 3, 4),
                self._row("CORRIDOR", "C-PERSIST-VARIANCE", 3, 10, 1_400, 20.0, 1, 3, 4),
            ),
        )

    def _persistence_corridor_frequency_artifact(self) -> CorridorBucketFrequencyArtifact:
        return CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-PERSIST-CONSTANT", 3, 8, 6, 8, 0.82),
                CorridorBucketFrequencyRow("C-PERSIST-CONSTANT", 3, 9, 6, 8, 0.81),
                CorridorBucketFrequencyRow("C-PERSIST-CONSTANT", 3, 10, 6, 8, 0.80),
                CorridorBucketFrequencyRow("C-PERSIST-CONSTANT", 3, 12, 1, 8, 0.12),
                CorridorBucketFrequencyRow("C-PERSIST-VARIANCE", 3, 8, 2, 8, 0.32),
                CorridorBucketFrequencyRow("C-PERSIST-VARIANCE", 3, 9, 2, 8, 0.31),
                CorridorBucketFrequencyRow("C-PERSIST-VARIANCE", 3, 10, 2, 8, 0.30),
                CorridorBucketFrequencyRow("C-PERSIST-VARIANCE", 3, 12, 1, 8, 0.12),
            ),
        )

    def _periodicity_sequence_dataset(self) -> SequenceDatasetArtifact:
        return SequenceDatasetArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            source_names=("corridor",),
            rows=(
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 7, 900, 60.0, 1, 4, 4),
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 7, 1_000, 62.0, 1, 4, 4),
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 7, 1_100, 58.0, 1, 4, 4),
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 11, 1_200, 20.0, 1, 4, 4),
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 11, 1_300, 22.0, 1, 4, 4),
                self._row("CORRIDOR", "C-PERIODIC-RUSH", 3, 11, 1_400, 18.0, 1, 4, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 7, 900, 39.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 7, 1_000, 41.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 7, 1_100, 40.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 11, 1_200, 39.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 11, 1_300, 41.0, 1, 1, 4),
                self._row("CORRIDOR", "C-FLAT-MEAN", 3, 11, 1_400, 40.0, 1, 1, 4),
            ),
        )

    def _periodicity_corridor_frequency_artifact(self) -> CorridorBucketFrequencyArtifact:
        return CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-PERIODIC-RUSH", 3, 7, 6, 10, 0.65),
                CorridorBucketFrequencyRow("C-PERIODIC-RUSH", 3, 11, 3, 10, 0.25),
                CorridorBucketFrequencyRow("C-FLAT-MEAN", 3, 7, 4, 10, 0.45),
                CorridorBucketFrequencyRow("C-FLAT-MEAN", 3, 11, 4, 10, 0.45),
            ),
        )

    def _recency_calibration_training_bundle(self) -> ForecastTrainingBundle:
        config = ForecastTrainingConfig(
            training_seed=13,
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
                rows=(
                    TemporalRepresentationRow(
                        "CORRIDOR",
                        "C-RECENT-CLUSTER",
                        "INCIDENT_PERSISTS",
                        8,
                        1_000,
                        1.0,
                        1.6,
                        0.8,
                        0.5,
                        0.5,
                        0.90,
                    ),
                    TemporalRepresentationRow(
                        "CORRIDOR",
                        "C-STALE-BASELINE",
                        "INCIDENT_PERSISTS",
                        8,
                        1_000,
                        1.0,
                        1.1,
                        0.4,
                        0.5,
                        0.5,
                        0.90,
                    ),
                    TemporalRepresentationRow(
                        "CORRIDOR",
                        "C-RECENT-CLUSTER",
                        "CONGESTION_WORSENING",
                        8,
                        1_000,
                        1.0,
                        1.7,
                        0.8,
                        0.5,
                        0.5,
                        0.92,
                    ),
                    TemporalRepresentationRow(
                        "CORRIDOR",
                        "C-STALE-BASELINE",
                        "CONGESTION_WORSENING",
                        8,
                        1_000,
                        1.0,
                        1.1,
                        0.4,
                        0.5,
                        0.5,
                        0.92,
                    ),
                ),
            ),
            forecast_surface=ForecastSurfaceArtifact(
                manifest_version="E3.v1",
                sequence_manifest_version="E2.v1",
                training_seed=config.training_seed,
                enabled_features=config.enabled_features,
                rows=(
                    ForecastSurfaceRow("CORRIDOR", "C-RECENT-CLUSTER", "INCIDENT_PERSISTS", 3, 8, 8, 0.55, 20.0, 25.0, 0.90),
                    ForecastSurfaceRow("CORRIDOR", "C-STALE-BASELINE", "INCIDENT_PERSISTS", 3, 8, 8, 0.55, 20.0, 20.6, 0.90),
                    ForecastSurfaceRow("CORRIDOR", "C-RECENT-CLUSTER", "CONGESTION_WORSENING", 3, 8, 8, 0.55, 18.0, 23.5, 0.92),
                    ForecastSurfaceRow("CORRIDOR", "C-STALE-BASELINE", "CONGESTION_WORSENING", 3, 8, 8, 0.55, 18.0, 18.6, 0.92),
                ),
            ),
        )

    def _preferential_training_bundle(self) -> ForecastTrainingBundle:
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
                rows=(
                    TemporalRepresentationRow("CORRIDOR", "C-ARTERIAL", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.3, 0.6, 0.5, 0.85, 0.85),
                    TemporalRepresentationRow("CORRIDOR", "C-LOCAL", "EVENT_INTENSITY", 8, 1_000, 1.0, 1.3, 0.6, 0.5, 0.20, 0.85),
                ),
            ),
            forecast_surface=ForecastSurfaceArtifact(
                manifest_version="E3.v1",
                sequence_manifest_version="E2.v1",
                training_seed=config.training_seed,
                enabled_features=config.enabled_features,
                rows=(
                    ForecastSurfaceRow("CORRIDOR", "C-ARTERIAL", "EVENT_INTENSITY", 3, 8, 8, 0.60, 30.0, 39.0, 0.85),
                    ForecastSurfaceRow("CORRIDOR", "C-LOCAL", "EVENT_INTENSITY", 3, 8, 8, 0.60, 30.0, 39.0, 0.85),
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

    def _reproducibility_pack_inputs(self):
        corridor_bucket_frequency = CorridorBucketFrequencyArtifact(
            manifest_version="E2.v1",
            engine_time_unit="SECONDS",
            bucket_size_seconds=100,
            timezone_id="UTC",
            rows=(
                CorridorBucketFrequencyRow("C-RECENT-CLUSTER", 3, 8, 6, 10, 0.55),
                CorridorBucketFrequencyRow("C-STALE-BASELINE", 3, 8, 6, 10, 0.55),
            ),
        )
        training_bundle = self._recency_calibration_training_bundle()
        calibration_bundle = build_calibration_bundle(training_bundle, corridor_bucket_frequency)
        return self._dataset_manifest(), training_bundle, calibration_bundle

    def _centered_vectors(
        self,
        left: TemporalRepresentationRow,
        right: TemporalRepresentationRow,
        corpus: tuple[TemporalRepresentationRow, ...],
    ) -> tuple[tuple[float, ...], tuple[float, ...]]:
        fields = (
            "mean_signal",
            "recent_signal",
            "persistence_signal",
            "periodicity_signal",
            "density_signal",
            "confidence",
        )
        centers = {
            field_name: sum(getattr(row, field_name) for row in corpus) / len(corpus)
            for field_name in fields
        }
        return (
            tuple(getattr(left, field_name) - centers[field_name] for field_name in fields),
            tuple(getattr(right, field_name) - centers[field_name] for field_name in fields),
        )

    def _cosine_similarity(self, left: tuple[float, ...], right: tuple[float, ...]) -> float:
        return sum(x * y for x, y in zip(left, right)) / (self._vector_norm(left) * self._vector_norm(right))

    def _euclidean_distance(self, left: tuple[float, ...], right: tuple[float, ...]) -> float:
        return math.sqrt(sum((x - y) ** 2 for x, y in zip(left, right)))

    def _vector_norm(self, values: tuple[float, ...]) -> float:
        return math.sqrt(sum(value * value for value in values))


if __name__ == "__main__":
    unittest.main()
