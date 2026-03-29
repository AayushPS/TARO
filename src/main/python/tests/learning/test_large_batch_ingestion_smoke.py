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
    TemporalFieldRule,
    TimeUnit,
    ingest_sources,
)


class LargeBatchIngestionSmokeTest(unittest.TestCase):

    def test_large_batch_ingestion_remains_deterministic(self):
        with TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            feed = self._telemetry_feed(self._write_large_csv(temp_dir / "telemetry.csv", row_count=2000))

            first_bundle = ingest_sources((feed,), TimeUnit.SECONDS)
            second_bundle = ingest_sources((feed,), TimeUnit.SECONDS)

            self.assertEqual(first_bundle.manifest, second_bundle.manifest)
            self.assertEqual(2000, first_bundle.manifest.total_row_count)
            self.assertEqual(2000, first_bundle.manifest.sources[0].row_count)
            self.assertEqual(0, first_bundle.manifest.total_warning_count)
            self.assertEqual(2000, len(first_bundle.normalized_rows["telemetry_source"]))

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

    def _write_large_csv(self, path: Path, row_count: int) -> Path:
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
            for row_index in range(row_count):
                writer.writerow(
                    {
                        "record_id": f"telemetry-{row_index}",
                        "edge_id": f"E-{row_index % 250}",
                        "timestamp": str(1000 + row_index),
                        "timestamp_unit": "seconds",
                        "travel_time": str(30 + (row_index % 20)),
                        "travel_time_unit": "seconds",
                    }
                )
        return path


if __name__ == "__main__":
    unittest.main()
