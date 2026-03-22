# TARO Codebase Concern Map for Future Codex Sessions

Status date: 2026-03-22
Purpose: help future Codex sessions quickly find the right package, file, and doc for a given TARO concern.

## 1. Read these docs first when architecture matters

TARO's architecture intent is documented on disk and should be read before answering roadmap, topology, or product-behavior questions.

| Concern | Read first | Why |
|---|---|---|
| Latest staged roadmap | `docs/taro_v14_stagewise_breakdown.md` | Canonical phase/stage sequencing reference integrating v11 foundations with v12/v13 serving and topology work |
| Latest operational architecture | `docs/taro_v13_architecture_plan.md` | Current top-level direction for topology evolution, failure quarantine, and atomic reload |
| Future-aware routing products | `docs/taro_v12_architecture_plan.md` | Defines expected ETA, robust/P90, top-K alternatives, and retained-result serving |
| Offline-learning baseline | `docs/taro_v11_architecture_plan.md` | Explains the v11 builder/runtime split that v12/v13 sit on top of |
| v11 implementation sequencing | `docs/taro_v11_implementation_guide.md` | Useful when the question is about what the original v11 rollout intended |
| Requirement contracts and current stage dependencies | `docs/taro_v14_stagewise_breakdown.md` and `docs/taro_v11_single_source_of_truth.md` | Best source for current stage numbers, closure rules, and requirement contracts |
| Historical v11-only stage numbering | `docs/taro_v11_ssot_stagewise_breakdown.md` | Use only when legacy stage-number mapping is required |
| What actually landed for v12/v13 in this repo | `docs/taro_v12_v13_migration_report.md` | Current implementation tracker for the v12/v13 additions now in code |
| Stage 18 closeout details | `docs/stage18_delta_report.md` | Best reference for Stage 18 bundle/runtime binding closure |
| Runtime immutability and lock posture | `docs/trait_runtime_lock_audit_report.md` | Useful for startup-lock and no-request-time-switching questions |

Important note:

- `README.md` still says v12 scenario-aware serving and v13 topology runtime are not implemented.
- The codebase and `docs/taro_v12_v13_migration_report.md` show that substantial v12/v13 foundations have already landed.
- `docs/taro_v14_stagewise_breakdown.md` is now the canonical staged roadmap; the old v11 stagewise breakdown remains historical.
- For current-state questions, trust the migration report and code over the stale README status bullets.
- The repo still contains some compatibility and legacy-shaped paths that may no longer be canonical under v14.
- Future dev sessions should classify touched code as canonical, compatibility-only, stale, or dead-before-investing effort, and should prefer pruning or isolating non-canonical paths when correctness and roadmap alignment allow it.

## 2. Current repo shape in one screen

Main production code lives under `src/main/java/org/Aayush`.

| Package | What lives there |
|---|---|
| `routing.core` | request normalization, route/matrix orchestration, planners, future evaluators |
| `routing.graph` | immutable graph and turn-cost runtime storage |
| `routing.profile` | immutable temporal multiplier lookup |
| `routing.cost` | canonical edge-cost composition |
| `routing.overlay` | bounded live override layer |
| `routing.spatial` | nearest-node KD query runtime |
| `routing.heuristic` | Euclidean, spherical, and landmark heuristics |
| `routing.traits.addressing` | Stage 15 typed addressing |
| `routing.traits.temporal` | Stage 16 temporal binding |
| `routing.traits.transition` | Stage 17 transition binding |
| `routing.traits.registry` | Stage 18 trait-bundle registry and hashing |
| `routing.execution` | startup execution-profile binding and atomic profile swaps |
| `routing.future` | v12 request/result contracts, stores, and topology-aware service facades |
| `routing.topology` | v13 change sets, builder-side rebuild, publication, reload, quarantine |
| `serialization.flatbuffers` | model contract validation and generated FlatBuffers classes |
| `core.id`, `core.time` | shared primitives used across the runtime |

Main test code lives under `src/test/java/org/Aayush`, with small Python-side support code and tests under `src/main/python`.

## 3. Current high-level user workflow

This is the current runtime behavior as implemented, not just planned.

### 3.1 Deterministic point-to-point route workflow

1. Caller builds `RouteRequest`.
2. `RouteCore.route()` is the main entrypoint.
3. `DefaultRequestNormalizer` resolves startup-locked execution mode and delegates endpoint normalization.
4. `AddressingTraitEngine` resolves external IDs or coordinates into internal node ids.
5. `RouteCore` chooses the planner from the locked execution profile or legacy request hints.
6. `BidirectionalTdAStarPlanner` or `EdgeBasedRoutePlanner` expands over `EdgeGraph`.
7. `CostEngine` computes edge cost from:
   `base_weight * temporal_multiplier * live_penalty + turn_cost`
8. The route is mapped back into a `RouteResponse` with external node ids and resolved-address metadata.

### 3.2 Deterministic matrix workflow

1. Caller builds `MatrixRequest`.
2. `RouteCore.matrix()` normalizes sources and targets through the same Stage 15/16/17/18 locks.
3. `NativeOneToManyMatrixPlanner` is the primary matrix engine.
4. Dijkstra matrices run natively for `DIJKSTRA + NONE`.
5. A* matrices run natively for bounded target counts; oversized target sets fall back to bounded compatibility batching via `TemporaryMatrixPlanner`.
6. `RouteCoreMatrixSupport` maps internal results back into `MatrixResponse`.

### 3.3 Future-aware route workflow (v12 layer now present)

1. Caller builds `FutureRouteRequest`.
2. `TopologyAwareFutureRouteService` captures the currently active topology snapshot.
3. `FutureRouteService` delegates to `FutureRouteEvaluator`.
4. `FutureRouteEvaluator` normalizes the underlying `RouteRequest` through `RouteCore`.
5. A `FailureQuarantine.Snapshot` is captured for the request departure tick.
6. `ScenarioBundleResolver` materializes a deterministic `ScenarioBundle`.
7. Each scenario gets a scenario-specific `CostEngine` by cloning the active `LiveOverlay` and applying scenario live updates.
8. `RouteCore.computeInternal(...)` is run once per scenario.
9. `FutureRouteObjectivePlanner` runs direct multi-scenario objective search for the primary expected and robust winners.
10. Results are aggregated into:
   expected ETA,
   robust/P90,
   top-K materially distinct alternatives.
11. The full `FutureRouteResultSet` is stored in an `EphemeralRouteResultStore` and returned with a `resultSetId`.

### 3.4 Future-aware matrix workflow (v12 matrix migration now present)

1. Caller builds `FutureMatrixRequest`.
2. `TopologyAwareFutureMatrixService` captures the active topology snapshot.
3. `FutureMatrixEvaluator` runs a deterministic matrix once per scenario.
4. Matrix cells are aggregated into probability-aware summaries:
   reachability probability,
   expected cost,
   P50,
   P90,
   min/max,
   earliest/latest arrival.
5. The retained result is stored in `EphemeralMatrixResultStore`.

Current matrix note:

- Matrix future serving is summary-first.
- It retains per-scenario matrices, but it does not yet expose fixed-path top-K families the way future route serving does.

### 3.5 Transient failure workflow (v13 fast path)

1. Failures are recorded in `FailureQuarantine`.
2. A snapshot is captured for the request time.
3. Topology-bound snapshots pre-expand node and edge failures into blocking `LiveUpdate` overlays through an incident-edge index.
4. Those updates participate in scenario generation and scenario-specific cost surfaces without a full-graph scan on the common request path.
5. The base graph is not mutated in place.

### 3.6 Structural topology change workflow (v13 slow path)

1. Build a typed `StructuralChangeSet`.
2. `StructuralChangeApplier` applies it to `TopologyModelSource`.
3. `TopologyModelCompiler` compiles the candidate source into a TARO FlatBuffer model.
4. `TopologyRuntimeFactory` rebuilds `EdgeGraph`, `ProfileStore`, `TurnCostMap`, optional `SpatialRuntime`, optional landmarks, and a new `RouteCore`, then rebinds still-active quarantines by stable source ids when possible.
5. `TopologyPublicationService` runs validation gates.
6. `TopologyReloadCoordinator` atomically swaps the active `TopologyRuntimeSnapshot`.
7. `ReloadCompatibilityPolicy` decides whether retained result sets survive or are invalidated.

## 4. Start-here file map by concern

Use this table as the quickest way to find the correct code area.

| If the concern is... | Read these files first | Why these are the right anchors |
|---|---|---|
| Main runtime orchestration | `src/main/java/org/Aayush/routing/core/RouteCore.java`, `src/main/java/org/Aayush/routing/core/DefaultRequestNormalizer.java` | `RouteCore` is the main facade; the normalizer explains how requests become internal plans |
| Route request/response contracts | `src/main/java/org/Aayush/routing/core/RouteRequest.java`, `src/main/java/org/Aayush/routing/core/RouteResponse.java` | Current client-facing route surface |
| Matrix request/response contracts | `src/main/java/org/Aayush/routing/core/MatrixRequest.java`, `src/main/java/org/Aayush/routing/core/MatrixResponse.java` | Current client-facing matrix surface |
| Point-to-point search behavior | `src/main/java/org/Aayush/routing/core/BidirectionalTdAStarPlanner.java`, `src/main/java/org/Aayush/routing/core/EdgeBasedRoutePlanner.java`, `src/main/java/org/Aayush/routing/core/PathEvaluator.java` | The two core route planners plus the authoritative path replay/evaluation layer |
| Matrix execution behavior | `src/main/java/org/Aayush/routing/core/NativeOneToManyMatrixPlanner.java`, `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`, `src/main/java/org/Aayush/routing/core/MatrixQueryContext.java` | Native matrix path, fallback path, and row-level state |
| Search guardrails and numeric safety | `src/main/java/org/Aayush/routing/core/SearchBudget.java`, `src/main/java/org/Aayush/routing/core/MatrixSearchBudget.java`, `src/main/java/org/Aayush/routing/core/TerminationPolicy.java` | Budget caps and numeric safety live here |
| Runtime graph layout | `src/main/java/org/Aayush/routing/graph/EdgeGraph.java` | The core immutable graph runtime |
| Turn penalties / forbidden turns | `src/main/java/org/Aayush/routing/graph/TurnCostMap.java`, `src/main/java/org/Aayush/routing/traits/transition/TransitionCostStrategy.java` | Static turn storage plus runtime transition semantics |
| Temporal profiles | `src/main/java/org/Aayush/routing/profile/ProfileStore.java` | Immutable temporal multiplier store |
| Canonical cost formula | `src/main/java/org/Aayush/routing/cost/CostEngine.java` | The single best file for understanding final edge cost |
| Live operational overrides | `src/main/java/org/Aayush/routing/overlay/LiveOverlay.java`, `src/main/java/org/Aayush/routing/overlay/LiveUpdate.java` | Stage 7 live slowdown/block layer |
| Spatial coordinate snapping | `src/main/java/org/Aayush/routing/spatial/SpatialRuntime.java`, `src/main/java/org/Aayush/routing/spatial/SpatialMatch.java` | KD-tree nearest-node runtime and result shape |
| Heuristic selection and validation | `src/main/java/org/Aayush/routing/heuristic/HeuristicFactory.java`, `src/main/java/org/Aayush/routing/heuristic/DefaultHeuristicProviderFactory.java` | Entry point for heuristic construction and validation |
| Landmark preprocessing/runtime | `src/main/java/org/Aayush/routing/heuristic/LandmarkPreprocessor.java`, `src/main/java/org/Aayush/routing/heuristic/LandmarkStore.java`, `src/main/java/org/Aayush/routing/heuristic/LandmarkSerializer.java` | ALT preprocessing, storage, and serialization |
| Stage 15 addressing | `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`, `src/main/java/org/Aayush/routing/traits/addressing/AddressingRuntimeBinder.java`, `src/main/java/org/Aayush/routing/traits/addressing/AddressingPolicy.java` | Request endpoint resolution, startup binding, and snap policies |
| Addressing config surface | `src/main/java/org/Aayush/routing/traits/addressing/AddressingRuntimeConfig.java`, `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitCatalog.java`, `src/main/java/org/Aayush/routing/traits/addressing/CoordinateStrategyRegistry.java` | What can be selected and how |
| Stage 16 temporal binding | `src/main/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinder.java`, `src/main/java/org/Aayush/routing/traits/temporal/TemporalContextResolver.java`, `src/main/java/org/Aayush/routing/traits/temporal/TemporalPolicy.java` | Startup-bound temporal behavior and runtime resolution |
| Time math helpers | `src/main/java/org/Aayush/core/time/TimeUtils.java` | Low-level day/bucket/tick utilities used widely |
| Stage 17 transition binding | `src/main/java/org/Aayush/routing/traits/transition/TransitionRuntimeBinder.java`, `src/main/java/org/Aayush/routing/traits/transition/TransitionPolicy.java`, `src/main/java/org/Aayush/routing/traits/transition/TransitionStrategyRegistry.java` | Startup-bound transition behavior and compatibility checks |
| Stage 18 trait bundles | `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeBinder.java`, `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRegistry.java`, `src/main/java/org/Aayush/routing/traits/registry/TraitBundleCompatibilityPolicy.java`, `src/main/java/org/Aayush/routing/traits/registry/TraitBundleHasher.java` | Named bundles, compatibility, and stable bundle hashing |
| Startup execution-profile locking | `src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java`, `src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java`, `src/main/java/org/Aayush/routing/execution/ExecutionRuntimeConfig.java` | Startup selection of algorithm + heuristic |
| Atomic execution-profile swaps | `src/main/java/org/Aayush/routing/execution/RouterRuntimeManager.java`, `src/main/java/org/Aayush/routing/execution/RouterRuntimeFactory.java` | Runtime swapping for execution profiles without per-request rebinding |
| Future-aware route evaluation | `src/main/java/org/Aayush/routing/core/FutureRouteEvaluator.java`, `src/main/java/org/Aayush/routing/core/FutureRouteObjectivePlanner.java`, `src/main/java/org/Aayush/routing/future/FutureRouteService.java`, `src/main/java/org/Aayush/routing/future/FutureRouteResultSet.java` | Main v12 route-serving flow plus direct multi-scenario winner selection |
| Future-aware matrix evaluation | `src/main/java/org/Aayush/routing/core/FutureMatrixEvaluator.java`, `src/main/java/org/Aayush/routing/future/FutureMatrixService.java`, `src/main/java/org/Aayush/routing/future/FutureMatrixResultSet.java` | Main v12 matrix-serving flow |
| Scenario generation | `src/main/java/org/Aayush/routing/future/ScenarioBundleResolver.java`, `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java`, `src/main/java/org/Aayush/routing/core/FutureScenarioSupport.java` | Scenario materialization and scenario-level cost-engine creation |
| Retained result stores | `src/main/java/org/Aayush/routing/future/InMemoryEphemeralRouteResultStore.java`, `src/main/java/org/Aayush/routing/future/InMemoryEphemeralMatrixResultStore.java`, `src/main/java/org/Aayush/routing/future/FutureResultStoreSizing.java`, `src/main/java/org/Aayush/routing/topology/ReloadCompatibilityPolicy.java` | Bounded TTL retention, byte budgeting, matrix compaction/compression, and reload-era invalidation behavior |
| Topology snapshot identity | `src/main/java/org/Aayush/routing/topology/TopologyVersion.java`, `src/main/java/org/Aayush/routing/topology/TopologyRuntimeSnapshot.java` | Versioning and active snapshot binding |
| Transient failure quarantine | `src/main/java/org/Aayush/routing/topology/FailureQuarantine.java`, `src/main/java/org/Aayush/routing/topology/TopologyIncidentIndex.java` | Fast-path edge/node failure suppression with topology-bound incident expansion |
| Batched structural changes | `src/main/java/org/Aayush/routing/topology/StructuralChangeSet.java`, `src/main/java/org/Aayush/routing/topology/StructuralChangeApplier.java`, `src/main/java/org/Aayush/routing/topology/TopologyModelSource.java` | Typed v13 change model and deterministic application |
| Builder-side compilation and reload | `src/main/java/org/Aayush/routing/topology/TopologyModelCompiler.java`, `src/main/java/org/Aayush/routing/topology/TopologyIndexLayout.java`, `src/main/java/org/Aayush/routing/topology/TopologyRuntimeFactory.java`, `src/main/java/org/Aayush/routing/topology/TopologyPublicationService.java`, `src/main/java/org/Aayush/routing/topology/TopologyReloadCoordinator.java` | End-to-end rebuild, balanced spatial artifacts, quarantine rebinding, validation, and atomic swap |
| Schema and FlatBuffers model contract | `src/main/resources/flatbuffers/taro_model.fbs`, `src/main/java/org/Aayush/serialization/flatbuffers/ModelContractValidator.java` | The wire/storage contract plus metadata validation |
| Generated FlatBuffers Java classes | `src/main/java/org/Aayush/serialization/flatbuffers/taro/model/*` | Generated schema bindings; read only when needed |
| Python-side current scope | `src/main/python/Utils/IDMapper.py`, `src/main/python/tests/*` | The only meaningful Python logic currently in-tree is small and utility-focused |

## 5. Fast heuristics for where to look

| Symptom or question | Most likely files |
|---|---|
| "Why did this route use the wrong endpoint?" | `AddressingTraitEngine.java`, `AddressingPolicy.java`, `SpatialRuntime.java` |
| "Why did the request reject a selector/config?" | `DefaultRequestNormalizer.java`, `TraitBundleRuntimeBinder.java`, `DefaultExecutionRuntimeBinder.java` |
| "Why is the cost different by departure time?" | `CostEngine.java`, `TemporalContextResolver.java`, `ProfileStore.java`, `TimeUtils.java` |
| "Why does turn behavior differ across modes?" | `TransitionPolicy.java`, `TransitionRuntimeBinder.java`, `TurnCostMap.java` |
| "Why did A* and Dijkstra diverge?" | `BidirectionalTdAStarPlanner.java`, `HeuristicFactory.java`, heuristic tests |
| "Why did matrix behavior change?" | `NativeOneToManyMatrixPlanner.java`, `TemporaryMatrixPlanner.java`, `RouteCoreMatrixSupport.java` |
| "Why is the expected or robust future route wrong?" | `FutureRouteObjectivePlanner.java`, `FutureRouteEvaluator.java`, `FutureRouteServiceTest.java` |
| "Why did a future-aware result disappear after reload?" | `ReloadCompatibilityPolicy.java`, retained result stores, `TopologyReloadCoordinator.java` |
| "Why did a quarantine disappear or stop blocking after reload?" | `TopologyRuntimeFactory.java`, `TopologyIndexLayout.java`, `FailureQuarantine.java`, `TopologyPublicationServiceTest.java` |
| "How are failures represented without mutating the graph?" | `FailureQuarantine.java`, `DefaultScenarioBundleResolver.java`, `FutureScenarioSupport.java` |
| "How is a topology change published?" | `StructuralChangeSet.java`, `StructuralChangeApplier.java`, `TopologyPublicationService.java` |

## 6. How the current code maps to v11 stages and later v12/v13 work

This section is partly direct observation and partly inference from the repo shape.

Current roadmap note:

- use `docs/taro_v14_stagewise_breakdown.md` for current phase/stage sequencing and closure rules
- use the table below only when you need to relate the current repo back to the older v11 stage numbering

| Stage / layer | Current repo status | Evidence |
|---|---|---|
| v11 Stage 1: ID translation | Implemented | `core.id`, `FastUtilIDMapper`, tests |
| v11 Stage 2: temporal utilities | Implemented | `core.time.TimeUtils`, tests |
| v11 Stage 3: FlatBuffers schema contract | Implemented | `taro_model.fbs`, generated bindings, serialization tests |
| v11 Stage 4: edge graph | Implemented | `routing.graph.EdgeGraph`, tests |
| v11 Stage 5: turn cost lookup | Implemented | `routing.graph.TurnCostMap`, tests |
| v11 Stage 6: search infrastructure | Implemented | `routing.search`, planner tests |
| v11 Stage 7: sparse live overlay | Implemented | `routing.overlay`, tests |
| v11 Stage 8: spatial runtime | Implemented | `routing.spatial`, tests |
| v11 Stage 9: profile store | Implemented | `routing.profile.ProfileStore`, tests |
| v11 Stage 10: time-dependent cost engine | Implemented | `routing.cost.CostEngine`, tests |
| v11 Stage 11: geometry heuristics | Implemented | `routing.heuristic` geometry providers, tests |
| v11 Stage 12: landmark preprocessing | Implemented | landmark preprocessor/store/serializer, tests |
| v11 Stage 13: bidirectional TD-A* | Implemented | planner code and Stage 13 guardrail tests |
| v11 Stage 14: one-to-many matrix | Implemented | native/fallback matrix planners and Stage 14 tests |
| v11 Stage 15: addressing trait | Implemented | `routing.traits.addressing`, Stage 15 tests |
| v11 Stage 16: temporal trait | Implemented | `routing.traits.temporal`, Stage 16 tests |
| v11 Stage 17: transition trait | Implemented | `routing.traits.transition`, Stage 17 tests |
| v11 Stage 18: trait registry/configuration | Implemented | `routing.traits.registry`, Stage 18 tests, delta report |
| v11 Stages 19-24: offline ingestion / compile pipeline | Not present as a full v11 builder stack in this repo; only partial/build-adjacent pieces exist | There is no dedicated v11 builder module family, but there is schema/compiler support and some Python utilities |
| v11 Stage 25: model loader / hot reload | Partially superseded by v13 topology snapshot publication and reload | `routing.topology` provides runtime rebuild and atomic snapshot swap |
| v11 Stage 26: HTTP API | Not implemented | no API/server/controller layer in repo |
| v11 Stages 27-28: telemetry / observability | Only local telemetry objects and test metrics exist; no full service-level observability layer | telemetry DTOs exist in traits/runtime, but no standalone aggregation/metrics subsystem |
| v12 future-aware route serving | Implemented foundation | future route request/result contracts, evaluator, stores, tests |
| v12 future-aware matrix serving | Implemented foundation | future matrix evaluator, stores, tests, migration report Step 1 |
| v12 richer scenario generation | Still minimal | default resolver is bounded and quarantine-driven |
| v12 frontend/API retrieval flow | Not wired to an actual API | retained results exist in-memory, but no HTTP layer |
| v13 failure quarantine | Implemented foundation | `FailureQuarantine`, topology tests |
| v13 structural rebuild and atomic reload | Implemented foundation | `TopologyModelSource`, compiler, runtime factory, publication service, reload coordinator, topology tests |

## 7. Practical guidance for future Codex work

- If the question mentions stages, roadmap order, or implementation sequence, start with `docs/taro_v14_stagewise_breakdown.md`, then pull in v13/v12 architecture detail as needed.
- If the question mentions the latest staged roadmap or roadmap order, start with `docs/taro_v14_stagewise_breakdown.md`; if it asks for the latest architecture plan specifically, pair that with `docs/taro_v13_architecture_plan.md`.
- If the task is about route correctness, start with `RouteCore`, `CostEngine`, and the relevant trait binder before touching planners.
- If the task is about matrix behavior, check whether the request is on the native path or the compatibility fallback path before debugging anything deeper.
- If the task mentions "future", inspect both `routing.future` and the evaluator in `routing.core`; the contracts and the logic are intentionally split.
- If a future-aware aggregate winner looks suspicious, inspect `FutureRouteObjectivePlanner` before changing distinctness or confidence code.
- If the task mentions topology reload, always inspect `ReloadCompatibilityPolicy` and retained-result stores together; reload semantics are not just about swapping `RouteCore`.
- If a reload bug mentions missing failures or slow node suppression, inspect `TopologyRuntimeFactory`, `TopologyIndexLayout`, `TopologyIncidentIndex`, and `FailureQuarantine` together.
- If a behavior claim conflicts with `README.md`, verify against `docs/taro_v12_v13_migration_report.md` and the codebase.
