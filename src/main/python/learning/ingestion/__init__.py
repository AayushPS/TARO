"""Stage E1 canonical ingestion surface for TARO offline builder work."""

from importlib import import_module

_EXPORT_MODULES = {
    "DatasetBundle": "manifest",
    "DatasetManifest": "manifest",
    "DatasetSourceManifest": "manifest",
    "FeedKind": "contracts",
    "FieldUnitRule": "contracts",
    "FilterMetadata": "contracts",
    "SourceFeed": "contracts",
    "SourceLineage": "contracts",
    "SourceSchema": "contracts",
    "SourceValidationDiagnostic": "contracts",
    "SourceValidationError": "contracts",
    "TemporalFieldRule": "contracts",
    "TimeUnit": "contracts",
    "ingest_sources": "pipeline",
    "write_dataset_manifest": "pipeline",
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
