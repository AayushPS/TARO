import csv
import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from src.main.python.learning.ingestion import (
    DatasetManifest,
    FeedKind,
    FieldUnitRule,
    FilterMetadata,
    SourceFeed,
    SourceLineage,
    SourceSchema,
    TemporalFieldRule,
    TimeUnit,
    ingest_sources,
    write_dataset_manifest,
)


class DatasetManifestContractTest(unittest.TestCase):

    def test_manifest_round_trip_is_deterministic(self):
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
                            "record_id": "telemetry-2",
                            "edge_id": "E-101",
                            "timestamp": "130",
                            "timestamp_unit": "seconds",
                            "travel_time": "55",
                            "travel_time_unit": "seconds",
                        },
                    ),
                )
            )

            bundle = ingest_sources((feed,), TimeUnit.SECONDS)
            manifest_json = bundle.manifest.to_json()

            self.assertEqual(manifest_json, bundle.manifest.to_json())
            self.assertEqual(bundle.manifest, DatasetManifest.from_json(manifest_json))

            manifest_payload = json.loads(manifest_json)
            self.assertEqual("E1.v1", manifest_payload["manifest_version"])
            self.assertEqual("SECONDS", manifest_payload["engine_time_unit"])
            self.assertEqual(2, manifest_payload["total_row_count"])
            self.assertEqual(2, manifest_payload["sources"][0]["row_count"])
            self.assertEqual("telemetry", manifest_payload["sources"][0]["feed_kind"])

    def test_manifest_writer_emits_canonical_dataset_manifest_json(self):
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
                    ),
                )
            )

            bundle = ingest_sources((feed,), TimeUnit.SECONDS)
            manifest_path = write_dataset_manifest(bundle.manifest, temp_dir / "dataset_manifest.json")
            payload = json.loads(manifest_path.read_text(encoding="utf-8"))

            self.assertEqual("dataset_manifest.json", manifest_path.name)
            self.assertEqual("telemetry:v1", payload["sources"][0]["lineage"]["snapshot_id"])
            self.assertEqual("s3://unit-test/telemetry.csv", payload["sources"][0]["lineage"]["source_uri"])
            self.assertEqual({"geography": "blr-core"}, payload["sources"][0]["filters"])

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
                temporal_fields=(
                    TemporalFieldRule("timestamp", unit_field="timestamp_unit"),
                ),
                duplicate_key_fields=("record_id",),
                unit_rules=(
                    FieldUnitRule("travel_time", "travel_time_unit", "seconds"),
                ),
                ordering_field="timestamp",
            ),
            lineage=SourceLineage(
                source_uri="s3://unit-test/telemetry.csv",
                snapshot_id="telemetry:v1",
                schema_version="1.0",
            ),
            filters=FilterMetadata({"geography": "blr-core"}),
        )

    def _write_csv(self, path: Path, rows: tuple[dict[str, str], ...]) -> Path:
        fieldnames = [
            "record_id",
            "edge_id",
            "timestamp",
            "timestamp_unit",
            "travel_time",
            "travel_time_unit",
        ]
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames)
            writer.writeheader()
            for row in rows:
                writer.writerow(row)
        return path


if __name__ == "__main__":
    unittest.main()
