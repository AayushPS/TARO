"""Stage E5 reproducibility pack builders and serializers."""

from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, is_dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

from ..calibration import CalibrationSelectionBundle
from ..forecasting import ForecastTrainingBundle
from ..ingestion import DatasetManifest, write_dataset_manifest
from .contracts import (
    CandidateReportArtifact,
    CandidateReportRow,
    LearningConfigArtifact,
    RefinementDecisionArtifact,
    RefinementDecisionRow,
    ReleaseArtifactDescriptor,
    ReproducibilityPackArtifact,
    ResearchClaimArtifact,
    ResearchClaimRecord,
    ValidationReportArtifact,
)

_MANIFEST_VERSION = "E5.v1"
_DEFAULT_MODEL_CLASS = "deterministic_forecast_bundle"
_DEFAULT_LEARNING_MODE = "profile_only"
_PACKAGE_SCOPE_RELEASE_ONLY = "release_only"
_PACKAGE_SCOPE_RELEASE_AND_RESEARCH = "release_and_research"
_REPRO_PACK_FILENAME = "reproducibility_pack.json"
_LEARNING_CONFIG_FILENAME = "learning_config.yaml"
_CANDIDATE_REPORT_FILENAME = "candidate_report.parquet"
_DECISION_REPORT_FILENAME = "refinement_decisions.parquet"
_VALIDATION_REPORT_FILENAME = "validation_report.json"
_RESEARCH_CLAIMS_FILENAME = "research_claims.json"
_PROFILE_DECISION_TYPE = "profile_refinement"
_SCENARIO_DECISION_TYPE = "scenario_prior"
_FLOAT_TOLERANCE = 1.0e-9


def _json_ready(value: Any) -> Any:
    if hasattr(value, "to_dict"):
        return _json_ready(value.to_dict())
    if is_dataclass(value):
        return _json_ready(asdict(value))
    if isinstance(value, Mapping):
        return {
            str(key): _json_ready(sub_value)
            for key, sub_value in sorted(value.items(), key=lambda entry: str(entry[0]))
        }
    if isinstance(value, tuple):
        return [_json_ready(item) for item in value]
    if isinstance(value, list):
        return [_json_ready(item) for item in value]
    return value


def _stable_json(value: Any) -> str:
    return json.dumps(
        _json_ready(value),
        sort_keys=True,
        separators=(",", ":"),
    )


def _hash_value(value: Any) -> str:
    return hashlib.sha256(_stable_json(value).encode("utf-8")).hexdigest()


def _hash_dataset_manifest(dataset_manifest: DatasetManifest) -> str:
    return hashlib.sha256(dataset_manifest.to_json().encode("utf-8")).hexdigest()


def _hash_learning_config(learning_config: LearningConfigArtifact) -> str:
    return hashlib.sha256(_learning_config_yaml(learning_config).encode("utf-8")).hexdigest()


def _mean(values: Sequence[float]) -> float:
    return 0.0 if not values else sum(values) / len(values)


def _direction_probe_score(training_bundle: ForecastTrainingBundle) -> float:
    edge_rows = [
        row
        for row in training_bundle.representations.rows
        if row.sequence_scope == "EDGE"
    ]
    if len(edge_rows) < 2:
        return 0.0
    recent_signals = [row.recent_signal for row in edge_rows]
    return max(recent_signals) - min(recent_signals)


def _release_artifact_id(
    package_scope: str,
    model_class: str,
    learning_mode: str,
    dataset_manifest_hash: str,
    training_bundle_hash: str,
    calibration_bundle_hash: str,
    learning_config_hash: str,
) -> str:
    digest = hashlib.sha256(
        "|".join(
            (
                _MANIFEST_VERSION,
                package_scope,
                model_class,
                learning_mode,
                dataset_manifest_hash,
                training_bundle_hash,
                calibration_bundle_hash,
                learning_config_hash,
            )
        ).encode("utf-8")
    ).hexdigest()
    return f"release-{digest[:24]}"


def build_learning_config(
    dataset_manifest_hash: str,
    training_bundle_hash: str,
    calibration_bundle_hash: str,
    training_bundle: ForecastTrainingBundle,
    calibration_bundle: CalibrationSelectionBundle,
    package_scope: str,
    model_class: str = _DEFAULT_MODEL_CLASS,
    learning_mode: str = _DEFAULT_LEARNING_MODE,
) -> LearningConfigArtifact:
    """Stage E5 freeze the canonical learning and calibration config surface."""

    return LearningConfigArtifact(
        manifest_version=_MANIFEST_VERSION,
        model_class=model_class,
        learning_mode=learning_mode,
        package_scope=package_scope,
        training_seed=training_bundle.config.training_seed,
        history_window_buckets=training_bundle.config.history_window_buckets,
        recency_half_life_buckets=training_bundle.config.recency_half_life_buckets,
        minimum_training_observations=training_bundle.config.minimum_training_observations,
        enabled_features=training_bundle.config.enabled_features,
        allowed_sequence_scopes=training_bundle.config.allowed_sequence_scopes,
        minimum_confidence=calibration_bundle.config.minimum_confidence,
        minimum_relative_improvement=calibration_bundle.config.minimum_relative_improvement,
        maximum_prior_adjustment=calibration_bundle.config.maximum_prior_adjustment,
        preferential_attachment_range=calibration_bundle.config.preferential_attachment_range,
        prior_floor=calibration_bundle.config.prior_floor,
        prior_ceiling=calibration_bundle.config.prior_ceiling,
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
    )


def build_candidate_report(
    release_artifact_id: str,
    learning_config_hash: str,
    dataset_manifest_hash: str,
    training_bundle_hash: str,
    calibration_bundle_hash: str,
    calibration_bundle: CalibrationSelectionBundle,
) -> CandidateReportArtifact:
    """Stage E5 publish proposal-level candidate evidence for calibrated profile rows."""

    rows = tuple(
        CandidateReportRow(
            candidate_id=f"{row.sequence_scope}:{row.subject_id}:{row.day_of_week}:{row.bucket_index}",
            sequence_scope=row.sequence_scope,
            subject_id=row.subject_id,
            day_of_week=row.day_of_week,
            bucket_index=row.bucket_index,
            baseline_prediction=row.baseline_prediction,
            feature_rich_prediction=row.feature_rich_prediction,
            calibrated_prediction=row.calibrated_prediction,
            confidence=row.confidence,
            relative_improvement=row.relative_improvement,
            accepted=row.accepted,
            rejection_reason=row.rejection_reason,
        )
        for row in sorted(
            calibration_bundle.refined_profile_selections,
            key=lambda entry: (entry.sequence_scope, entry.subject_id, entry.day_of_week, entry.bucket_index),
        )
    )
    return CandidateReportArtifact(
        manifest_version=_MANIFEST_VERSION,
        release_artifact_id=release_artifact_id,
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        learning_config_hash=learning_config_hash,
        rows=rows,
    )


def build_refinement_decisions(
    release_artifact_id: str,
    learning_config_hash: str,
    dataset_manifest_hash: str,
    calibration_bundle_hash: str,
    calibration_bundle: CalibrationSelectionBundle,
) -> RefinementDecisionArtifact:
    """Stage E5 publish accepted and rejected profile/prior decisions with reasons."""

    profile_rows = [
        RefinementDecisionRow(
            decision_id=f"profile:{row.sequence_scope}:{row.subject_id}:{row.day_of_week}:{row.bucket_index}",
            decision_type=_PROFILE_DECISION_TYPE,
            subject_id=row.subject_id,
            day_of_week=row.day_of_week,
            bucket_index=row.bucket_index,
            accepted=row.accepted,
            rejection_reason=row.rejection_reason,
            baseline_value=row.baseline_prediction,
            calibrated_value=row.calibrated_prediction,
            confidence=row.confidence,
        )
        for row in calibration_bundle.refined_profile_selections
    ]
    scenario_rows = [
        RefinementDecisionRow(
            decision_id=f"scenario_prior:{row.corridor_id}:{row.signal_kind}:{row.day_of_week}:{row.bucket_index}",
            decision_type=_SCENARIO_DECISION_TYPE,
            subject_id=row.corridor_id,
            day_of_week=row.day_of_week,
            bucket_index=row.bucket_index,
            accepted=row.confidence_qualified,
            rejection_reason=row.rejection_reason,
            baseline_value=row.historical_bucket_frequency,
            calibrated_value=row.calibrated_prior_probability,
            confidence=row.confidence,
            evidence_response=row.evidence_response,
            preferential_attachment_adjustment=row.preferential_attachment_adjustment,
        )
        for row in calibration_bundle.scenario_prior_calibrations
    ]
    rows = tuple(
        sorted(
            profile_rows + scenario_rows,
            key=lambda entry: (entry.decision_type, entry.subject_id, entry.day_of_week, entry.bucket_index, entry.decision_id),
        )
    )
    return RefinementDecisionArtifact(
        manifest_version=_MANIFEST_VERSION,
        release_artifact_id=release_artifact_id,
        dataset_manifest_hash=dataset_manifest_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        learning_config_hash=learning_config_hash,
        rows=rows,
    )


def build_validation_report(
    package_scope: str,
    release_artifact_id: str,
    dataset_manifest_hash: str,
    learning_config_hash: str,
    candidate_report_hash: str,
    refinement_decisions_hash: str,
    training_bundle: ForecastTrainingBundle,
    candidate_report: CandidateReportArtifact,
    refinement_decisions: RefinementDecisionArtifact,
) -> ValidationReportArtifact:
    """Stage E5 emit quality, correctness, and system metrics over the frozen evidence."""

    accepted_candidates = [row for row in candidate_report.rows if row.accepted]
    rejected_candidates = [row for row in candidate_report.rows if not row.accepted]
    scenario_rows = [
        row
        for row in refinement_decisions.rows
        if row.decision_type == _SCENARIO_DECISION_TYPE
    ]
    quality_metrics = {
        "mean_candidate_confidence": _mean([row.confidence for row in candidate_report.rows]),
        "mean_accepted_relative_improvement": _mean([row.relative_improvement for row in accepted_candidates]),
        "mean_rejected_relative_improvement": _mean([row.relative_improvement for row in rejected_candidates]),
        "mean_scenario_prior_delta": _mean(
            [row.calibrated_value - row.baseline_value for row in scenario_rows]
        ),
        "direction_probe_score": _direction_probe_score(training_bundle),
    }
    accepted_rejected_separation = all(
        (row.accepted and row.rejection_reason is None)
        or ((not row.accepted) and row.rejection_reason is not None)
        for row in candidate_report.rows
    ) and all(
        (row.accepted and row.rejection_reason is None)
        or ((not row.accepted) and row.rejection_reason is not None)
        for row in refinement_decisions.rows
    )
    correctness_gates = {
        "deterministic_export": True,
        "accepted_rejected_separation": accepted_rejected_separation,
        "lineage_complete": all(
            (
                bool(release_artifact_id),
                bool(dataset_manifest_hash),
                bool(learning_config_hash),
                bool(candidate_report_hash),
                bool(refinement_decisions_hash),
            )
        ),
    }
    system_metrics = {
        "candidate_count": float(len(candidate_report.rows)),
        "accepted_candidate_count": float(len(accepted_candidates)),
        "rejected_candidate_count": float(len(rejected_candidates)),
        "decision_count": float(len(refinement_decisions.rows)),
        "artifact_count": float(6 if package_scope == _PACKAGE_SCOPE_RELEASE_AND_RESEARCH else 5),
    }
    failures = tuple(
        name
        for name, value in correctness_gates.items()
        if not value
    )
    return ValidationReportArtifact(
        manifest_version=_MANIFEST_VERSION,
        package_scope=package_scope,
        release_artifact_id=release_artifact_id,
        dataset_manifest_hash=dataset_manifest_hash,
        learning_config_hash=learning_config_hash,
        candidate_report_hash=candidate_report_hash,
        refinement_decisions_hash=refinement_decisions_hash,
        quality_metrics=quality_metrics,
        correctness_gates=correctness_gates,
        system_metrics=system_metrics,
        failures=failures,
    )


def _lookup_validation_metric(validation_report: ValidationReportArtifact, metric_key: str) -> float:
    prefix, separator, metric_name = metric_key.partition(".")
    if separator == "":
        raise ValueError(f"validation metric key must use <section>.<name>: {metric_key}")
    if prefix == "quality":
        return float(validation_report.quality_metrics[metric_name])
    if prefix == "system":
        return float(validation_report.system_metrics[metric_name])
    if prefix == "correctness":
        return 1.0 if validation_report.correctness_gates[metric_name] else 0.0
    raise ValueError(f"unsupported validation metric section: {prefix}")


def _validate_research_claims(
    research_claims: Sequence[ResearchClaimRecord],
    validation_report: ValidationReportArtifact,
) -> None:
    for claim in research_claims:
        observed_value = _lookup_validation_metric(validation_report, claim.validation_metric_key)
        if abs(observed_value - claim.metric_value) > _FLOAT_TOLERANCE:
            raise ValueError(
                "research claim diverges from validation report: "
                f"{claim.claim_id} ({claim.validation_metric_key}={observed_value}, claim={claim.metric_value})"
            )


def build_reproducibility_pack(
    dataset_manifest: DatasetManifest,
    training_bundle: ForecastTrainingBundle,
    calibration_bundle: CalibrationSelectionBundle,
    research_claims: Sequence[ResearchClaimRecord] = (),
    package_scope: str = _PACKAGE_SCOPE_RELEASE_ONLY,
    model_class: str = _DEFAULT_MODEL_CLASS,
    learning_mode: str = _DEFAULT_LEARNING_MODE,
) -> ReproducibilityPackArtifact:
    """Stage E5 freeze reproducible release and optional research evidence from E1-E4 artifacts."""

    normalized_scope = package_scope.strip().lower()
    dataset_manifest_hash = _hash_dataset_manifest(dataset_manifest)
    training_bundle_hash = _hash_value(training_bundle)
    calibration_bundle_hash = _hash_value(calibration_bundle)
    learning_config = build_learning_config(
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        training_bundle=training_bundle,
        calibration_bundle=calibration_bundle,
        package_scope=normalized_scope,
        model_class=model_class,
        learning_mode=learning_mode,
    )
    learning_config_hash = _hash_learning_config(learning_config)
    release_artifact_id = _release_artifact_id(
        package_scope=normalized_scope,
        model_class=model_class,
        learning_mode=learning_mode,
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        learning_config_hash=learning_config_hash,
    )
    candidate_report = build_candidate_report(
        release_artifact_id=release_artifact_id,
        learning_config_hash=learning_config_hash,
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        calibration_bundle=calibration_bundle,
    )
    refinement_decisions = build_refinement_decisions(
        release_artifact_id=release_artifact_id,
        learning_config_hash=learning_config_hash,
        dataset_manifest_hash=dataset_manifest_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        calibration_bundle=calibration_bundle,
    )
    candidate_report_hash = _hash_value(candidate_report)
    refinement_decisions_hash = _hash_value(refinement_decisions)
    validation_report = build_validation_report(
        package_scope=normalized_scope,
        release_artifact_id=release_artifact_id,
        dataset_manifest_hash=dataset_manifest_hash,
        learning_config_hash=learning_config_hash,
        candidate_report_hash=candidate_report_hash,
        refinement_decisions_hash=refinement_decisions_hash,
        training_bundle=training_bundle,
        candidate_report=candidate_report,
        refinement_decisions=refinement_decisions,
    )
    validation_report_hash = _hash_value(validation_report)

    if normalized_scope == _PACKAGE_SCOPE_RELEASE_ONLY and research_claims:
        raise ValueError("release_only packs must not receive research_claims")
    if normalized_scope == _PACKAGE_SCOPE_RELEASE_AND_RESEARCH and not research_claims:
        raise ValueError("release_and_research packs must include research_claims")

    research_claim_artifact = None
    research_claims_hash = None
    if research_claims:
        _validate_research_claims(research_claims, validation_report)
        research_claim_artifact = ResearchClaimArtifact(
            manifest_version=_MANIFEST_VERSION,
            release_artifact_id=release_artifact_id,
            validation_report_hash=validation_report_hash,
            candidate_report_hash=candidate_report_hash,
            refinement_decisions_hash=refinement_decisions_hash,
            claims=tuple(research_claims),
        )
        research_claims_hash = _hash_value(research_claim_artifact)

    release_artifact = ReleaseArtifactDescriptor(
        manifest_version=_MANIFEST_VERSION,
        package_scope=normalized_scope,
        release_artifact_id=release_artifact_id,
        model_class=model_class,
        learning_mode=learning_mode,
        dataset_manifest_hash=dataset_manifest_hash,
        training_bundle_hash=training_bundle_hash,
        calibration_bundle_hash=calibration_bundle_hash,
        learning_config_hash=learning_config_hash,
        candidate_report_hash=candidate_report_hash,
        refinement_decisions_hash=refinement_decisions_hash,
        validation_report_hash=validation_report_hash,
        research_claims_hash=research_claims_hash,
    )
    return ReproducibilityPackArtifact(
        manifest_version=_MANIFEST_VERSION,
        package_scope=normalized_scope,
        release_artifact=release_artifact,
        dataset_manifest=dataset_manifest,
        learning_config=learning_config,
        candidate_report=candidate_report,
        refinement_decisions=refinement_decisions,
        validation_report=validation_report,
        research_claims=research_claim_artifact,
    )


def _learning_config_yaml(learning_config: LearningConfigArtifact) -> str:
    lines = [
        f"manifest_version: {learning_config.manifest_version}",
        f"model_class: {learning_config.model_class}",
        f"learning_mode: {learning_config.learning_mode}",
        f"package_scope: {learning_config.package_scope}",
        f"training_seed: {learning_config.training_seed}",
        f"history_window_buckets: {learning_config.history_window_buckets}",
        f"recency_half_life_buckets: {learning_config.recency_half_life_buckets}",
        f"minimum_training_observations: {learning_config.minimum_training_observations}",
        f"enabled_features: [{', '.join(learning_config.enabled_features)}]",
        f"allowed_sequence_scopes: [{', '.join(learning_config.allowed_sequence_scopes)}]",
        f"minimum_confidence: {learning_config.minimum_confidence}",
        f"minimum_relative_improvement: {learning_config.minimum_relative_improvement}",
        f"maximum_prior_adjustment: {learning_config.maximum_prior_adjustment}",
        f"preferential_attachment_range: {learning_config.preferential_attachment_range}",
        f"prior_floor: {learning_config.prior_floor}",
        f"prior_ceiling: {learning_config.prior_ceiling}",
        f"dataset_manifest_hash: {learning_config.dataset_manifest_hash}",
        f"training_bundle_hash: {learning_config.training_bundle_hash}",
        f"calibration_bundle_hash: {learning_config.calibration_bundle_hash}",
        "",
    ]
    return "\n".join(lines)


def _parse_list(raw: str) -> tuple[str, ...]:
    normalized = raw.strip()
    if normalized == "[]":
        return tuple()
    if not (normalized.startswith("[") and normalized.endswith("]")):
        raise ValueError(f"invalid list value: {raw}")
    inner = normalized[1:-1].strip()
    if not inner:
        return tuple()
    return tuple(part.strip() for part in inner.split(","))


def write_learning_config(learning_config: LearningConfigArtifact, output_path: Path | str) -> Path:
    """Stage E5 write the canonical `learning_config.yaml` artifact."""

    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_learning_config_yaml(learning_config), encoding="utf-8")
    return path


def read_learning_config(input_path: Path | str) -> LearningConfigArtifact:
    """Stage E5 read the canonical `learning_config.yaml` artifact."""

    raw_lines = Path(input_path).read_text(encoding="utf-8").splitlines()
    payload: dict[str, str] = {}
    for raw_line in raw_lines:
        line = raw_line.strip()
        if not line:
            continue
        key, separator, value = line.partition(":")
        if separator == "":
            raise ValueError(f"invalid learning_config line: {raw_line}")
        payload[key.strip()] = value.strip()
    return LearningConfigArtifact(
        manifest_version=payload["manifest_version"],
        model_class=payload["model_class"],
        learning_mode=payload["learning_mode"],
        package_scope=payload["package_scope"],
        training_seed=int(payload["training_seed"]),
        history_window_buckets=int(payload["history_window_buckets"]),
        recency_half_life_buckets=int(payload["recency_half_life_buckets"]),
        minimum_training_observations=int(payload["minimum_training_observations"]),
        enabled_features=_parse_list(payload["enabled_features"]),
        allowed_sequence_scopes=_parse_list(payload["allowed_sequence_scopes"]),
        minimum_confidence=float(payload["minimum_confidence"]),
        minimum_relative_improvement=float(payload["minimum_relative_improvement"]),
        maximum_prior_adjustment=float(payload["maximum_prior_adjustment"]),
        preferential_attachment_range=float(payload["preferential_attachment_range"]),
        prior_floor=float(payload["prior_floor"]),
        prior_ceiling=float(payload["prior_ceiling"]),
        dataset_manifest_hash=payload["dataset_manifest_hash"],
        training_bundle_hash=payload["training_bundle_hash"],
        calibration_bundle_hash=payload["calibration_bundle_hash"],
    )


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


def _candidate_report_schema(pa):
    return pa.schema(
        [
            ("candidate_id", pa.string()),
            ("sequence_scope", pa.string()),
            ("subject_id", pa.string()),
            ("day_of_week", pa.int32()),
            ("bucket_index", pa.int32()),
            ("baseline_prediction", pa.float64()),
            ("feature_rich_prediction", pa.float64()),
            ("calibrated_prediction", pa.float64()),
            ("confidence", pa.float64()),
            ("relative_improvement", pa.float64()),
            ("accepted", pa.bool_()),
            ("rejection_reason", pa.string()),
        ]
    )


def _decision_report_schema(pa):
    return pa.schema(
        [
            ("decision_id", pa.string()),
            ("decision_type", pa.string()),
            ("subject_id", pa.string()),
            ("day_of_week", pa.int32()),
            ("bucket_index", pa.int32()),
            ("accepted", pa.bool_()),
            ("rejection_reason", pa.string()),
            ("baseline_value", pa.float64()),
            ("calibrated_value", pa.float64()),
            ("confidence", pa.float64()),
            ("evidence_response", pa.float64()),
            ("preferential_attachment_adjustment", pa.float64()),
        ]
    )


def write_candidate_report(candidate_report: CandidateReportArtifact, output_path: Path | str) -> Path:
    """Stage E5 write the canonical `candidate_report.parquet` artifact."""

    pa, pq = _parquet_modules()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(
        [row.to_dict() for row in candidate_report.rows],
        schema=_candidate_report_schema(pa),
    )
    table = _attach_metadata(
        table,
        {
            "manifest_version": candidate_report.manifest_version,
            "release_artifact_id": candidate_report.release_artifact_id,
            "dataset_manifest_hash": candidate_report.dataset_manifest_hash,
            "training_bundle_hash": candidate_report.training_bundle_hash,
            "calibration_bundle_hash": candidate_report.calibration_bundle_hash,
            "learning_config_hash": candidate_report.learning_config_hash,
        },
    )
    pq.write_table(table, path)
    return path


def read_candidate_report(input_path: Path | str) -> CandidateReportArtifact:
    """Stage E5 read the canonical `candidate_report.parquet` artifact."""

    _, pq = _parquet_modules()
    table = pq.read_table(input_path)
    metadata = _read_metadata(table)
    rows = tuple(CandidateReportRow.from_dict(row) for row in table.to_pylist())
    return CandidateReportArtifact(
        manifest_version=str(metadata["manifest_version"]),
        release_artifact_id=str(metadata["release_artifact_id"]),
        dataset_manifest_hash=str(metadata["dataset_manifest_hash"]),
        training_bundle_hash=str(metadata["training_bundle_hash"]),
        calibration_bundle_hash=str(metadata["calibration_bundle_hash"]),
        learning_config_hash=str(metadata["learning_config_hash"]),
        rows=rows,
    )


def write_refinement_decisions(refinement_decisions: RefinementDecisionArtifact, output_path: Path | str) -> Path:
    """Stage E5 write the canonical `refinement_decisions.parquet` artifact."""

    pa, pq = _parquet_modules()
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(
        [row.to_dict() for row in refinement_decisions.rows],
        schema=_decision_report_schema(pa),
    )
    table = _attach_metadata(
        table,
        {
            "manifest_version": refinement_decisions.manifest_version,
            "release_artifact_id": refinement_decisions.release_artifact_id,
            "dataset_manifest_hash": refinement_decisions.dataset_manifest_hash,
            "calibration_bundle_hash": refinement_decisions.calibration_bundle_hash,
            "learning_config_hash": refinement_decisions.learning_config_hash,
        },
    )
    pq.write_table(table, path)
    return path


def read_refinement_decisions(input_path: Path | str) -> RefinementDecisionArtifact:
    """Stage E5 read the canonical `refinement_decisions.parquet` artifact."""

    _, pq = _parquet_modules()
    table = pq.read_table(input_path)
    metadata = _read_metadata(table)
    rows = tuple(RefinementDecisionRow.from_dict(row) for row in table.to_pylist())
    return RefinementDecisionArtifact(
        manifest_version=str(metadata["manifest_version"]),
        release_artifact_id=str(metadata["release_artifact_id"]),
        dataset_manifest_hash=str(metadata["dataset_manifest_hash"]),
        calibration_bundle_hash=str(metadata["calibration_bundle_hash"]),
        learning_config_hash=str(metadata["learning_config_hash"]),
        rows=rows,
    )


def write_validation_report(validation_report: ValidationReportArtifact, output_path: Path | str) -> Path:
    """Stage E5 write the canonical `validation_report.json` artifact."""

    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(validation_report.to_dict(), indent=2, sort_keys=True) + "\n"
    path.write_text(payload, encoding="utf-8")
    return path


def read_validation_report(input_path: Path | str) -> ValidationReportArtifact:
    """Stage E5 read the canonical `validation_report.json` artifact."""

    return ValidationReportArtifact.from_dict(json.loads(Path(input_path).read_text(encoding="utf-8")))


def write_research_claims(research_claims: ResearchClaimArtifact, output_path: Path | str) -> Path:
    """Stage E5 write the canonical `research_claims.json` artifact."""

    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(research_claims.to_dict(), indent=2, sort_keys=True) + "\n"
    path.write_text(payload, encoding="utf-8")
    return path


def read_research_claims(input_path: Path | str) -> ResearchClaimArtifact:
    """Stage E5 read the canonical `research_claims.json` artifact."""

    return ResearchClaimArtifact.from_dict(json.loads(Path(input_path).read_text(encoding="utf-8")))


def _pack_manifest_payload(
    pack: ReproducibilityPackArtifact,
    research_claims_filename: str | None,
) -> dict[str, Any]:
    return {
        "manifest_version": pack.manifest_version,
        "package_scope": pack.package_scope,
        "release_artifact": pack.release_artifact.to_dict(),
        "artifact_files": {
            "dataset_manifest": "dataset_manifest.json",
            "learning_config": _LEARNING_CONFIG_FILENAME,
            "candidate_report": _CANDIDATE_REPORT_FILENAME,
            "refinement_decisions": _DECISION_REPORT_FILENAME,
            "validation_report": _VALIDATION_REPORT_FILENAME,
            "research_claims": research_claims_filename,
        },
    }


def write_reproducibility_pack(pack: ReproducibilityPackArtifact, output_dir: Path | str) -> Path:
    """Stage E5 write a reproducibility pack directory with canonical component artifacts."""

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    write_dataset_manifest(pack.dataset_manifest, output_path / "dataset_manifest.json")
    write_learning_config(pack.learning_config, output_path / _LEARNING_CONFIG_FILENAME)
    write_candidate_report(pack.candidate_report, output_path / _CANDIDATE_REPORT_FILENAME)
    write_refinement_decisions(pack.refinement_decisions, output_path / _DECISION_REPORT_FILENAME)
    write_validation_report(pack.validation_report, output_path / _VALIDATION_REPORT_FILENAME)
    research_claims_filename = None
    if pack.research_claims is not None:
        write_research_claims(pack.research_claims, output_path / _RESEARCH_CLAIMS_FILENAME)
        research_claims_filename = _RESEARCH_CLAIMS_FILENAME
    manifest_payload = _pack_manifest_payload(pack, research_claims_filename)
    manifest_path = output_path / _REPRO_PACK_FILENAME
    manifest_path.write_text(
        json.dumps(manifest_payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest_path


def read_reproducibility_pack(input_dir: Path | str) -> ReproducibilityPackArtifact:
    """Stage E5 read a reproducibility pack directory and verify internal lineage consistency."""

    input_path = Path(input_dir)
    payload = json.loads((input_path / _REPRO_PACK_FILENAME).read_text(encoding="utf-8"))
    release_artifact = ReleaseArtifactDescriptor.from_dict(payload["release_artifact"])
    artifact_files = payload["artifact_files"]
    dataset_manifest = DatasetManifest.from_json(
        (input_path / artifact_files["dataset_manifest"]).read_text(encoding="utf-8")
    )
    learning_config = read_learning_config(input_path / artifact_files["learning_config"])
    candidate_report = read_candidate_report(input_path / artifact_files["candidate_report"])
    refinement_decisions = read_refinement_decisions(input_path / artifact_files["refinement_decisions"])
    validation_report = read_validation_report(input_path / artifact_files["validation_report"])
    research_claims = None
    if artifact_files["research_claims"] is not None:
        research_claims = read_research_claims(input_path / artifact_files["research_claims"])
    pack = ReproducibilityPackArtifact(
        manifest_version=str(payload["manifest_version"]),
        package_scope=str(payload["package_scope"]),
        release_artifact=release_artifact,
        dataset_manifest=dataset_manifest,
        learning_config=learning_config,
        candidate_report=candidate_report,
        refinement_decisions=refinement_decisions,
        validation_report=validation_report,
        research_claims=research_claims,
    )

    dataset_manifest_hash = _hash_dataset_manifest(pack.dataset_manifest)
    learning_config_hash = _hash_learning_config(pack.learning_config)
    candidate_report_hash = _hash_value(pack.candidate_report)
    refinement_decisions_hash = _hash_value(pack.refinement_decisions)
    validation_report_hash = _hash_value(pack.validation_report)

    if release_artifact.dataset_manifest_hash != dataset_manifest_hash:
        raise ValueError("dataset_manifest_hash does not match reproducibility pack contents")
    if release_artifact.learning_config_hash != learning_config_hash:
        raise ValueError("learning_config_hash does not match reproducibility pack contents")
    if release_artifact.candidate_report_hash != candidate_report_hash:
        raise ValueError("candidate_report_hash does not match reproducibility pack contents")
    if release_artifact.refinement_decisions_hash != refinement_decisions_hash:
        raise ValueError("refinement_decisions_hash does not match reproducibility pack contents")
    if release_artifact.validation_report_hash != validation_report_hash:
        raise ValueError("validation_report_hash does not match reproducibility pack contents")
    if pack.learning_config.dataset_manifest_hash != dataset_manifest_hash:
        raise ValueError("learning_config.dataset_manifest_hash is inconsistent with the pack manifest")
    if pack.candidate_report.dataset_manifest_hash != dataset_manifest_hash:
        raise ValueError("candidate_report.dataset_manifest_hash is inconsistent with the pack manifest")
    if pack.candidate_report.learning_config_hash != learning_config_hash:
        raise ValueError("candidate_report.learning_config_hash is inconsistent with the pack manifest")
    if pack.refinement_decisions.dataset_manifest_hash != dataset_manifest_hash:
        raise ValueError("refinement_decisions.dataset_manifest_hash is inconsistent with the pack manifest")
    if pack.refinement_decisions.learning_config_hash != learning_config_hash:
        raise ValueError("refinement_decisions.learning_config_hash is inconsistent with the pack manifest")
    if pack.validation_report.dataset_manifest_hash != dataset_manifest_hash:
        raise ValueError("validation_report.dataset_manifest_hash is inconsistent with the pack manifest")
    if pack.validation_report.learning_config_hash != learning_config_hash:
        raise ValueError("validation_report.learning_config_hash is inconsistent with the pack manifest")
    if pack.validation_report.candidate_report_hash != candidate_report_hash:
        raise ValueError("validation_report.candidate_report_hash is inconsistent with the pack manifest")
    if pack.validation_report.refinement_decisions_hash != refinement_decisions_hash:
        raise ValueError("validation_report.refinement_decisions_hash is inconsistent with the pack manifest")
    if pack.research_claims is not None:
        _validate_research_claims(pack.research_claims.claims, pack.validation_report)
        research_claims_hash = _hash_value(pack.research_claims)
        if release_artifact.research_claims_hash != research_claims_hash:
            raise ValueError("research_claims_hash does not match reproducibility pack contents")
        if pack.research_claims.release_artifact_id != release_artifact.release_artifact_id:
            raise ValueError("research_claims.release_artifact_id is inconsistent with the pack manifest")
    return pack
