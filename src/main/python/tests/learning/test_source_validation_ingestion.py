import csv
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.ingestion import (
    FeedKind,
    FieldUnitRule,
    FilterMetadata,
    SourceFeed,
    SourceLineage,
    SourceSchema,
    SourceValidationError,
    TemporalFieldRule,
    TimeUnit,
    ingest_sources,
)


class SourceValidationIngestionTest(unittest.TestCase):

    def test_ingest_sources_normalizes_three_feed_types(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            telemetry_feed = self._telemetry_feed(
                self._write_csv(
                    temp_dir / "telemetry.csv",
                    (
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "E-100",
                            "timestamp": "100",
                            "timestamp_unit": "seconds",
                            "travel_time": "42",
                            "travel_time_unit": "seconds",
                        },
                    ),
                )
            )
            incident_feed = self._incident_feed(
                self._write_csv(
                    temp_dir / "incident.csv",
                    (
                        {
                            "record_id": "incident-1",
                            "corridor_id": "C-7",
                            "start_timestamp": "110",
                            "end_timestamp": "145",
                            "timestamp_unit": "seconds",
                            "incident_code": "lane_closure",
                        },
                    ),
                )
            )
            topology_feed = self._topology_feed(
                self._write_csv(
                    temp_dir / "topology.csv",
                    (
                        {
                            "record_id": "topology-1",
                            "subject_id": "EDGE-9",
                            "observed_at": "150",
                            "timestamp_unit": "seconds",
                            "change_type": "profile_update",
                        },
                    ),
                )
            )

            bundle = ingest_sources((topology_feed, incident_feed, telemetry_feed), TimeUnit.SECONDS)

            self.assertEqual(3, bundle.manifest.total_row_count)
            self.assertEqual(0, bundle.manifest.total_warning_count)
            self.assertEqual({"corridor": 1, "edge": 1, "topology_subject": 1}, bundle.manifest.id_namespace_cardinality)

            telemetry_row = bundle.normalized_rows["telemetry_source"][0]
            incident_row = bundle.normalized_rows["incident_source"][0]
            topology_row = bundle.normalized_rows["topology_source"][0]

            self.assertEqual(100, telemetry_row["timestamp_ticks"])
            self.assertEqual(110, incident_row["start_timestamp_ticks"])
            self.assertEqual(145, incident_row["end_timestamp_ticks"])
            self.assertEqual(150, topology_row["observed_at_ticks"])
            self.assertEqual(0, telemetry_row["internal_subject_id"])

    def test_missing_required_field_is_rejected(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            feed = self._telemetry_feed(
                self._write_csv(
                    temp_dir / "telemetry.csv",
                    (
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "",
                            "timestamp": "100",
                            "timestamp_unit": "seconds",
                            "travel_time": "42",
                            "travel_time_unit": "seconds",
                        },
                    ),
                )
            )

            with self.assertRaises(SourceValidationError) as context:
                ingest_sources((feed,), TimeUnit.SECONDS)

            codes = {diagnostic.code for diagnostic in context.exception.diagnostics}
            self.assertIn("MISSING_FIELD", codes)

    def test_unit_mismatch_is_rejected(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            feed = self._telemetry_feed(
                self._write_csv(
                    temp_dir / "telemetry.csv",
                    (
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "E-100",
                            "timestamp": "100",
                            "timestamp_unit": "seconds",
                            "travel_time": "42",
                            "travel_time_unit": "minutes",
                        },
                    ),
                )
            )

            with self.assertRaises(SourceValidationError) as context:
                ingest_sources((feed,), TimeUnit.SECONDS)

            codes = {diagnostic.code for diagnostic in context.exception.diagnostics}
            self.assertIn("UNIT_MISMATCH", codes)

    def test_duplicate_record_id_is_rejected(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            feed = self._telemetry_feed(
                self._write_csv(
                    temp_dir / "telemetry.csv",
                    (
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "E-100",
                            "timestamp": "100",
                            "timestamp_unit": "seconds",
                            "travel_time": "42",
                            "travel_time_unit": "seconds",
                        },
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "E-100",
                            "timestamp": "140",
                            "timestamp_unit": "seconds",
                            "travel_time": "44",
                            "travel_time_unit": "seconds",
                        },
                    ),
                )
            )

            with self.assertRaises(SourceValidationError) as context:
                ingest_sources((feed,), TimeUnit.SECONDS)

            codes = {diagnostic.code for diagnostic in context.exception.diagnostics}
            self.assertIn("DUPLICATE_ROW_ID", codes)

    def test_late_arriving_row_is_reported_as_warning(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            feed = self._telemetry_feed(
                self._write_csv(
                    temp_dir / "telemetry.csv",
                    (
                        {
                            "record_id": "telemetry-1",
                            "edge_id": "E-100",
                            "timestamp": "200",
                            "timestamp_unit": "seconds",
                            "travel_time": "42",
                            "travel_time_unit": "seconds",
                        },
                        {
                            "record_id": "telemetry-2",
                            "edge_id": "E-100",
                            "timestamp": "150",
                            "timestamp_unit": "seconds",
                            "travel_time": "45",
                            "travel_time_unit": "seconds",
                        },
                    ),
                )
            )

            bundle = ingest_sources((feed,), TimeUnit.SECONDS)

            self.assertEqual(1, bundle.manifest.total_warning_count)
            self.assertEqual(1, bundle.manifest.sources[0].late_arrival_count)
            self.assertEqual({"LATE_ARRIVAL"}, {diagnostic.code for diagnostic in bundle.diagnostics})

    def _telemetry_feed(self, csv_path: Path) -> SourceFeed:
        return SourceFeed(
            source_name="telemetry_source",
            path=csv_path,
            schema=SourceSchema(
                feed_kind=FeedKind.TELEMETRY,
                id_namespace="edge",
                id_field="edge_id",
                required_fields=(
                    "record_id",
                    "edge_id",
                    "timestamp",
                    "timestamp_unit",
                    "travel_time",
                    "travel_time_unit",
                ),
                temporal_fields=(TemporalFieldRule("timestamp", unit_field="timestamp_unit"),),
                duplicate_key_fields=("record_id",),
                unit_rules=(FieldUnitRule("travel_time", "travel_time_unit", "seconds"),),
                ordering_field="timestamp",
            ),
            lineage=SourceLineage(
                source_uri="s3://unit-test/telemetry.csv",
                snapshot_id="telemetry:v1",
                schema_version="1.0",
            ),
            filters=FilterMetadata({"geography": "blr-core"}),
        )

    def _incident_feed(self, csv_path: Path) -> SourceFeed:
        return SourceFeed(
            source_name="incident_source",
            path=csv_path,
            schema=SourceSchema(
                feed_kind=FeedKind.INCIDENT,
                id_namespace="corridor",
                id_field="corridor_id",
                required_fields=(
                    "record_id",
                    "corridor_id",
                    "start_timestamp",
                    "end_timestamp",
                    "timestamp_unit",
                    "incident_code",
                ),
                temporal_fields=(
                    TemporalFieldRule("start_timestamp", unit_field="timestamp_unit"),
                    TemporalFieldRule("end_timestamp", unit_field="timestamp_unit"),
                ),
                duplicate_key_fields=("record_id",),
                ordering_field="start_timestamp",
            ),
            lineage=SourceLineage(
                source_uri="s3://unit-test/incident.csv",
                snapshot_id="incident:v1",
                schema_version="1.0",
            ),
            filters=FilterMetadata({"geography": "blr-core"}),
        )

    def _topology_feed(self, csv_path: Path) -> SourceFeed:
        return SourceFeed(
            source_name="topology_source",
            path=csv_path,
            schema=SourceSchema(
                feed_kind=FeedKind.TOPOLOGY,
                id_namespace="topology_subject",
                id_field="subject_id",
                required_fields=(
                    "record_id",
                    "subject_id",
                    "observed_at",
                    "timestamp_unit",
                    "change_type",
                ),
                temporal_fields=(TemporalFieldRule("observed_at", unit_field="timestamp_unit"),),
                duplicate_key_fields=("record_id",),
                ordering_field="observed_at",
            ),
            lineage=SourceLineage(
                source_uri="s3://unit-test/topology.csv",
                snapshot_id="topology:v1",
                schema_version="1.0",
            ),
            filters=FilterMetadata({"geography": "blr-core"}),
        )

    def _write_csv(self, path: Path, rows: tuple[dict[str, str], ...]) -> Path:
        fieldnames = list(rows[0].keys())
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames)
            writer.writeheader()
            for row in rows:
                writer.writerow(row)
        return path


if __name__ == "__main__":
    unittest.main()
