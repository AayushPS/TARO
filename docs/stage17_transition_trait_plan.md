# Stage 17 Transition Trait Implementation Plan (Integration-Ready, Perf/Stress-Gated)

Date: 2026-02-23  
Status: Planning Draft (aligned to current runtime shape)  
Scope: Implement startup-locked transition traits (`NODE_BASED`, `EDGE_BASED`) with deterministic validation, integrate them into the current Stage 15/16 runtime flow, and enforce explicit perf/stress non-regression gates.

## 1. Scope Boundary

Stage 17 includes:

- transition trait interfaces, built-in strategies, and startup binder,
- one-time transition-mode binding in `RouteCore` startup path,
- immutable `ResolvedTransitionContext` propagation through route and matrix internals,
- transition-aware turn-cost composition in `CostEngine`,
- deterministic `H17_*` reason-code mapping,
- integration tests and stress/perf gates wired into existing suites.

Stage 17 excludes:

- Stage 18 global trait bundle registry and trait-hash authority,
- Stage 15 addressing redesign and Stage 16 temporal redesign,
- API transport/versioning changes (Stage 26),
- offline builder/compiler work (Stages 19+).

## 2. Current System Snapshot (As-Implemented)

Current runtime already provides:

- startup-bound addressing runtime bind (`AddressingRuntimeBinder`) in `RouteCore`,
- startup-bound temporal runtime bind (`TemporalRuntimeBinder`) in `RouteCore`,
- `InternalRouteRequest`/`InternalMatrixRequest` carrying locked `ResolvedTemporalContext`,
- route and matrix execution via `EdgeBasedRoutePlanner`,
- route and matrix execution via `BidirectionalTdAStarPlanner`,
- route and matrix execution via `NativeOneToManyMatrixPlanner`,
- route and matrix execution via `TemporaryMatrixPlanner`,
- implicit transition behavior in `CostEngine` (turn penalty applied when turn map + predecessor edge exist).

Existing quality gates already in repo:

- `RouteCoreStressPerfTest` (Stage 13/14 parity/determinism/perf/stress),
- `Stage15AddressingTraitStressPerfTest`,
- `Stage16TemporalTraitStressPerfTest`,
- `SystemIntegrationStressPerfTest` (mixed route/matrix/addressing/temporal workload).

Primary Stage 17 gap:

- transition mode is not explicit and not startup-bound,
- no transition trait package/runtime binder exists,
- no `H17_*` failure namespace exists,
- no transition-specific perf/stress acceptance criteria are defined.

## 3. Locked Policy Decisions

1. Transition mode is system behavior, not request behavior.
2. Transition mode is selected once at `RouteCore` startup and immutable per instance.
3. `TurnCostMap` may be absent; absence is valid neutral fallback.
4. Forbidden turns must be blocked whenever turn data marks transition as forbidden.
5. Stage 17 must integrate with current Stage 15/16 runtime style, not bypass it.

## 4. Requirement Trace Baseline

| ID | Requirement | Source Anchor |
|---|---|---|
| R17-01 | Support `NODE_BASED` and `EDGE_BASED` transition traits. | SSOT `FR-017` |
| R17-02 | Apply finite turn penalties only when trait semantics require it. | SSOT `FR-005`, `FR-017` |
| R17-03 | Block forbidden turns deterministically. | Stagewise Stage 17 equivalence classes |
| R17-04 | Startup-only trait selection with no runtime ambiguity. | Stagewise Stage 17 NFR |
| R17-05 | Request payload cannot switch transition mode. | user lock + startup-lock principle |
| R17-06 | Turn-map absence remains valid neutral fallback. | user lock |
| R17-07 | Preserve Stage 13/14 route+matrix correctness/parity contracts. | SSOT `FR-014`, `NFR-007` |
| R17-08 | Add explicit perf and stress non-regression gates. | SSOT `NFR-001`, `NFR-005` |
| R17-09 | Keep Stage 17 bounded from Stage 18 registry authority. | SSOT stage interface model |

## 5. Startup Runtime Model

Stage 17 follows the Stage 16 binder pattern:

1. `RouteCore.builder()` accepts mandatory `TransitionRuntimeConfig`.
2. `TransitionRuntimeBinder` resolves trait and strategy once at startup.
3. Binder emits immutable `ResolvedTransitionContext` plus `TransitionTelemetry`.
4. Every route/matrix request on that `RouteCore` instance uses the same bound transition context.
5. Request contracts (`RouteRequest`, `MatrixRequest`) do not include `transitionTraitId`.

Builder additions:

- required: `transitionRuntimeConfig`,
- optional: `TransitionTraitCatalog`, `TransitionStrategyRegistry`, `TransitionPolicy`, `TransitionRuntimeBinder`.

## 6. Transition Package Design

Add package `src/main/java/org/Aayush/routing/traits/transition/`:

- `TransitionTrait.java`
- `TransitionTraitCatalog.java`
- `TransitionCostStrategy.java`
- `NodeBasedTransitionCostStrategy.java`
- `EdgeBasedTransitionCostStrategy.java`
- `TransitionStrategyRegistry.java`
- `TransitionPolicy.java`
- `TransitionRuntimeConfig.java`
- `TransitionRuntimeBinder.java`
- `ResolvedTransitionContext.java`
- `TransitionTelemetry.java`

Built-in catalog contract:

- `NODE_BASED` -> strategy `NODE_BASED`,
- `EDGE_BASED` -> strategy `EDGE_BASED`.

## 7. Transition Semantics Contract

`EDGE_BASED`:

- predecessor-edge transition semantics active,
- finite turn penalties applied when turn map exists and predecessor exists,
- forbidden transition (`+INF`) blocks expansion deterministically,
- turn-map absence remains neutral fallback (0 turn contribution).

`NODE_BASED`:

- finite turn penalties are ignored,
- forbidden transition (`+INF`) still blocks expansion when present in turn map,
- turn-map absence remains neutral fallback.

Safety invariant:

- no strategy may convert forbidden transition into finite cost.

## 8. File-by-File Integration Plan (Current System Aligned)

| Area | Files | Required Change |
|---|---|---|
| RouteCore runtime bind | `src/main/java/org/Aayush/routing/core/RouteCore.java` | add `H17_*`, bind transition runtime at startup, attach transition context to internal requests, expose transition telemetry contract |
| Internal requests | `src/main/java/org/Aayush/routing/core/InternalRouteRequest.java`, `src/main/java/org/Aayush/routing/core/InternalMatrixRequest.java` | add required `ResolvedTransitionContext transitionContext` |
| Cost composition | `src/main/java/org/Aayush/routing/cost/CostEngine.java` | add transition-context-aware compute/explain methods; keep base+temporal+live semantics unchanged |
| Route planners | `src/main/java/org/Aayush/routing/core/EdgeBasedRoutePlanner.java`, `src/main/java/org/Aayush/routing/core/BidirectionalTdAStarPlanner.java` | pass transition context at every cost call |
| Matrix planners | `src/main/java/org/Aayush/routing/core/NativeOneToManyMatrixPlanner.java`, `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java` | pass transition context in row expansion and pairwise fallback |
| Replay/eval | `src/main/java/org/Aayush/routing/core/PathEvaluator.java` | include transition context in replay cost calls |
| Test utility | `src/test/java/org/Aayush/routing/testutil/TemporalTestContexts.java` plus new transition equivalent | provide shared transition contexts for low-level tests |

No default transition-context fallback path is allowed in Stage 17 internals.

## 9. Deterministic Validation Contract (`H17_*`)

Add in `RouteCore`:

- `H17_TRANSITION_CONFIG_REQUIRED`
- `H17_UNKNOWN_TRANSITION_TRAIT`
- `H17_UNKNOWN_TRANSITION_STRATEGY`
- `H17_TRANSITION_CONFIG_INCOMPATIBLE`
- `H17_TRANSITION_RESOLUTION_FAILURE`

Explicit non-goal:

- no `H17_TURN_COST_MAP_REQUIRED` code; missing turn map is valid by policy.

## 10. Test Plan (Unit, Integration, Perf, Stress)

New test classes:

- `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitTest.java`
- `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionTraitCatalogTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionStrategyRegistryTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionRuntimeBinderTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionPolicyTest.java`

Existing suites to extend:

- `src/test/java/org/Aayush/routing/cost/CostEngineTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/SystemIntegrationStressPerfTest.java`

Mandatory equivalence classes:

- startup bind success for `NODE_BASED` and `EDGE_BASED`,
- startup failures for missing/unknown/incompatible config (`H17_*`),
- edge mode applies finite turn penalty while node mode does not,
- forbidden transition blocked in both modes when present in map,
- map-absent neutral fallback in both modes,
- route/matrix deterministic replay under concurrency for both modes.

## 11. Perf and Stress Gates (Hard)

Pinned-seed workloads must pass all gates:

- `H17-PERF-01`: in turn-map-absent fixture, `EDGE_BASED` and `NODE_BASED` produce identical route and matrix outputs.
- `H17-PERF-02`: route average latency in `EDGE_BASED` no-turn-map mode is within `1.15x` of `NODE_BASED` baseline on same workload.
- `H17-PERF-03`: matrix average latency in `EDGE_BASED` no-turn-map mode is within `1.20x` of `NODE_BASED` baseline.
- `H17-PERF-04`: moderate turn-density (`~10-20%` explicit turn entries) keeps route average latency within `1.35x` of no-turn `EDGE_BASED` baseline.
- `H17-STRESS-01`: route and matrix concurrency determinism holds for both transition modes.
- `H17-STRESS-02`: live-overlay churn plus turn-map workload preserves parity and deterministic replay.
- `H17-STRESS-03`: mixed integration traffic (legacy route, typed route, native matrix, compatibility matrix) remains deterministic and keeps throughput floor at or above current `SystemIntegrationStressPerfTest` gate.

All perf/stress workloads use deterministic seeds and bounded thread/loop profiles.

## 12. Implementation Units (Execution Sequence)

1. `U0 - Contract freeze`: lock semantics, codes, startup-only selection.
2. `U1 - Transition core types`: trait/strategy interfaces and built-ins.
3. `U2 - Catalog/registry`: immutable catalogs and strategy registry.
4. `U3 - Runtime config + binder`: deterministic startup bind and `H17_*` mapping.
5. `U4 - RouteCore startup integration`: bind once and store telemetry/context.
6. `U5 - Internal request propagation`: thread transition context through route/matrix records.
7. `U6 - CostEngine integration`: strategy hook for turn contribution and forbidden blocking.
8. `U7 - Planner and replay callsites`: pass transition context in all cost calls.
9. `U8 - Unit tests`: trait/catalog/registry/policy/binder coverage.
10. `U9 - Contract tests`: route/matrix correctness + `H17_*` assertions.
11. `U10 - Perf/stress`: deterministic non-regression gates across existing suites.
12. `U11 - Closure docs`: `docs/stage17_delta_report.md`, README/runtime status updates.

## 13. Migration and Rollout Guidance

1. Migration default for parity with existing runtime behavior is `EDGE_BASED`.
2. Update all `RouteCore.builder()` callsites (tests and examples) to provide `transitionRuntimeConfig`.
3. Deploy `EDGE_BASED` first in canary for behavior continuity.
4. Run full parity/perf/stress suite before widening rollout.
5. Run `NODE_BASED` only in explicit experiments until quality gates are stable.
6. Rollback target is previous Stage 16 build/config without Stage 17 transition binding.

## 14. Hard Exit Criteria (DoD)

Stage 17 closes only when:

1. all `R17-*` requirements map to passing tests,
2. all `H17_*` codes have deterministic test coverage,
3. startup lock and request-path non-switching are enforced,
4. forbidden-turn blocking and map-absent fallback both pass in route and matrix flows,
5. Stage 13/14/15/16 suites remain green with no correctness regressions,
6. Stage 17 perf/stress gates pass on pinned workloads,
7. `docs/stage17_delta_report.md` is published.

## 15. Stage 18 Forward Readiness

Stage 17 outputs must be composable for Stage 18:

- `ResolvedTransitionContext` can be bundled alongside addressing and temporal contexts,
- startup bind artifacts can participate in single trait-bundle resolution,
- request path remains free of trait-mode toggles.
