"""Stage F2 parquet helpers for joined telemetry-feedback artifacts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .contracts import TelemetryEventArtifact, TelemetryEventRow

_MANIFEST_VERSION = "F2.v1"


def _parquet_modules():
    import pyarrow as pa
    import pyarrow.parquet as pq

    return pa, pq


def _attach_metadata(table, metadata: dict[str, Any]):
    encoded = {
        key.encode("utf-8"): json.dumps(value).encode("utf-8")
        for key, value in metadata.items()
    }
    return table.replace_schema_metadata(encoded)


def _read_metadata(table) -> dict[str, Any]:
    metadata = table.schema.metadata or {}
    return {
        key.decode("utf-8"): json.loads(value.decode("utf-8"))
        for key, value in metadata.items()
    }


def _schema(pa):
    return pa.schema(
        [
            ("result_kind", pa.string()),
            ("prediction_id", pa.string()),
            ("result_set_id", pa.string()),
            ("served_at", pa.string()),
            ("feedback_recorded_at", pa.string()),
            ("outcome_status", pa.string()),
            ("complete", pa.bool_()),
            ("caller_hash", pa.string()),
            ("topology_version_id", pa.string()),
            ("model_version", pa.string()),
            ("source_data_lineage_hash", pa.string()),
            ("change_set_hash", pa.string()),
            ("trait_bundle_id", pa.string()),
            ("trait_hash", pa.string()),
            ("execution_profile_id", pa.string()),
            ("quarantine_snapshot_id", pa.string()),
            ("scenario_bundle_id", pa.string()),
            ("scenario_count", pa.int32()),
            ("scenario_ids", pa.list_(pa.string())),
            ("scenario_labels", pa.list_(pa.string())),
            ("scenario_probabilities", pa.list_(pa.float64())),
            ("departure_ticks", pa.int64()),
            ("horizon_ticks", pa.int64()),
            ("preferred_objective", pa.string()),
            ("top_k_alternatives", pa.int32()),
            ("predicted_expected_cost_seconds", pa.float64()),
            ("predicted_robust_cost_seconds", pa.float64()),
            ("matrix_source_count", pa.int32()),
            ("matrix_target_count", pa.int32()),
            ("observed_at_ticks", pa.int64()),
            ("observed_arrival_ticks", pa.int64()),
            ("observed_cost_seconds", pa.float64()),
            ("observation_count", pa.int32()),
            ("partition_date", pa.string()),
        ]
    )


def write_telemetry_event_artifact(
    artifact: TelemetryEventArtifact,
    output_path: Path | str,
) -> Path:
    """Stage F2 write the canonical `telemetry_event.parquet` artifact."""

    pa, pq = _parquet_modules()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(
        artifact.to_pylist(),
        schema=_schema(pa),
    )
    table = _attach_metadata(
        table,
        {
            **artifact.metadata(),
            "manifest_version": artifact.manifest_version or _MANIFEST_VERSION,
        },
    )
    pq.write_table(table, path)
    return path


def read_telemetry_event_artifact(input_path: Path | str) -> TelemetryEventArtifact:
    """Stage F2 read the canonical `telemetry_event.parquet` artifact."""

    _, pq = _parquet_modules()
    table = pq.read_table(input_path)
    metadata = _read_metadata(table)
    rows = tuple(TelemetryEventRow.from_dict(row) for row in table.to_pylist())
    return TelemetryEventArtifact(
        manifest_version=str(metadata["manifest_version"]),
        exported_at=str(metadata["exported_at"]),
        result_kind_filter=None if metadata.get("result_kind_filter") is None else str(metadata["result_kind_filter"]),
        topology_version_filter=None
        if metadata.get("topology_version_filter") is None
        else str(metadata["topology_version_filter"]),
        scenario_bundle_filter=None
        if metadata.get("scenario_bundle_filter") is None
        else str(metadata["scenario_bundle_filter"]),
        trait_hash_filter=None if metadata.get("trait_hash_filter") is None else str(metadata["trait_hash_filter"]),
        complete_only=bool(metadata["complete_only"]),
        rows=rows,
    )
