"""Stage E4 canonical calibration and selection surface."""

from importlib import import_module

_EXPORT_MODULES = {
    "CalibrationSelectionBundle": "contracts",
    "CalibrationSelectionConfig": "contracts",
    "RefinedProfileSelectionRow": "contracts",
    "ScenarioPriorCalibrationRow": "contracts",
    "build_calibration_bundle": "selector",
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
