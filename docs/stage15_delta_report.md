# Stage 15 Delta Report

Date: 2026-02-20  
Stage: 15 (Addressing Trait)  
Status: Implemented  
Owner: TARO runtime team  
Commit Range: working-tree (not yet committed)

## 1. Scope Summary

- Addressing trait axis is implemented for typed request normalization.
- Legacy external-id request fields remain backward-compatible.
- Stage 13/14 planners remain unchanged and still consume internal node ids only.
- Stage 18 full trait-bundle registry/config is intentionally out of scope.

## 2. Requirement Coverage (`R15-*`)

| Requirement ID | Description | Implementation Evidence | Test Evidence | Status |
|---|---|---|---|---|
| R15-01 | Typed `external_id` + `coordinates` support | `src/main/java/org/Aayush/routing/traits/addressing/AddressInput.java` | `Stage15AddressingTraitTest#testTypedExternalIdRouteSuccess` | Pass |
| R15-02 | Typed input resolved to internal anchors + reverse mapping | `AddressingTraitEngine`, `RouteCore` response wiring | `testTypedExternalIdRouteSuccess`, `testMatrixTypedCoordinateDedupAndTelemetry` | Pass |
| R15-03 | O(1)-style ID path + sublinear spatial path | ID mapping via `IDMapper`; coordinate path via `SpatialRuntime` | `testTypedXyRouteThresholdPassAndFail`, full suite | Pass |
| R15-04 | Deterministic snapping + threshold policy | `AddressingPolicy`, `CoordinateStrategyRegistry`, `AddressingTraitEngine` | XY/LAT_LON threshold pass/fail tests | Pass |
| R15-05 | Trait-driven plugin-style extensibility | `AddressingTrait`, `AddressingTraitCatalog`, `CoordinateDistanceStrategy` | unsupported trait/type validation tests | Pass |
| R15-06 | Valid/invalid ids, bounds, mixed-mode tests | Stage15 test suite + existing RouteCore tests | `Stage15AddressingTraitTest` + `RouteCoreTest` | Pass |
| R15-07 | Pre-query trait compatibility validation | trait + strategy validation in `AddressingTraitEngine` | unknown trait/strategy + unsupported type tests | Pass |
| R15-08 | Stage 15/18 boundary respected | no global trait-bundle registry added | architecture review + code diff | Pass |
| R15-09 | Canonical typed address shape compatibility | `AddressInput.ofExternalId/ofXY/ofLatLon` | typed route/matrix tests | Pass |
| R15-10 | Mixed mode explicit-enable only | `allowMixedAddressing` enforcement | `testMixedModeAndTypedLegacyAmbiguityContracts` | Pass |
| R15-11 | Planner seam internal-ID-only | normalization outputs internal ids before planner calls | existing Stage13/14 tests + stress suite | Pass |
| R15-12 | Fixed-snapshot determinism preserved | deterministic tie-break and replay behavior retained | `RouteCoreTest` + `RouteCoreStressPerfTest` | Pass |

## 3. Contract Delta

### 3.1 Request Model Changes

- `RouteRequest` added:
  - `sourceAddress`, `targetAddress`
  - `addressingTraitId`
  - `coordinateDistanceStrategyId`
  - `allowMixedAddressing`
  - `maxSnapDistance`
- `MatrixRequest` added typed lists and the same trait/strategy/policy fields.
- Legacy external-id fields remain supported.

### 3.2 Response Model Changes

- `RouteResponse` added:
  - `sourceResolvedAddress`
  - `targetResolvedAddress`
- `MatrixResponse` shape remains stable; source/target arrays now carry resolved-anchor ids for typed coordinate inputs.

### 3.3 Reason Codes

Added `H15_*` codes in `RouteCore`:

- `H15_ADDRESSING_TRAIT_REQUIRED`
- `H15_UNKNOWN_ADDRESSING_TRAIT`
- `H15_UNSUPPORTED_ADDRESS_TYPE`
- `H15_MALFORMED_TYPED_PAYLOAD`
- `H15_NON_FINITE_COORDINATES`
- `H15_LAT_LON_RANGE`
- `H15_COORDINATE_STRATEGY_REQUIRED`
- `H15_UNKNOWN_COORDINATE_STRATEGY`
- `H15_UNKNOWN_TYPED_EXTERNAL_NODE`
- `H15_COORDINATE_STRATEGY_FAILURE`
- `H15_SPATIAL_RUNTIME_UNAVAILABLE`
- `H15_SNAP_THRESHOLD_EXCEEDED`
- `H15_MIXED_MODE_DISABLED`
- `H15_TYPED_LEGACY_AMBIGUITY`
- `H15_INVALID_MAX_SNAP_DISTANCE`

Legacy `H12_*` paths remain active for legacy-only invalid request flows.

## 4. Planner Seam and Parity Evidence

- Stage 13/14 planner classes are unchanged.
- RouteCore now normalizes through `AddressingTraitEngine` before planner invocation.
- Full test suite (`mvn test`) passed after Stage 15 integration.

## 5. Functional and Validation Test Evidence

- New: `src/test/java/org/Aayush/routing/core/Stage15AddressingTraitTest.java`.
- Covers typed external-id route success, XY/LAT_LON snapping pass/fail,
  typed external-id matrix success, mixed-mode policy, typed+legacy ambiguity,
  unknown trait/strategy, typed external unknown-id reason mapping,
  invalid max-snap-distance mapping, custom strategy failure wrapping,
  spatial-runtime unavailable, and coordinate validation errors.

## 6. Performance and Stress Evidence

- Existing Stage 13/14 stress/perf suites pass unchanged (`RouteCoreStressPerfTest`).
- Matrix typed-address dedup telemetry is validated in
  `testMatrixTypedCoordinateDedupAndTelemetry`.

## 7. Determinism Replay Evidence

- Deterministic repeated route/matrix behavior remains validated by existing RouteCore and stress suites.
- Stage 15 coordinate snapping reuses deterministic nearest-node tie-break behavior from `SpatialRuntime`.

## 8. Risks and Residuals

- Stage 15 does not implement Stage 18 global bundle registry/configuration.
- Coordinate request success requires providing a spatial runtime in `RouteCore` when coordinate addressing is used.

## 9. Stage 15 Closure Checklist

- [x] All `R15-*` requirements mapped to passing tests.
- [x] Mixed-mode explicit-enable policy enforced.
- [x] Stage 13/14 parity remains zero mismatch.
- [x] Deterministic replay confirmed on pinned snapshots.
- [x] `docs/taro_v11_ssot_stagewise_breakdown.md` Stage 15 status updated.
- [x] `README.md` typed addressing examples added/updated.
