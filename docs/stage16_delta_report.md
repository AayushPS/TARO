# Stage 16 Delta Report

Date: 2026-02-22  
Stage: 16 (Temporal Trait)  
Status: Implemented and Revalidated  
Owner: TARO runtime team  
Commit Range: working-tree (not yet committed)

## 1. Scope Summary

- Stage 16 temporal trait runtime is bound once at startup via `TemporalRuntimeBinder`.
- Runtime supports `LINEAR` and `CALENDAR` strategies with explicit timezone policy in calendar mode.
- Route and matrix request payloads remain unchanged (no per-request temporal-mode switching).
- Temporal-mode determinism and Stage 13/14 planner seams remain intact.

## 2. Requirement Coverage (`R16-*`)

| Requirement ID | Description | Implementation Evidence | Test Evidence | Status |
|---|---|---|---|---|
| R16-01 | Support temporal traits `LINEAR` and `CALENDAR` | `TemporalTraitCatalog`, `TemporalStrategyRegistry` | `TemporalTraitCatalogTest`, `TemporalStrategyRegistryTest` | Pass |
| R16-02 | Resolve bucket/day selection per selected trait policy | `LinearTemporalResolutionStrategy`, `CalendarTemporalResolutionStrategy` | `Stage16TemporalTraitTest`, `CalendarTemporalResolutionStrategyTest` | Pass |
| R16-03 | Respect explicit timezone policy semantics | `TemporalTimezonePolicyRegistry`, `TemporalRuntimeBinder` | `TemporalTimezonePolicyRegistryTest`, `TemporalRuntimeBinderTest` | Pass |
| R16-04 | Preserve low-latency translation in hot path | pre-bound resolver + offset cache | `Stage16TemporalTraitStressPerfTest#testStage16RoutePerfSmokeCalendarVsLinear`, `testStage16MatrixPerfSmokeCalendarVsLinear` | Pass |
| R16-05 | Deterministic DST/timezone behavior | `TemporalOffsetCache`, `CalendarTemporalResolutionStrategy` | `Stage16TemporalTraitTest#testModelTimezoneDstBoundaryDeterminism`, `TemporalOffsetCacheTest` | Pass |
| R16-06 | Stateless deterministic request-path behavior | immutable context binding in `RouteCore` | Stage16 route/matrix concurrency determinism tests | Pass |
| R16-07 | Deterministic startup failure mapping | reason-coded binder/policy validation | `TemporalRuntimeBinderTest` deterministic `H16_*` assertions | Pass |
| R16-08 | Preserve Stage 13/14 planner contracts | normalized internal request propagation | full suite + `RouteCoreStressPerfTest` delta reports | Pass |
| R16-09 | Cover linear periodic, calendar weekday/weekend, DST boundaries | strategy and route-level tests | `Stage16TemporalTraitTest`, `TemporalOffsetCacheTest`, `CalendarTemporalResolutionStrategyTest` | Pass |
| R16-10 | Keep Stage 16 bounded from Stage 17/18 concerns | no transition-trait or global trait registry changes | architecture/code review + passing existing stage suites | Pass |

## 3. H16 Reason-Code Coverage

Validated in tests:

- `H16_TEMPORAL_CONFIG_REQUIRED`
- `H16_UNKNOWN_TEMPORAL_TRAIT`
- `H16_UNKNOWN_TEMPORAL_STRATEGY`
- `H16_TIMEZONE_POLICY_REQUIRED`
- `H16_UNKNOWN_TIMEZONE_POLICY`
- `H16_TIMEZONE_POLICY_NOT_APPLICABLE`
- `H16_MODEL_TIMEZONE_REQUIRED`
- `H16_INVALID_MODEL_TIMEZONE`
- `H16_TEMPORAL_CONFIG_INCOMPATIBLE`
- `H16_TEMPORAL_RESOLUTION_FAILURE`

## 4. Hardening Changes from Revalidation

- Hardened `TemporalRuntimeBinder` to reject null zone from timezone policy in calendar mode and to map unexpected timezone-policy runtime failures deterministically to `H16_TEMPORAL_CONFIG_INCOMPATIBLE`.
- Hardened `TemporalContextResolver` constructor:
  - day-mask-aware strategy now requires non-null `zoneId`.
  - `offsetCache` cannot be bound without `zoneId`.

## 5. Perf and Stress Evidence

- Added Stage 16 route perf smoke (`CALENDAR` vs `LINEAR`) with bounded regression assertions.
- Added Stage 16 matrix perf smoke (`CALENDAR` vs `LINEAR`) with parity and bounded regression assertions.
- Existing Stage 16 route and matrix concurrency-determinism tests remain green.

## 6. Coverage Evidence (Temporal Package)

From `target/site/jacoco/jacoco.csv` after full suite:

- Branch coverage: `132 / 134` (`98.51%`)
- Line coverage: `405 / 407` (`99.51%`)
- Instruction coverage: `1513 / 1516` (`99.80%`)

All temporal classes are at 100% branch coverage except `TemporalOffsetCache`.

## 7. Residual Untested Branches (Non-essential)

Remaining branch misses are in `TemporalOffsetCache`:

- `TemporalOffsetCache.java:69` (`guard < MAX_TRANSITIONS_PER_DAY`) missing path where a single local day has more than the guard limit of transitions while still inside the day-window loop.
- `TemporalOffsetCache.java:75` (`transitionEpoch > windowStart`) missing path where `nextTransition()` returns an instant equal to current window start.

Rationale:

- `ZoneRules#nextTransition(instant)` provides strictly subsequent transitions, making equality path non-observable under standard `ZoneId` rules.
- synthetic injection of custom `ZoneId` rules is constrained in JDK 21 (`ZoneId` is sealed), so this is not practical in normal unit-test contracts.

## 8. Full Regression Status

- Full suite executed: `mvn test -DskipITs`
- Result: `451 tests`, `0 failures`, `0 errors`, `0 skipped`.

## 9. Closure Checklist

- [x] `R16-*` requirements mapped to passing tests.
- [x] All `H16_*` reason codes covered.
- [x] Stage 13/14 parity and stress suites remain green.
- [x] Concurrency determinism (route + matrix) validated.
- [x] Perf/stress smoke for Stage 16 calendar/linear modes validated.
- [x] Coverage expanded; only non-essential temporal branches remain.
