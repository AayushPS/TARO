"""Stage E3 canonical forecast and representation learning surface."""

from importlib import import_module

_EXPORT_MODULES = {
    "ForecastSurfaceArtifact": "contracts",
    "ForecastSurfaceRow": "contracts",
    "ForecastTrainingBundle": "contracts",
    "ForecastTrainingConfig": "contracts",
    "TemporalAttributeProbeCase": "contracts",
    "TemporalAttributeProbeReport": "contracts",
    "TemporalAttributeProbeRow": "contracts",
    "TemporalRepresentationArtifact": "contracts",
    "TemporalRepresentationRow": "contracts",
    "run_temporal_attribute_probe": "trainer",
    "train_forecast_bundle": "trainer",
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
