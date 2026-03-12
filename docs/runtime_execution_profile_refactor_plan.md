# Runtime Execution Profile Refactor Plan

Date: 2026-03-11  
Status: Implemented baseline

## 1. Summary

- Move routing algorithm and heuristic selection from request scope to startup scope.
- Keep request-level `algorithm` / `heuristicType` fields only as deprecated compatibility hints during migration.
- Introduce explicit extension seams for execution binding, heuristic creation, request normalization, and runtime config management.
- Reduce `RouteCore` orchestration density by pushing normalization and startup execution binding into dedicated collaborators.

## 2. Implemented Architecture

### 2.1 Startup-bound execution profile

- Added execution profile model types:
  - `ExecutionProfileSpec`
  - `ExecutionRuntimeConfig`
  - `ExecutionProfileRegistry`
  - `ResolvedExecutionProfileContext`
- Added `ExecutionRuntimeBinder` as an interface seam and `DefaultExecutionRuntimeBinder` as the built-in implementation.
- Binding rules enforced at startup:
  - `DIJKSTRA -> NONE` only
  - `A_STAR -> explicit heuristic`
  - `LANDMARK` requires compatible landmark artifacts
  - no implicit algorithm or heuristic defaults

### 2.2 Request-path behavior

- `RouteRequest` and `MatrixRequest` still carry `algorithm` and `heuristicType`, but only as deprecated compatibility hints.
- `DefaultRequestNormalizer` binds all normalized internal requests to the startup-selected execution profile.
- If deprecated request hints are present and do not match startup configuration, the query fails deterministically with `HEX_REQUEST_EXECUTION_SELECTOR_MISMATCH`.

### 2.3 Extensibility seams

- Added or formalized interface seams:
  - `RoutePlanner`
  - `MatrixPlanner`
  - `HeuristicProviderFactory`
  - `RequestNormalizer`
  - `ExecutionRuntimeBinder`
  - `ExecutionConfigAdminService`
- `RouteCore` now depends directly on `RequestNormalizer` and `HeuristicProviderFactory` interfaces rather than embedding all logic internally.

### 2.4 Runtime management

- Added `ExecutionProfileAwareRouter`, `RouterRuntimeFactory`, and `RouterRuntimeManager`.
- `RouterRuntimeManager` provides:
  - current execution-profile introspection
  - candidate validation
  - atomic router swap on execution-profile change
- In-flight requests remain pinned to the router instance captured at request entry.

## 3. Migration Contract

- Phase 1 is active:
  - requests may omit `algorithm` and `heuristicType`
  - requests may still send them as compatibility hints
  - runtime behavior always comes from startup-bound execution config
- Phase 2 is deferred:
  - remove deprecated request fields
  - simplify request builders and tests to the startup-only model

## 4. Test Coverage Added

- `DefaultExecutionRuntimeBinderTest`
  - named profile success
  - invalid Dijkstra/heuristic combination rejection
  - missing config rejection
  - LANDMARK artifact validation
- `ExecutionProfileRouteCoreTest`
  - route/matrix without per-request execution selectors
  - selector mismatch rejection
  - constructor rejection when execution config is missing
- `RouterRuntimeManagerTest`
  - validate/apply profile swap
  - in-flight request stability during swap

## 5. Follow-up Refactor Work

- Migrate remaining tests and helper builders from per-request execution selection to startup-bound execution configs.
- Push more response-mapping and planner-selection logic out of `RouteCore` if class complexity rises again.
- Add broader package dependency guardrails if the execution subsystem expands further.
