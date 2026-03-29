"""Stage E2 canonical sequence and feature artifact surface."""

from importlib import import_module

_EXPORT_MODULES = {
    "CorridorBucketFrequencyArtifact": "contracts",
    "CorridorBucketFrequencyRow": "contracts",
    "SequenceBuilderConfig": "contracts",
    "SequenceDatasetArtifact": "contracts",
    "SequenceRow": "contracts",
    "build_corridor_bucket_frequency": "builder",
    "build_sequence_dataset": "builder",
    "read_corridor_bucket_frequency": "builder",
    "read_sequence_dataset": "builder",
    "write_corridor_bucket_frequency": "builder",
    "write_sequence_dataset": "builder",
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
