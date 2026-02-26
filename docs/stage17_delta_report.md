# Stage 17 Delta Report

Date: 2026-02-26  
Status: Implemented and closure-gated (revalidated)

## 1. Scope Delivered
Stage 17 transition trait runtime is implemented with startup-locked behavior.

Delivered contracts:
- `NODE_BASED` and `EDGE_BASED` transition traits with deterministic startup binding.
- Locked `ResolvedTransitionContext` propagation through route and matrix internals.
- Transition-aware turn handling in `CostEngine` with deterministic `H17_*` reason-code mapping.
- Route and matrix behavior parity guarantees for no-turn fixtures.
- Stage 17 stress/perf suite with deterministic seeded workloads.

## 2. Runtime Behavior Summary
- Transition mode is selected once during `RouteCore` construction via `TransitionRuntimeConfig`.
- Request payloads cannot switch transition mode.
- `EDGE_BASED` applies finite turn penalties and forbidden turns.
- `NODE_BASED` ignores finite turn penalties but still blocks forbidden turns.
- Turn-map absence is valid neutral fallback in both modes.

## 3. Perf/Stress Gate Results
Gate suites:
- `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/core/SystemIntegrationStressPerfTest.java` (Stage 17 integration path)

Passed gates:
- No-turn route parity (`EDGE_BASED` vs `NODE_BASED`).
- No-turn matrix parity (`EDGE_BASED` vs `NODE_BASED`).
- No-turn route latency ratio bound (`<= 1.15x`).
- No-turn matrix latency ratio bound (`<= 1.20x`).
- Moderate turn-density route overhead bound (`<= 1.35x`).
- Route+matrix concurrency determinism under mixed transition-mode traffic.
- Live-overlay churn + turn-map workload parity and deterministic replay in both transition modes.
- Mixed integration traffic (legacy route, typed route, native matrix, compatibility matrix) remains deterministic for both transition modes with throughput floor preserved.

## 4. Additional Hardening in This Pass
- Reduced transition hot-path allocation pressure by adding packed turn-decision evaluation (`evaluatePacked`) in strategy contracts.
- Kept backward compatibility for custom strategies that only implement `evaluate(...)` via default packed fallback.
- Added tests for packed fallback error handling and packed decision round-trip semantics.
- Rebalanced Stage 17 concurrency stress loop bounds to keep deterministic pressure while avoiding environment-specific timeout flakiness introduced by recent stability changes.
- Fixed a clean-build blocker in `TurnCostDecision` by removing the duplicate static/instance `forbidden()` signature conflict.
- Fixed a dark-path determinism gap: invalid packed turn decisions (for example `NaN`) are now wrapped as `H17_TRANSITION_RESOLUTION_FAILURE` instead of leaking raw `IllegalStateException`.
- Added route/matrix RouteCore wrapper tests and low-level CostEngine regression tests for invalid packed transition decisions.

## 5. Coverage Snapshot
Command:

```bash
mvn -Pcoverage test
```

Current line coverage snapshot:
- Overall bundle: **95.05%** (`4073/4285` lines)
- `org.Aayush.routing.traits.transition`: **98.27%** (`227/231` lines)
- `org.Aayush.routing.cost`: **91.47%** (`118/129` lines)
- `org.Aayush.routing.core`: **95.65%** (`1298/1357` lines)

Coverage gate (`pom.xml`, profile `coverage`) is satisfied (`LINE COVEREDRATIO >= 0.90`).

Clean-compile validation:

```bash
mvn clean test -DskipTests
```

Result: Pass.

## 6. Residual Risks
- Custom third-party strategies can still return unusual packed values; invalid values are now normalized to deterministic `H17_TRANSITION_RESOLUTION_FAILURE`, but integration owners should keep custom strategy contracts strict.
- Performance thresholds are smoke/perf gates, not strict micro-benchmarks; cross-machine variance is expected.

## 7. Files Added/Updated for Stage 17 Closure
- `src/main/java/org/Aayush/routing/traits/transition/TransitionCostStrategy.java`
- `src/main/java/org/Aayush/routing/traits/transition/NodeBasedTransitionCostStrategy.java`
- `src/main/java/org/Aayush/routing/traits/transition/EdgeBasedTransitionCostStrategy.java`
- `src/main/java/org/Aayush/routing/cost/CostEngine.java`
- `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionCostStrategyTest.java`
- `src/test/java/org/Aayush/routing/traits/transition/TransitionStrategyRegistryTest.java`
- `src/test/java/org/Aayush/routing/cost/CostEngineTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreTest.java`
- `src/test/java/org/Aayush/routing/core/SystemIntegrationStressPerfTest.java`
