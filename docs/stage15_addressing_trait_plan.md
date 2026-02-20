# Stage 15 Addressing Trait Plan (Aligned Merge)

Date: 2026-02-20  
Status: Implemented (2026-02-20)  
Scope: Implement Stage 15 Addressing Trait by merging the latest team plan with the existing Stage 15 docs plan and SSOT constraints.

## 1. Stage 15 Scope Boundary

Stage 15 includes:

- addressing-axis interfaces and built-in trait implementations,
- typed request normalization from client address inputs to internal node anchors,
- deterministic validation and reason-code mapping for addressing failures,
- reverse-mapping metadata in responses,
- addressing-only extensibility seams for future strategy additions.

Stage 15 excludes:

- full multi-axis trait bundle registry/configuration (Stage 18),
- temporal trait logic (Stage 16),
- transition trait logic (Stage 17),
- planner algorithm changes (Stage 13/14 remain unchanged).

## 2. Requirement Trace Baseline

| ID | Requirement | Source Anchor |
|---|---|---|
| R15-01 | Support typed addressing with `external_id` and `coordinates`. | SSOT FR-015 |
| R15-02 | Resolve typed request input to internal graph anchors and provide reverse mapping. | Stagewise Stage 15 functional requirements |
| R15-03 | Preserve O(1)-style ID path and sublinear spatial path behavior. | Stagewise Stage 15 non-functional requirements |
| R15-04 | Enforce deterministic snapping and threshold policy. | Stagewise Stage 15 non-functional requirements |
| R15-05 | Keep trait-driven plugin extensibility. | Stagewise Stage 15 non-functional requirements |
| R15-06 | Cover valid/invalid ID, coordinate bounds, and mixed-mode tests. | Stagewise Stage 15 equivalence classes |
| R15-07 | Validate valid trait combinations and reject incompatible pre-query combinations. | SSOT trait validation matrix |
| R15-08 | Keep Stage 15 as addressing-only strategy layer; do not absorb Stage 18 global bundle registry. | SSOT stage interface model |
| R15-09 | Keep canonical typed address shape compatibility. | Architecture/API typed input references |
| R15-10 | Mixed mode is allowed only when explicitly enabled. | Typed input decision contract |
| R15-11 | Planner seam invariant: planners receive normalized internal IDs only. | Stage 14 future-readiness seam |
| R15-12 | Preserve deterministic output under fixed snapshot. | SSOT determinism contract |

## 3. Locked Design Decisions

1. Coordinate system choice is per-request, not a global default.
2. Stage 15 coordinate system strategies include built-in `XY` and `LAT_LON`.
3. `LAT_LON` snapping uses KD nearest candidate from `SpatialRuntime`, then spherical threshold validation.
4. Addressing remains extensible via interfaces and registries, not enum-only switch logic.
5. Coordinate payload shape is a generic pair under typed `AddressInput` with explicit factories (`ofExternalId`, `ofXY`, `ofLatLon`).
6. `maxSnapDistance` is one field with strategy-relative units:
- `XY` units for XY strategy,
- meters for `LAT_LON` strategy.
7. Mixed address types in one request are disabled by default and only allowed when `allowMixedAddressing=true`.
8. Typed + legacy fields on the same endpoint are deterministic contract failure.
9. Legacy external-id request fields remain backward-compatible.
10. Stage 13/14 planner internals are untouched; only normalization and response metadata change.

## 4. Planned Public API and Type Changes

1. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressType.java`.
2. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressInput.java`.
3. Add `src/main/java/org/Aayush/routing/traits/addressing/ResolvedAddress.java`.
4. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressingTrait.java`.
5. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`.
6. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitCatalog.java` (addressing-only; not Stage 18 global bundle registry).
7. Add coordinate strategy seam:
- `CoordinateDistanceStrategy`,
- `CoordinateStrategyRegistry`,
- built-ins `XY` and `LAT_LON`.
8. Add `src/main/java/org/Aayush/routing/traits/addressing/AddressingPolicy.java` for threshold bounds and override validation.
9. Extend `src/main/java/org/Aayush/routing/core/RouteRequest.java` with:
- `sourceAddress`, `targetAddress`,
- `addressingTraitId`,
- `coordinateDistanceStrategyId`,
- `allowMixedAddressing`,
- `maxSnapDistance`.
10. Extend `src/main/java/org/Aayush/routing/core/MatrixRequest.java` with typed address lists and the same trait/strategy/policy fields.
11. Extend `src/main/java/org/Aayush/routing/core/RouteResponse.java` with minimal resolved endpoint metadata.
12. Keep `src/main/java/org/Aayush/routing/core/MatrixResponse.java` shape stable; source/target arrays carry resolved-anchor semantics for coordinate inputs.
13. Extend `RouteCore` builder with optional addressing dependencies (trait engine/catalog, coordinate registry, spatial runtime, addressing policy).

## 5. Deterministic Validation and Reason-Code Contract

Introduce deterministic `H15_*` reason-code family for:

- missing/unknown addressing trait id,
- unsupported address type for selected trait,
- malformed typed payload,
- non-finite coordinates,
- invalid lat/lon range under `LAT_LON`,
- missing/unknown coordinate strategy id,
- unknown typed external-id endpoint,
- custom coordinate-strategy runtime failure wrapping,
- spatial runtime unavailable when spatial coordinate resolution is required,
- snap-threshold exceeded,
- mixed mode used without explicit enablement,
- typed + legacy endpoint ambiguity.

Compatibility rule:

- Keep legacy `H12_*` reason codes for legacy-only invalid input paths.

## 6. RouteCore Integration Contract

RouteCore execution path remains:

1. validate algorithm/heuristic constraints (existing behavior),
2. normalize request through `AddressingTraitEngine` into internal node IDs,
3. run existing Stage 13/14 planners unchanged,
4. map resolved addressing metadata into response.

Planner seam invariant:

- planners consume internal IDs only,
- Stage 13/14 behavior and parity expectations must remain unchanged.

## 7. Implementation Units (U0-U10)

1. `U0 - Plan/doc sync`: update Stage 15 plan doc and create delta report skeleton.
2. `U1 - Shared geometry reuse`: expose reusable distance helpers for heuristic + addressing paths.
3. `U2 - Addressing strategy core`: implement addressing interfaces, traits, catalogs, strategy registry, and deterministic `H15_*` codes.
4. `U3 - Request model extension`: add typed and control fields while preserving legacy builder usages.
5. `U4 - Route normalization integration`: route path normalized by addressing trait engine.
6. `U5 - Matrix normalization integration + dedup`: resolve typed addresses once per unique endpoint key and remap duplicates deterministically.
7. `U6 - Response metadata wiring`: route metadata additions and stable matrix contract semantics.
8. `U7 - Addressing telemetry seam`: deterministic counters/latencies for mode counts, resolve calls, dedup reduction, and threshold rejects.
9. `U8 - Functional and contract tests`: typed path, lat/lon path, compatibility, deterministic validation, reason-code mapping.
10. `U9 - Stress/perf/parity tests`: mixed-mode concurrency, ID-path non-regression, coordinate-path p95 gate, dedup reduction gate.
11. `U10 - Closure artifacts`: finalize delta report, update stagewise SSOT status, and add typed examples to `README.md`.

## 8. Test Matrix

Functional:

- typed external-id route and matrix success,
- typed `XY` coordinate route/matrix threshold pass/fail boundaries,
- typed `LAT_LON` coordinate route/matrix threshold pass/fail boundaries,
- explicit mixed-mode enable success,
- legacy compatibility unchanged.

Validation:

- non-finite coordinates rejected deterministically,
- lat/lon range violations rejected deterministically,
- unknown coordinate strategy rejected deterministically,
- unknown addressing trait rejected deterministically,
- spatial runtime unavailable rejection is deterministic,
- typed+legacy ambiguity is deterministic contract failure.

Correctness and parity:

- typed-address resolution matches internal-node oracle,
- Stage 13 A*/Dijkstra parity remains zero mismatch,
- Stage 14 matrix parity remains zero mismatch,
- repeated and concurrent identical requests remain byte-equivalent for fixed snapshot.

Performance and stress:

- ID path regression gate remains O(1)-style behavior,
- coordinate path preserves sublinear spatial lookup behavior,
- matrix dedup reduces coordinate resolve calls on repeated endpoints,
- mixed-mode concurrency stress remains deterministic.

## 9. Hard Exit Gates (Definition of Done)

Stage 15 closes only when:

1. all `R15-*` requirements have explicit passing test trace,
2. mixed-mode explicit-enable policy is enforced (`allowMixedAddressing`),
3. Stage 13/14 parity suites remain zero mismatch,
4. deterministic replay is preserved on pinned snapshots,
5. Stage 15 delta report is published,
6. Stage 15 scope remains bounded and Stage 18 registry responsibilities are not absorbed.
