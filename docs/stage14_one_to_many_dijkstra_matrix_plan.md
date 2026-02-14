# Stage 14 Implementation Plan: One-to-Many Dijkstra Matrix

Date: 2026-02-14  
Status: Implemented (closed)  
Scope: Replace Stage 12 temporary matrix pairwise expansion with a dedicated one-to-many edge-based Dijkstra matrix engine, while preserving current RouteCore contracts and Stage 13 parity guarantees.

## 1. Objective

Implement Stage 14 so matrix routing is:

- exact under current Stage 10 cost semantics (base + profile + live + turn),
- deterministic under fixed model + overlay snapshot,
- substantially faster and lower memory than pairwise expansion,
- stress-stable under concurrency and live-overlay churn,
- ready for Stage 15-18 traits and Stage 26 API serving without contract churn.

## 2. Source Context and Carry-Forward Constraints

Primary references:

- `docs/taro_v11_ssot_stagewise_breakdown.md` (Stage 14 FR/NFR/test classes)
- `docs/taro_v11_single_source_of_truth.md` (`FR-013`, `FR-014`, `NFR-005`, `NFR-007`)
- `docs/stage12_route_core_impl.md` (Stage 14 revisit note)
- `docs/stage13_bidirectional_td_astar_plan.md` (reusable primitives and fail-fast design)
- `docs/stage13_delta_report.md` (current deterministic perf-gate format)

Non-negotiable carry-forward constraints:

- deterministic output for identical snapshot + query inputs,
- zero accepted parity regressions at release gates,
- bounded memory/work via deterministic budgets,
- no runtime stochastic behavior,
- RouteCore external request/response contracts remain stable.

## 3. Current Runtime Baseline (What Stage 14 Builds On)

Current matrix path (Stage 12 behavior still active):

- `RouteCore.matrix(...)` delegates to `MatrixPlanner` (`src/main/java/org/Aayush/routing/core/RouteCore.java`).
- default planner is `TemporaryMatrixPlanner` (`src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`).
- `TemporaryMatrixPlanner` performs pairwise expansion by calling `RouteCore.computeInternal(...)` for every source-target pair.

## 3.1 Stage 12 Revisit File Set (Must Be Revised in Stage 14)

Stage 12 explicitly deferred matrix internals to Stage 14. The following files are mandatory revisit targets:

- `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`
  - replace as default matrix path with native one-to-many planner.
  - retain only as optional compatibility/fallback path until Stage 14 gates are green.
- `src/main/java/org/Aayush/routing/core/RouteCore.java`
  - change default matrix planner wiring away from temporary pairwise execution.
  - preserve existing request validation and response mapping contracts.
- `src/main/java/org/Aayush/routing/core/MatrixResponse.java`
  - update implementation-note semantics from Stage 12 revisit marker to Stage 14 implementation descriptor.
- `src/test/java/org/Aayush/routing/core/RouteCoreTest.java`
  - replace `testMatrixWiringAndNote` expectations tied to `TemporaryMatrixPlanner.STAGE14_REVISIT_NOTE`.
  - add assertions for Stage 14 default planner behavior and deterministic matrix outputs.

Existing strengths to reuse:

- Stage 13 reusable internals: `SearchBudget`, `PlannerQueryContext`, `ReverseEdgeIndex`, `TerminationPolicy`, `PathEvaluator`.
- strict request validation and reason-coded exceptions in `RouteCore`.
- deterministic pinned-seed stress/perf harness style in `RouteCoreStressPerfTest`.

Current gap:

- matrix complexity is `O(|S| * |T| * route_search)` due full pairwise routing,
- no row-level early termination when all targets settled,
- no matrix-specific budget surface or stress/perf acceptance gates,
- revisit note still present in matrix response path.

## 4. Stage 14 Target Design

## 4.1 Core Planner Architecture

Introduce a native planner:

- `OneToManyDijkstraMatrixPlanner implements MatrixPlanner`

Planned supporting internals:

- `MatrixQueryContext` (thread-confined reusable row buffers/frontier/label state)
- `MatrixSearchBudget` (deterministic per-row + per-request limits)
- `MatrixTargetIndex` (deduplicated target-node lookup + output remap)
- `MatrixDeterminism` helper for fixed tie-break ordering and canonical unreachable fill

Keep API-level contracts unchanged:

- `MatrixRequest` and `MatrixResponse` field shapes remain unchanged,
- `RouteCore.builder().matrixPlanner(...)` injection seam remains available.

## 4.2 Algorithm Shape (Per Source Row)

Use one edge-based TD-Dijkstra expansion per unique source node:

1. Initialize frontier from source outgoing edges.
2. Expand states with deterministic ordering:
   - priority (`g`), then arrival ticks, then edge id, then label id.
3. Update best-known result when a target node is reached.
4. Early terminate row when:
   - all deduplicated targets have finite best result, and
   - next frontier priority cannot improve any remaining target best cost.
5. Fill unresolved targets as unreachable with canonical sentinels.

This preserves Stage 10 semantics by reusing exact transition cost evaluation (`CostEngine`) and edge-based dominance rules.

## 4.3 Compatibility Strategy for `MatrixRequest.algorithm`

To avoid external contract break while delivering Stage 14 performance:

- `DIJKSTRA + NONE`: always execute native Stage 14 one-to-many planner.
- `A_STAR` requests: keep compatibility mode using current pairwise route execution initially.

Rationale:

- Stage 14 SSOT scope is explicitly Dijkstra matrix.
- preserves current client behavior while enabling high-performance matrix mode now.
- keeps a safe path for future optional multi-target A* acceleration under strict parity gates.

## 4.4 Deterministic Output Rules

For identical model + overlay snapshot + request:

- `reachable[][]`, `totalCosts[][]`, `arrivalTicks[][]` must be byte-equivalent across runs.
- unreachable cells are canonicalized as:
  - `reachable=false`
  - `totalCost=Float.POSITIVE_INFINITY`
  - `arrivalTicks=departureTicks`
- duplicate source/target ids return identical cell values in all duplicate positions.

## 4.5 Observability and Reporting

Add matrix-run telemetry at test/report level (not public API changes required):

- per-row settled/work states,
- frontier peak,
- label peak,
- request-level wall time,
- heap start/peak/end deltas.

Stage artifact to produce on completion:

- `docs/stage14_delta_report.md` with pinned-seed comparison:
  - Stage 12 temporary pairwise baseline vs Stage 14 native one-to-many.

## 5. Performance Plan

## 5.1 High-Performance Execution Tactics

- run one search per unique source instead of per source-target pair,
- deduplicate targets for termination tracking while preserving output layout,
- reuse row buffers via thread-confined context to reduce GC churn,
- apply deterministic budgets to prevent pathological frontier growth,
- short-circuit completed rows immediately after global target completion.

## 5.2 Performance Acceptance Gates (Mandatory)

Pinned deterministic workload (documented in test and report):

- fixture: deterministic grid fixture (18x18 or larger),
- workload: mixed matrix shapes with target-heavy rows,
- seed: fixed and documented in report.

Hard pass thresholds for native Dijkstra matrix mode:

- wall-clock throughput: at least `3.0x` faster than Stage 12 pairwise baseline.
- p95 settled/work states per matrix cell: at most `70%` of baseline.
- heap peak delta per matrix cell: at most `60%` of baseline.
- zero correctness regressions while meeting perf thresholds.

## 6. Stress and Durability Plan

Mandatory stress classes:

- high-cardinality matrix requests (large source/target sets),
- mixed reachable/unreachable graphs,
- repeated duplicate source/target ids,
- concurrent identical matrix requests (determinism under threads),
- heavy live-overlay churn with expiries while matrix queries execute.

Failure handling must be deterministic and reason-coded:

- row/request budget exceeded,
- numeric safety breach (non-finite/negative priority),
- structural/path invariant breach (if encountered in replay/reconstruction helpers).

## 7. Correctness, Completeness, and Admissibility Plan

## 7.1 Correctness and Completeness

For every matrix cell `(s, t)` in pinned suites:

- result must match pairwise Dijkstra oracle:
  - reachability equality,
  - cost equality within `1e-5f`,
  - arrival equality for reachable pairs.

Completeness requirement:

- every requested pair must always produce one deterministic triple `(reachable, cost, arrival)`.
- no null rows, ragged arrays, or partially written cells.

## 7.2 Admissibility and Parity Envelope

Stage 14 native mode is Dijkstra and therefore exact by construction.

Admissibility/parity must still be enforced for compatibility and future extension:

- `A_STAR` matrix compatibility mode must keep zero mismatches vs pairwise Dijkstra oracle on pinned randomized suites.
- if a future optimized matrix A* mode is introduced, it cannot ship without zero mismatch against native Dijkstra matrix oracle under fixed snapshots.

## 8. Test and Validation Plan

## 8.1 Functional Tests

Add `OneToManyDijkstraMatrixPlannerTest` to cover:

- single-target equivalence with route query,
- many-target behavior with early termination,
- unreachable target handling,
- duplicate source/target mapping correctness,
- deterministic tie-break outcomes on equal-cost plateaus.

## 8.2 RouteCore Contract Tests

Update/extend `RouteCoreTest`:

- Stage 14 implementation note expectations,
- matrix defensive-copy behavior unchanged,
- custom matrix planner injection remains functional,
- validation behavior for source/target/algorithm/heuristic remains explicit.

## 8.3 Stress/Perf/Concurrency Tests

Extend `RouteCoreStressPerfTest` with Stage 14 gates:

- matrix random stress workload,
- matrix perf smoke for target-heavy shapes,
- concurrent deterministic matrix replay,
- live-overlay churn matrix parity sampling,
- pinned Stage12-vs-Stage14 delta gate with telemetry output.

## 9. Implementation Sequence

1. Add native planner and matrix query context internals.
2. Implement one-to-many row engine with deterministic ordering.
3. Add matrix-specific budgets and fail-fast reason codes.
4. Wire native planner as default for `DIJKSTRA + NONE` in `RouteCore`.
5. Keep `A_STAR` compatibility path via pairwise mode initially.
6. Replace Stage 14 revisit note with concrete Stage 14 implementation note.
7. Add functional + parity + stress/perf tests.
8. Produce `docs/stage14_delta_report.md` from pinned deterministic run.
9. Remove/retire temporary planner only after all hard gates pass.

## 10. Stage 14 Completion Scheme (Hard Exit Gates)

Stage 14 is complete only if every gate below is green.

### H14-FUNC (Functional)

- `H14-FUNC-01`: one-to-many single-target equals route result.
- `H14-FUNC-02`: many-target matrix outputs are dimensionally correct and complete.
- `H14-FUNC-03`: unreachable sentinel behavior is canonical and deterministic.
- `H14-FUNC-04`: custom matrix planner injection seam remains supported.

### H14-CORR (Correctness and Parity)

- `H14-CORR-01`: zero DIJKSTRA matrix mismatches vs pairwise Dijkstra oracle on pinned suites.
- `H14-CORR-02`: `A_STAR` compatibility matrix mode keeps zero mismatches vs Dijkstra oracle on pinned suites.
- `H14-CORR-03`: deterministic repeat runs are byte-equivalent for fixed snapshots.

### H14-COMPLETE (Completeness)

- `H14-COMPLETE-01`: every requested cell is populated exactly once with deterministic values.
- `H14-COMPLETE-02`: no partial-row failure or ragged output under stress workloads.
- `H14-COMPLETE-03`: duplicate ids map to identical output cells deterministically.

### H14-PERF (High Performance)

- `H14-PERF-01`: throughput improvement >= `3.0x` vs Stage 12 pairwise baseline on pinned target-heavy workload.
- `H14-PERF-02`: p95 settled/work states per cell <= `0.70x` baseline.
- `H14-PERF-03`: heap peak delta per cell <= `0.60x` baseline.

### H14-STRESS (Durability)

- `H14-STRESS-01`: concurrency determinism passes under multi-thread repeated matrix calls.
- `H14-STRESS-02`: live-overlay churn scenario keeps oracle parity and deterministic replay.
- `H14-STRESS-03`: deterministic reason-coded fail-fast behavior under enforced budget limits.

### H14-DOC (Evidence and Handoff)

- `H14-DOC-01`: `docs/stage14_delta_report.md` committed with pinned seed/workload and measured metrics.
- `H14-DOC-02`: `docs/taro_v11_ssot_stagewise_breakdown.md` Stage 14 status updated from planned to implemented.
- `H14-DOC-03`: Stage 12 revisit note retired from default response path.

## 11. Future Stage Readiness (15-18, 25-26, 27-28)

Stage 14 deliverable should leave clean seams for:

- Stage 15 addressing trait: planner consumes normalized internal ids only.
- Stage 16 temporal trait: matrix engine remains tick-contract compliant, trait logic stays above planner.
- Stage 17 transition trait: edge-based transition semantics remain isolated in cost/planner internals.
- Stage 18 registry: matrix execution mode can be selected through trait/config composition later.
- Stage 26 API: stable matrix schema while implementation evolves behind `MatrixPlanner` seam.
- Stage 27/28 ops loop: matrix telemetry keys can feed parity/perf dashboards and release gates.

## 12. Risks and Mitigations

- Risk: time-dependent edge-label dominance mistakes under one-to-many mode.
  - Mitigation: oracle parity tests against pairwise Dijkstra on pinned randomized suites.

- Risk: performance gains regress on sparse target sets.
  - Mitigation: include mixed-shape workloads and retain heuristic threshold where pairwise fallback can be selected by policy if needed.

- Risk: budget tuning too strict or too lax.
  - Mitigation: expose deterministic stage14 budget properties and include bounded defaults with stress calibration.

- Risk: contract drift while removing legacy implementation note.
  - Mitigation: maintain `MatrixResponse` shape unchanged; only update implementation note semantics.

## 13. Definition of Done Summary

Stage 14 is considered closed only when:

- native one-to-many Dijkstra matrix path is active for DIJKSTRA matrix requests,
- all H14 hard exit gates pass,
- deterministic delta report is published,
- legacy temporary path is no longer default and revisit note is retired.
