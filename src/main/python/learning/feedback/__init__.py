"""Stage F2 canonical telemetry-feedback artifact surface."""

from importlib import import_module

_FEEDBACK_MODULES = {
    "TelemetryEventArtifact": "contracts",
    "TelemetryEventRow": "contracts",
    "read_telemetry_event_artifact": "artifact",
    "write_telemetry_event_artifact": "artifact",
}

__all__ = sorted(_FEEDBACK_MODULES)


def __getattr__(name: str):
    module_name = _FEEDBACK_MODULES.get(name)
    if module_name is None:
        raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
    module = import_module(f"{__name__}.{module_name}")
    value = getattr(module, name)
    globals()[name] = value
    return value
