# TARO v12/v13 Migration Report

Status date: 2026-03-21  
Scope: repo-wide migration tracking for the remaining v12/v13 implementation work after the initial future-route and topology foundation landed

## Step Status

- [x] Step 1: matrix-side v12/v13 migration
- [x] Step 2: builder-side `StructuralChangeSet` to actual rebuild pipeline
- [x] Step 2A: future-routing, reload, and retained-result hardening
- [ ] Step 3: full API/frontend wiring for `resultSetId` retrieval
- [ ] Step 4: richer forecast-driven scenario generation beyond the default bounded resolver

## Step 1: Matrix-Side v12/v13 Migration

Status: Completed on 2026-03-21

### Goal

Bring matrix execution onto the same v12/v13 serving model already introduced for point-to-point routes:

- scenario-aware future evaluation
- retained result sets keyed by `resultSetId`
- topology-version and quarantine binding
- reload compatibility handling for retained results

### Breakdown

1. Open matrix execution to scenario-specific cost engines.
2. Add future-aware matrix evaluation and retained-result contracts.
3. Extend topology reload compatibility to matrix retained results.
4. Verify with focused tests and a full suite run.

### Implementation

#### 1. Matrix runtime seam

Updated the deterministic matrix planner seam so future-aware matrix evaluation can inject a scenario-specific `CostEngine` without replacing the existing planners.

Main changes:

- `RouteCore` now exposes package-private matrix helpers used by future-aware evaluation:
  - normalized matrix request access
  - internal matrix execution with explicit `CostEngine`
- `MatrixPlanner` now supports an explicit-cost-engine execution path while preserving the old two-argument compatibility entrypoint for existing overrides and tests.
- `NativeOneToManyMatrixPlanner` and `TemporaryMatrixPlanner` both honor the explicit `CostEngine` path.

Files:

- `src/main/java/org/Aayush/routing/core/RouteCore.java`
- `src/main/java/org/Aayush/routing/core/MatrixPlanner.java`
- `src/main/java/org/Aayush/routing/core/NativeOneToManyMatrixPlanner.java`
- `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`
- `src/main/java/org/Aayush/routing/core/RouteCoreMatrixSupport.java`

#### 2. Future-aware matrix serving

Added a first-class future-aware matrix stack parallel to the existing future-aware route stack.

New request/result contracts:

- `FutureMatrixRequest`
- `FutureMatrixAggregate`
- `FutureMatrixScenarioResult`
- `FutureMatrixResultSet`

New services/stores:

- `FutureMatrixEvaluator`
- `FutureMatrixService`
- `TopologyAwareFutureMatrixService`
- `EphemeralMatrixResultStore`
- `InMemoryEphemeralMatrixResultStore`

Files:

- `src/main/java/org/Aayush/routing/core/FutureMatrixEvaluator.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixRequest.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixAggregate.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixScenarioResult.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixResultSet.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixService.java`
- `src/main/java/org/Aayush/routing/future/TopologyAwareFutureMatrixService.java`
- `src/main/java/org/Aayush/routing/future/EphemeralMatrixResultStore.java`
- `src/main/java/org/Aayush/routing/future/InMemoryEphemeralMatrixResultStore.java`

#### 3. Shared scenario-bundle contract

Generalized the scenario-bundle request interface so both route and matrix future-aware evaluation can use the same resolver contract.

Files:

- `src/main/java/org/Aayush/routing/future/ScenarioBundleRequest.java`
- `src/main/java/org/Aayush/routing/future/ScenarioBundleResolver.java`
- `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java`
- `src/main/java/org/Aayush/routing/future/FutureRouteRequest.java`

#### 4. v13 retained-result compatibility

Extended retained-result reload handling so route and matrix result stores both participate in topology-compatibility invalidation.

Files:

- `src/main/java/org/Aayush/routing/topology/TopologyBoundResultStore.java`
- `src/main/java/org/Aayush/routing/topology/ReloadCompatibilityPolicy.java`
- `src/main/java/org/Aayush/routing/topology/TopologyReloadCoordinator.java`
- `src/main/java/org/Aayush/routing/future/EphemeralRouteResultStore.java`

#### 5. Shared future-scenario utilities

Extracted shared scenario validation and scenario cost-engine materialization helpers for reuse by future-aware evaluators.

Files:

- `src/main/java/org/Aayush/routing/core/FutureScenarioSupport.java`

### Matrix Semantics Chosen In This Step

This step implements matrix aggregation as a summary-first product:

- each scenario runs a full deterministic matrix against its own scenario-specific cost surface
- each matrix cell is aggregated across scenarios into:
  - reachability probability
  - expected cost
  - P50 cost
  - P90 cost
  - min/max cost
  - earliest/latest arrival

Important note:

- unlike point-to-point future routing, matrix aggregation in this step does **not** retain per-cell path alternatives or fixed-path top-K route families
- the retained per-scenario matrices are preserved so downstream API/frontend work can still inspect scenario-specific outputs by `resultSetId`

This is an intentional first migration slice because the native matrix runtime currently returns cell summaries, not route-shape alternatives.

### Tests Added/Updated

Added:

- `src/test/java/org/Aayush/routing/future/FutureMatrixServiceTest.java`

Updated:

- `src/test/java/org/Aayush/routing/topology/TopologyReloadCoordinatorTest.java`

Regression coverage exercised during implementation:

- `FutureMatrixServiceTest`
- `FutureRouteServiceTest`
- `TopologyReloadCoordinatorTest`
- `NativeOneToManyMatrixPlannerTest`
- `RouteCoreTest`

### Verification

Passed:

- `mvn -q -DskipTests test-compile`
- `mvn -q -Dtest=FutureMatrixServiceTest,FutureRouteServiceTest,TopologyReloadCoordinatorTest,NativeOneToManyMatrixPlannerTest,RouteCoreTest test`
- `mvn -q test`

Note:

- the first full-suite rerun initially failed only on `MaintainabilityGuardrailTest` because `RouteCore.java` crossed the 750-line budget after the new matrix helpers were added
- this was fixed by extracting matrix-only mapping helpers into `RouteCoreMatrixSupport`
- final full-suite status is green

## Step 2: Builder-Side Structural Rebuild and Publication

Status: Completed on 2026-03-21

### Goal

Replace the placeholder v13 topology contracts with a real builder-side publication path that can:

- accept typed `StructuralChangeSet` inputs,
- apply them against canonical source-level topology inputs,
- rebuild a candidate runtime snapshot off the hot path,
- run validation gates before publication,
- atomically reload through `TopologyReloadCoordinator`,
- and keep v12 retained-result behavior aligned with the active topology snapshot.

### Implementation

#### 1. Typed v13 structural-change contracts

Replaced the string-only `StructuralChangeSet` placeholder with typed builders for:

- node additions
- edge additions
- coordinate changes
- turn-relationship upserts/removals
- profile-assignment changes
- rollout policy selection (`VALIDATE_ONLY` vs `ATOMIC_RELOAD`)

Files:

- `src/main/java/org/Aayush/routing/topology/StructuralChangeSet.java`

#### 2. Source-level topology model and change application

Added canonical source-level topology inputs plus a deterministic applier that turns one `StructuralChangeSet` into a rebuilt source snapshot.

Files:

- `src/main/java/org/Aayush/routing/topology/TopologyModelSource.java`
- `src/main/java/org/Aayush/routing/topology/StructuralChangeApplier.java`

#### 3. Builder-side model compilation and runtime materialization

Added the builder-side pipeline that:

- compiles source inputs into a TARO model buffer,
- reconstructs `EdgeGraph`, `ProfileStore`, `TurnCostMap`, spatial runtime, and optional landmarks,
- rebuilds `RouteCore`,
- and publishes a new `TopologyRuntimeSnapshot`.

Files:

- `src/main/java/org/Aayush/routing/topology/CompiledTopologyModel.java`
- `src/main/java/org/Aayush/routing/topology/TopologyModelCompiler.java`
- `src/main/java/org/Aayush/routing/topology/TopologyRuntimeTemplate.java`
- `src/main/java/org/Aayush/routing/topology/TopologyRuntimeFactory.java`
- `src/main/java/org/Aayush/routing/topology/TopologyPublicationService.java`
- `src/main/java/org/Aayush/routing/topology/TopologyPublicationResult.java`
- `src/main/java/org/Aayush/routing/topology/TopologyValidationContext.java`
- `src/main/java/org/Aayush/routing/topology/TopologyValidationGate.java`

#### 4. Reload-safe runtime continuity

Added one small public seam on `RouteCore` so the publication path can preserve active live-overlay state while rebuilding a new topology snapshot.

Files:

- `src/main/java/org/Aayush/routing/core/RouteCore.java`

### Test Suite Migration and Expansion

Added new topology-focused coverage:

- `src/test/java/org/Aayush/routing/topology/StructuralChangeApplierTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyPublicationServiceTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyReloadSmokeTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyReloadPerfTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyTestFixtures.java`

Migrated older tests into explicit execution lanes with JUnit tags:

- `integration`: future/topology retained-result and reload tests
- `perf`: existing stress/perf guardrails plus new reload perf coverage
- `smoke`: new topology reload smoke scenarios

Updated files include:

- `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java`
- `src/test/java/org/Aayush/routing/future/FutureMatrixServiceTest.java`
- `src/test/java/org/Aayush/routing/topology/FailureQuarantineTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyReloadCoordinatorTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/SystemIntegrationStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/Stage15AddressingTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/Stage16TemporalTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitStressPerfTest.java`

Maven test-lane setup:

- default `mvn test` now runs the stable unit/integration/smoke lane and excludes `perf`
- `mvn -q -Psmoke-tests test` runs smoke coverage only
- `mvn -q -Pintegration-tests test` runs integration-tagged coverage
- `mvn -q -Pperf-tests test` runs perf guardrails explicitly

File:

- `pom.xml`

### Verification

Passed:

- `mvn -q -DskipTests test-compile`
- `mvn -q -Dtest=StructuralChangeApplierTest,TopologyPublicationServiceTest,TopologyReloadSmokeTest,TopologyReloadPerfTest,FutureRouteServiceTest,FutureMatrixServiceTest,TopologyReloadCoordinatorTest,FailureQuarantineTest test`
- `mvn -q -Psmoke-tests test`
- `mvn -q -Pintegration-tests -Dtest=TopologyPublicationServiceTest,TopologyReloadSmokeTest,FutureRouteServiceTest,FutureMatrixServiceTest,TopologyReloadCoordinatorTest,FailureQuarantineTest test`
- `mvn -q -Pperf-tests -Dtest=TopologyReloadPerfTest,RouteCoreStressPerfTest test`
- `mvn -q test`
- `mvn -q -Pperf-tests test`
- `.venv/bin/python -m pip install -e '.[dev]'`
- `.venv/bin/python -m pytest -q`

## Step 2A: Future Routing, Reload, and Retained-Result Hardening

Status: Completed on 2026-03-21

### Goal

Close the main correctness and operational gaps left after the initial v12/v13 foundation landed:

- ensure expected ETA and robust/P90 winners are not restricted to per-scenario-optimal candidate routes
- restore real spatial-index rebuild behavior after topology publication
- preserve active quarantine state across atomic reload
- remove full-graph node-failure expansion from the request path
- bound retained-result memory growth for route and matrix result stores

### Implementation

#### 1. Direct multi-scenario winner selection

Added a dedicated multi-scenario objective planner so the primary expected ETA and robust/P90 winners are selected by direct objective search instead of only from the union of scenario-optimal routes.

Key points:

- per-scenario shortest-path runs remain in place for scenario metrics and scenario-optimality accounting
- `FutureRouteObjectivePlanner` performs a label-setting search over scenario-cost vectors with same-terminal-edge dominance
- `FutureRouteEvaluator` now uses the direct planner as the source of truth for expected and robust winners and seeds alternatives with those winners before distinctness ranking
- aggregate-only winners now keep `optimalityProbability == 0` and choose dominant-scenario labeling by lowest regret among the highest-probability scenarios

Files:

- `src/main/java/org/Aayush/routing/core/FutureRouteObjectivePlanner.java`
- `src/main/java/org/Aayush/routing/core/FutureRouteEvaluator.java`

#### 2. Reload-safe quarantine and balanced spatial rebuilds

Hardened the topology rebuild path so it preserves runtime operational state and rebuilds usable spatial artifacts.

Key points:

- extracted deterministic source-to-runtime layout mapping with `TopologyIndexLayout`
- added `TopologyIncidentIndex` so node quarantines can expand through a topology-bound incident-edge index
- `FailureQuarantine.snapshot(...)` now precomputes blocked-edge expansion and exposes a no-graph `toLiveUpdates()` fast path
- `DefaultScenarioBundleResolver` now uses the pre-expanded quarantine fast path for incident-persistent scenarios
- `TopologyModelCompiler` now emits a deterministic balanced KD spatial index instead of a single-leaf fallback
- `TopologyRuntimeFactory` now rebinds still-active edge and node quarantines across atomic reload by stable source ids

Files:

- `src/main/java/org/Aayush/routing/topology/TopologyIndexLayout.java`
- `src/main/java/org/Aayush/routing/topology/TopologyIncidentIndex.java`
- `src/main/java/org/Aayush/routing/topology/FailureQuarantine.java`
- `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java`
- `src/main/java/org/Aayush/routing/topology/TopologyModelCompiler.java`
- `src/main/java/org/Aayush/routing/topology/TopologyRuntimeFactory.java`
- `src/main/java/org/Aayush/routing/topology/TopologyPublicationService.java`

#### 3. Bounded retained-result stores

Replaced the TTL-only in-memory retained-result maps with bounded caches that track bytes, entry counts, and eviction order explicitly.

Key points:

- route store defaults: `512` entries, `64 MiB` total, `2 MiB` per entry
- matrix store defaults: `128` entries, `256 MiB` total, `32 MiB` per entry
- eviction prefers expired entries first, then earliest expiry, then least-recently-read ties
- matrix payloads are compacted into flattened primitive buffers and optionally compressed before retention
- oversize entries are not retained, but the live evaluation result still returns normally

Files:

- `src/main/java/org/Aayush/routing/future/FutureResultStoreSizing.java`
- `src/main/java/org/Aayush/routing/future/InMemoryEphemeralRouteResultStore.java`
- `src/main/java/org/Aayush/routing/future/InMemoryEphemeralMatrixResultStore.java`

### Tests Added or Expanded

- `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java`
- `src/test/java/org/Aayush/routing/future/InMemoryEphemeralResultStoreTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyModelCompilerTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyPublicationServiceTest.java`

Coverage focus:

- aggregate-only compromise routes for expected and robust winners
- `optimalityProbability == 0` winner semantics
- bounded route and matrix retained-result eviction/compression
- balanced KD rebuild parity against brute-force snapping
- quarantine preservation and removed-subject drop across atomic reload

### Verification

Passed:

- `mvn -q -DskipTests test-compile`
- `mvn -q -Dtest=TopologyPublicationServiceTest,TopologyReloadSmokeTest,FailureQuarantineTest test`
- `mvn -q -Dtest=FutureRouteServiceTest,InMemoryEphemeralResultStoreTest,TopologyModelCompilerTest,TopologyPublicationServiceTest,FailureQuarantineTest,FutureMatrixServiceTest,TopologyReloadCoordinatorTest,TopologyReloadSmokeTest,SpatialRuntimeTest test`
- `mvn -q -Dtest=FutureRouteServiceTest,FutureMatrixServiceTest,FailureQuarantineTest,TopologyPublicationServiceTest,TopologyReloadCoordinatorTest,TopologyReloadSmokeTest,SpatialRuntimeTest test`
- `mvn -q -Pperf-tests -Dtest=TopologyReloadPerfTest,RouteCoreStressPerfTest test`

## Next Step

Step 3 remains the next major slice:

- expose retained `resultSetId` retrieval through the actual API/frontend layer
- wire summary/detail follow-up endpoints around the current in-memory retained-result services
- keep topology-version labeling visible in those responses so reload-era result semantics remain explicit
