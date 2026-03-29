"""Stage E5 canonical reproducibility-pack export surface."""

from importlib import import_module

_EXPORT_MODULES = {
    "CandidateReportArtifact": "contracts",
    "CandidateReportRow": "contracts",
    "LearningConfigArtifact": "contracts",
    "RefinementDecisionArtifact": "contracts",
    "RefinementDecisionRow": "contracts",
    "ReleaseArtifactDescriptor": "contracts",
    "ReproducibilityPackArtifact": "contracts",
    "ResearchClaimArtifact": "contracts",
    "ResearchClaimRecord": "contracts",
    "ValidationReportArtifact": "contracts",
    "build_candidate_report": "pack",
    "build_learning_config": "pack",
    "build_refinement_decisions": "pack",
    "build_reproducibility_pack": "pack",
    "build_validation_report": "pack",
    "read_candidate_report": "pack",
    "read_learning_config": "pack",
    "read_refinement_decisions": "pack",
    "read_reproducibility_pack": "pack",
    "read_research_claims": "pack",
    "read_validation_report": "pack",
    "write_candidate_report": "pack",
    "write_learning_config": "pack",
    "write_refinement_decisions": "pack",
    "write_reproducibility_pack": "pack",
    "write_research_claims": "pack",
    "write_validation_report": "pack",
}

__all__ = sorted(_EXPORT_MODULES)


def __getattr__(name: str):
    module_name = _EXPORT_MODULES.get(name)
    if module_name is None:
        raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
    module = import_module(f"{__name__}.{module_name}")
    value = getattr(module, name)
    globals()[name] = value
    return value
