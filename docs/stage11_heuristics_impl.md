# Stage 11 Heuristics Implementation Notes

Date: 2026-02-11  
Scope: Runtime Stage 11 (`NONE`, `EUCLIDEAN`, `SPHERICAL`) only

## 1. Strict Contract Summary

- `HeuristicType` is explicitly limited to:
  - `NONE`
  - `EUCLIDEAN`
  - `SPHERICAL`
- No implicit default exists anywhere in Stage 11.
- `HeuristicFactory.create(type, edgeGraph, profileStore, costEngine)` rejects `null`/unspecified `type` with deterministic reason-code errors.
- `HeuristicProvider.bindGoal(int goalNodeId)` returns an immutable `GoalBoundHeuristic`.
- `GoalBoundHeuristic.estimateFromNode(int nodeId)` is allocation-free hot path.
- `LANDMARK` is intentionally not present in Stage 11 enums or runtime paths (deferred to Stage 12).

## 2. Admissibility Calibration Model

`GeometryLowerBoundModel` precomputes one immutable scalar:

- `lowerBoundCostPerDistance`

Calibration uses safe lower bounds consistent with Stage 10 cost semantics:

- Base component: `base_weight`
- Temporal component: minimum day-aware profile multiplier
- Live component: no-speedup lower bound (`live_penalty >= 1.0`)
- Turn component: non-negative lower bound (`turn >= 0.0`)

So each edge contributes:

- `edgeLowerBoundCost = base_weight * min_temporal * 1.0 + 0.0`
- `edgeRatio = edgeLowerBoundCost / edge_geometry_distance`

Final model scale is:

- `min(edgeRatio)` across edges with positive geometry distance.

Construction fails fast when deterministic finite non-negative calibration cannot be produced.

## 3. Factory Validation Matrix

- `NONE`
  - Always valid for graph data shape (coordinates not required).
  - Returns zero estimator.
- `EUCLIDEAN`
  - Requires coordinates.
  - Uses planar distance (`hypot`) times calibrated lower-bound scale.
- `SPHERICAL`
  - Requires coordinates.
  - Enforces geodetic ranges to prevent projected/network coordinate misuse:
    - latitude in `[-90, 90]`
    - longitude in `[-180, 180]`
  - Uses anti-meridian-normalized great-circle distance with clamped haversine domain.

All validation failures throw `HeuristicConfigurationException` with deterministic reason-code text in the message (`[CODE] ...`).

## 4. Observability and Fail-Fast Behavior

Reason-code examples used by Stage 11:

- `H11_TYPE_REQUIRED`
- `H11_COORDINATES_REQUIRED`
- `H11_SPHERICAL_LAT_RANGE`
- `H11_SPHERICAL_LON_RANGE`
- `H11_LB_*` calibration failure codes (`EMPTY_GRAPH`, `NO_POSITIVE_DISTANCE_EDGES`, contract mismatches, invalid numeric inputs)

This keeps mode/data incompatibility failures reproducible and audit-friendly.

## 5. Stage-12 Readiness

- Stage 11 provider/factory contracts are reusable by route-core selection in Stage 12.
- No Stage 11 code path references landmark artifacts.
- Landmark/ALT integration remains a Stage 12 concern by design.
