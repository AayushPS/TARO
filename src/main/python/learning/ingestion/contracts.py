"""Stage E1 typed contracts for TARO offline source ingestion."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Mapping


def _require_non_blank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must be non-blank")
    return normalized


def _canonical_unit_label(value: str) -> str:
    return _require_non_blank(str(value), "unit").replace("-", "_").replace(" ", "_").upper()


def _freeze_mapping(mapping: Mapping[str, Any]) -> dict[str, Any]:
    frozen: dict[str, Any] = {}
    for key in sorted(mapping):
        normalized_key = _require_non_blank(str(key), "mapping key")
        value = mapping[key]
        if isinstance(value, Mapping):
            frozen[normalized_key] = _freeze_mapping(value)
        elif isinstance(value, (list, tuple)):
            frozen[normalized_key] = tuple(
                _freeze_mapping(item) if isinstance(item, Mapping) else item
                for item in value
            )
        else:
            frozen[normalized_key] = value
    return frozen


class FeedKind(str, Enum):
    """Stage E1 feed families that the canonical ingestion layer accepts."""

    TELEMETRY = "telemetry"
    INCIDENT = "incident"
    TOPOLOGY = "topology"


class TimeUnit(str, Enum):
    """Stage E1 time-unit surface aligned with Java `TimeUtils` semantics."""

    SECONDS = "SECONDS"
    MILLISECONDS = "MILLISECONDS"

    @classmethod
    def parse(cls, raw_value: object) -> "TimeUnit":
        if isinstance(raw_value, cls):
            return raw_value
        token = _canonical_unit_label(str(raw_value))
        if token in {"SECONDS", "SECOND", "SEC", "S"}:
            return cls.SECONDS
        if token in {"MILLISECONDS", "MILLISECOND", "MILLIS", "MILLI", "MSEC", "MS"}:
            return cls.MILLISECONDS
        raise ValueError(f"unsupported time unit: {raw_value}")


@dataclass(frozen=True)
class TemporalFieldRule:
    """Stage E1 temporal-field contract for source normalization."""

    field_name: str
    unit: TimeUnit | None = None
    unit_field: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "field_name", _require_non_blank(self.field_name, "field_name"))
        if self.unit is None and self.unit_field is None:
            raise ValueError("TemporalFieldRule requires either unit or unit_field")
        if self.unit is not None and not isinstance(self.unit, TimeUnit):
            object.__setattr__(self, "unit", TimeUnit.parse(self.unit))
        if self.unit_field is not None:
            object.__setattr__(self, "unit_field", _require_non_blank(self.unit_field, "unit_field"))

    @property
    def normalized_field_name(self) -> str:
        return f"{self.field_name}_ticks"


@dataclass(frozen=True)
class FieldUnitRule:
    """Stage E1 value-field unit expectation used for row validation."""

    value_field: str
    unit_field: str
    expected_unit: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "value_field", _require_non_blank(self.value_field, "value_field"))
        object.__setattr__(self, "unit_field", _require_non_blank(self.unit_field, "unit_field"))
        object.__setattr__(self, "expected_unit", _canonical_unit_label(self.expected_unit))


@dataclass(frozen=True)
class SourceLineage:
    """Stage E1 lineage metadata that later stages must be able to trace."""

    source_uri: str
    snapshot_id: str
    schema_version: str
    extras: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "source_uri", _require_non_blank(self.source_uri, "source_uri"))
        object.__setattr__(self, "snapshot_id", _require_non_blank(self.snapshot_id, "snapshot_id"))
        object.__setattr__(self, "schema_version", _require_non_blank(self.schema_version, "schema_version"))
        object.__setattr__(self, "extras", _freeze_mapping(self.extras))

    def as_dict(self) -> dict[str, Any]:
        data = {
            "schema_version": self.schema_version,
            "snapshot_id": self.snapshot_id,
            "source_uri": self.source_uri,
        }
        data.update(self.extras)
        return data


@dataclass(frozen=True)
class FilterMetadata:
    """Stage E1 explicit filtering metadata recorded in the manifest."""

    values: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "values", _freeze_mapping(self.values))

    def as_dict(self) -> dict[str, Any]:
        return dict(self.values)


@dataclass(frozen=True)
class SourceSchema:
    """Stage E1 schema and validation contract for one feed."""

    feed_kind: FeedKind
    id_namespace: str
    id_field: str
    required_fields: tuple[str, ...]
    temporal_fields: tuple[TemporalFieldRule, ...]
    duplicate_key_fields: tuple[str, ...]
    unit_rules: tuple[FieldUnitRule, ...] = ()
    ordering_field: str | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.feed_kind, FeedKind):
            object.__setattr__(self, "feed_kind", FeedKind(self.feed_kind))
        object.__setattr__(self, "id_namespace", _require_non_blank(self.id_namespace, "id_namespace"))
        object.__setattr__(self, "id_field", _require_non_blank(self.id_field, "id_field"))
        required_fields = tuple(_require_non_blank(field_name, "required_field") for field_name in self.required_fields)
        temporal_fields = tuple(self.temporal_fields)
        duplicate_key_fields = tuple(
            _require_non_blank(field_name, "duplicate_key_field") for field_name in self.duplicate_key_fields
        )
        unit_rules = tuple(self.unit_rules)
        if not required_fields:
            raise ValueError("required_fields must be non-empty")
        if not temporal_fields:
            raise ValueError("temporal_fields must be non-empty")
        if not duplicate_key_fields:
            raise ValueError("duplicate_key_fields must be non-empty")
        if self.ordering_field is not None:
            ordering_field = _require_non_blank(self.ordering_field, "ordering_field")
            if ordering_field not in {rule.field_name for rule in temporal_fields}:
                raise ValueError("ordering_field must match one of the temporal field names")
            object.__setattr__(self, "ordering_field", ordering_field)
        object.__setattr__(self, "required_fields", required_fields)
        object.__setattr__(self, "temporal_fields", temporal_fields)
        object.__setattr__(self, "duplicate_key_fields", duplicate_key_fields)
        object.__setattr__(self, "unit_rules", unit_rules)

    def required_columns(self) -> tuple[str, ...]:
        columns = set(self.required_fields)
        columns.add(self.id_field)
        columns.update(self.duplicate_key_fields)
        for rule in self.temporal_fields:
            columns.add(rule.field_name)
            if rule.unit_field is not None:
                columns.add(rule.unit_field)
        for unit_rule in self.unit_rules:
            columns.add(unit_rule.value_field)
            columns.add(unit_rule.unit_field)
        return tuple(sorted(columns))


@dataclass(frozen=True)
class SourceFeed:
    """Stage E1 source-feed configuration for one CSV input."""

    source_name: str
    path: Path
    schema: SourceSchema
    lineage: SourceLineage
    filters: FilterMetadata = field(default_factory=FilterMetadata)

    def __post_init__(self) -> None:
        object.__setattr__(self, "source_name", _require_non_blank(self.source_name, "source_name"))
        object.__setattr__(self, "path", Path(self.path))


@dataclass(frozen=True)
class SourceValidationDiagnostic:
    """Stage E1 row-level or source-level validation diagnostic."""

    severity: str
    code: str
    message: str
    source_name: str
    row_number: int | None = None
    field_name: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "severity", _canonical_unit_label(self.severity))
        object.__setattr__(self, "code", _canonical_unit_label(self.code))
        object.__setattr__(self, "message", _require_non_blank(self.message, "message"))
        object.__setattr__(self, "source_name", _require_non_blank(self.source_name, "source_name"))
        if self.field_name is not None:
            object.__setattr__(self, "field_name", _require_non_blank(self.field_name, "field_name"))

    def as_dict(self) -> dict[str, Any]:
        return {
            "code": self.code,
            "field_name": self.field_name,
            "message": self.message,
            "row_number": self.row_number,
            "severity": self.severity,
            "source_name": self.source_name,
        }


class SourceValidationError(Exception):
    """Stage E1 validation failure carrying auditable diagnostics."""

    def __init__(self, diagnostics: tuple[SourceValidationDiagnostic, ...]):
        if not diagnostics:
            raise ValueError("SourceValidationError requires at least one diagnostic")
        self.diagnostics = diagnostics
        super().__init__(self._build_message())

    def _build_message(self) -> str:
        preview = "; ".join(
            f"{diagnostic.source_name}:{diagnostic.row_number}:{diagnostic.code}"
            for diagnostic in self.diagnostics[:3]
        )
        suffix = "" if len(self.diagnostics) <= 3 else "; ..."
        return f"Stage E1 source validation failed with {len(self.diagnostics)} diagnostic(s): {preview}{suffix}"
