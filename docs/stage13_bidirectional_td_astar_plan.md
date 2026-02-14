# Stage 13 Implementation Plan: Bidirectional Time-Dependent A*

Date: 2026-02-13  
Status: Implemented and validated (2026-02-14)  
Scope: Runtime route planner upgrade only (builds on Stage 12)

## 1. Objective

Implement Stage 13 as a **bidirectional time-dependent A\*** planner that:

- preserves exact route optimality under current TARO cost semantics,
- keeps deterministic behavior under fixed model + overlay snapshots,
- improves pruning and tail latency over the Stage 12 single-direction planner,
- remains easy to extend for Stage 14 (matrix) and Stage 15-18 (trait-driven execution).

## 2. Source Context (Current + Older References)

Primary constraints and historical intent come from:

- `docs/taro_v11_ssot_stagewise_breakdown.md` (Stage 13 functional/NFR/test classes)
- `docs/taro_v11_single_source_of_truth.md` (global contracts, parity, determinism)
- `docs/stage12_route_core_impl.md` (current implementation handoff)
- `docs/taro_stagewise_v10_1_guide.md` (older stage-ordering and Stage 13/14 scope continuity)
- `ResearchData/taro_v11_impl_guide.md` (old research track parity and non-regression hard gates)

Critical carry-forward rules:

- A\* must remain admissible and deterministic.
- A\*/Dijkstra parity is a release gate.
- Memory must remain bounded for configured query budgets.
- Stage 14 matrix engine will replace temporary pairwise expansion; Stage 13 should expose reusable primitives.

## 3. Current Runtime Baseline (What We Build On)

Existing implementation already provides the right foundation:

- `RouteCore` request validation + heuristic provider binding (`src/main/java/org/Aayush/routing/core/RouteCore.java`)
- `RoutePlanner` abstraction for point-to-point search (`src/main/java/org/Aayush/routing/core/RoutePlanner.java`)
- current single-direction edge-label planner with dominance filtering (`src/main/java/org/Aayush/routing/core/EdgeBasedRoutePlanner.java`)
- cost semantics already centralized in `CostEngine` (`src/main/java/org/Aayush/routing/cost/CostEngine.java`)
- deterministic parity/determinism/stress tests already present in:
  - `src/test/java/org/Aayush/routing/core/RouteCoreTest.java`
  - `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java`

Baseline verification run at planning time:

- `mvn -q -Dtest=RouteCoreTest test` (pass)
- `mvn -q -Dtest=RouteCoreStressPerfTest test` (pass)

## 4. Gap to Stage 13

Current A\* path is still single-direction (`EdgeBasedRoutePlanner` with `useHeuristic=true`), so we are missing:

- bidirectional search orchestration,
- explicit bounded-query budgets (labels/frontiers/expansions),
- reusable reverse traversal infrastructure for future algorithms,
- Stage 13-specific observability for pruning quality and termination behavior.

## 5. Proposed Stage 13 Design

## 5.1 Core Planner Shape

Add `BidirectionalTdAStarPlanner implements RoutePlanner` with two coordinated search lanes:

- **Forward lane (exact TD lane):**
  - same correctness semantics as current planner (uses `CostEngine` with full time/turn/live behavior),
  - produces final route/path/cost/arrival.
- **Backward lane (lower-bound lane on reverse graph):**
  - computes admissible reverse lower bounds incrementally,
  - does not directly decide final route,
  - strengthens forward pruning safely.

This split avoids time-reversal correctness traps while still delivering true two-frontier behavior.

## 5.2 Reusable New Primitives

Add internal runtime primitives to keep future hooks clean:

1. `ReverseEdgeIndex`
- maps edge/node to incoming edges once per graph contract,
- reused by Stage 13 planner and Stage 14 matrix engine.

2. `SearchBudget`
- per-query limits: `maxSettledStates`, `maxLabels`, `maxFrontierSize`,
- deterministic fail-fast reason codes when exceeded.

3. `PlannerQueryContext`
- thread-confined reusable buffers/lists/heaps,
- reduces hot-path allocations and GC spikes under concurrency.

4. `TerminationPolicy`
- encapsulates stop conditions (goal upper bound + frontier lower bound checks),
- allows future specialized policies (matrix one-to-many, trait-specific modes).

5. `PathEvaluator`
- replays candidate stitched paths through `CostEngine` (exact forward semantics),
- future reuse for route auditing and parity diagnostics.

## 5.3 RouteCore Wiring Strategy

Keep public API stable. Internally:

- keep `RoutingAlgorithm` enum unchanged (`DIJKSTRA`, `A_STAR`),
- keep `RouteRequest`/`RouteResponse` unchanged,
- switch default `aStarPlanner` from `EdgeBasedRoutePlanner(true)` to `BidirectionalTdAStarPlanner`,
- keep `dijkstraPlanner` unchanged as parity oracle.

Optional extension seam:

- allow builder injection for custom `aStarPlanner` (same pattern as existing matrix planner injection).

## 5.4 Determinism Rules

Maintain deterministic ordering with explicit tie-break sequence:

1. priority (`f` or lane key)  
2. arrival ticks  
3. edge id  
4. label id

No nondeterministic iteration over hash-based structures in hot-path decisions.

## 6. Performance Plan

## 6.1 Hot-Path Improvements

- Avoid object churn by reusing query buffers (`PlannerQueryContext`).
- Precompute reverse adjacency once; avoid per-query reverse scans.
- Clamp non-finite heuristic outputs to safe zero lower bound (existing behavior continuity).
- Keep saturation-safe tick arithmetic and finite-cost guards.

## 6.2 Measured Gates (Stage 13)

Keep existing Stage 12 smoke gates and add Stage 13-specific guards:

- p95 settled-state count not worse than Stage 12 baseline on fixed seed workloads.
- no regression in deterministic repeated-query tests.
- parity mismatch count must remain zero on pinned random suites.
- memory growth per query bounded by configured budgets.

## 7. Durability Under Stress

Failure handling must be deterministic and reason-coded:

- budget exceeded (settled/label/frontier),
- numeric safety breach (NaN/overflow in derived priorities),
- impossible reconstruction state (defensive invariant failure).

Stress scenarios to explicitly test:

- dense graphs with high branching factors,
- large equal-cost plateaus (tie-break stability),
- heavy live overlay updates + expired entries,
- turn-cost-heavy transitions,
- near-overflow departure ticks and long travel horizons.

## 8. Test and Validation Plan

## 8.1 Functional (Stage 13 classes)

- trivial route, disconnected route, long route,
- departure-time-sensitive route differences,
- turn-cost and live-overlay interaction routes.

## 8.2 Correctness/Parity

- A\* vs Dijkstra parity on deterministic fixture graphs,
- pinned-seed randomized parity suite,
- LANDMARK/EUCLIDEAN/SPHERICAL/NONE heuristic combinations (where valid).

## 8.3 Stress/Perf/Concurrency

- extend `RouteCoreStressPerfTest` with Stage 13 planner enabled,
- concurrent determinism under repeated identical requests,
- sustained random workloads with budget assertions.

## 9. Implementation Sequence

1. Add internal primitives (`ReverseEdgeIndex`, `SearchBudget`, `PlannerQueryContext`, `TerminationPolicy`).
2. Implement `BidirectionalTdAStarPlanner` behind current `RoutePlanner` contract.
3. Wire planner into `RouteCore` default A\* path, preserve Dijkstra path.
4. Add/extend parity + stress + determinism tests.
5. Add Stage 13 implementation note doc update with measured baseline deltas.

## 10. Stage 13 Exit Criteria

Stage 13 is considered complete only when:

- all Stage 12 tests still pass,
- new Stage 13 tests pass (functional + parity + stress),
- zero A\*/Dijkstra parity mismatches on pinned suites,
- deterministic repeatability maintained under concurrency,
- no smoke perf regression beyond agreed tolerance.

## 11. Stage 14 and Future Hook Readiness

This design intentionally prepares for:

- Stage 14 one-to-many Dijkstra by reusing `ReverseEdgeIndex`, `SearchBudget`, and termination abstractions,
- Stage 15-18 transition/addressing traits by isolating planner internals from `RouteCore` API contracts,
- later algorithm experiments via planner injection without changing client request/response schemas.

## 12. Refined Stage 13 Blueprint (Implementation-Ready)

This section refines the above plan into concrete classes, invariants, and fail-fast contracts.

### 12.1 Concrete Class Map

Runtime (`src/main/java/org/Aayush/routing/core/`):

- `BidirectionalTdAStarPlanner`
  - default Stage 13 A* planner implementation.
  - forward exact lane + reverse lower-bound lane.
- `ReverseEdgeIndex`
  - immutable incoming-edge CSR view for reverse traversal.
- `SearchBudget`
  - deterministic query limits (`settled`, `labels`, `frontier`) with reason-coded exceptions.
- `PlannerQueryContext`
  - thread-confined reusable query buffers (labels, dominance lists, reverse arrays, frontiers).
- `TerminationPolicy`
  - stop condition and numeric-safety checks.
- `PathEvaluator`
  - exact forward replay of candidate edge paths + node-path reconstruction.
- `ForwardFrontierState`, `BackwardFrontierState`, `DominanceLabelStore`
  - deterministic frontier/label internals extracted for clarity and reuse.

Route-core wiring:

- `RouteCore` default `A_STAR` planner: `BidirectionalTdAStarPlanner`.
- `RouteCore` `DIJKSTRA`: unchanged (`EdgeBasedRoutePlanner(false)`).
- optional builder seam: `RouteCore.builder().aStarPlanner(...)`.

### 12.2 Deterministic Ordering Contracts

Forward frontier tie-break order (fixed):

1. `priority` (`g + h`)
2. `arrivalTicks`
3. `edgeId`
4. `labelId`

Backward frontier tie-break order (fixed):

1. `reverseLowerBoundDistance`
2. `nodeId`

No hash-iteration order affects expansion decisions.

### 12.3 Admissible Reverse Lower Bound Model

Reverse lane uses static per-edge lower bound:

- `edgeLowerBound = baseWeight * min(1.0, profileMinMultiplier)`
- live overlay lower bound fixed at `1.0`
- turn lower bound fixed at `0.0`

Heuristic composition in forward lane:

- `h(node) = max(existingGoalBoundHeuristic(node), settledReverseLowerBound(node))`
- reverse bound is used only after reverse-node settle, preserving admissibility.

### 12.4 Fail-Fast Surfaces

Budget failures:

- `H13_BUDGET_SETTLED_EXCEEDED`
- `H13_BUDGET_LABEL_EXCEEDED`
- `H13_BUDGET_FRONTIER_EXCEEDED`

Numeric safety failures:

- `H13_NUMERIC_NON_FINITE_PRIORITY`
- `H13_NUMERIC_NEGATIVE_PRIORITY`

Path replay failures:

- `H13_PATH_NON_FINITE_EDGE_COST`
- `H13_PATH_NON_FINITE_PATH_COST`
- `H13_PATH_NODE_RECONSTRUCTION_FAILED`

Route-core wrapping codes:

- `H13_SEARCH_BUDGET_EXCEEDED`
- `H13_NUMERIC_SAFETY_BREACH`
- `H13_PATH_EVALUATION_FAILED`

### 12.5 Configurable Budget Properties

System properties (all optional; non-positive/invalid values become unbounded):

- `taro.routing.stage13.maxSettledStates`
- `taro.routing.stage13.maxLabels`
- `taro.routing.stage13.maxFrontierSize`

## 13. Implementation Status (Closed)

Implemented and validated:

- Stage 13 runtime primitives and planner classes listed in 12.1.
- `RouteCore` default A* path switched to `BidirectionalTdAStarPlanner`.
- A* planner injection seam added in `RouteCore` builder.
- deterministic wrapping for Stage 13 fail-fast exceptions.
- completed Stage 13 tests:
  - `BidirectionalTdAStarPlannerTest` (cost parity, settled/label/frontier budget fail-fast, contract guards, plateau/live/overflow scenarios).
  - `RouteCoreTest` additions for custom A* injection plus budget/numeric/path fail-fast wrapping and TD regression on A*.
  - `RouteCoreStressPerfTest` extensions for:
    - heavy live-overlay churn + expiry stress parity/determinism,
    - Stage12-vs-Stage13 pinned workload delta gate with p50/p95 settled-state comparison and heap telemetry.
- final Stage 13 delta report captured in `docs/stage13_delta_report.md`.
