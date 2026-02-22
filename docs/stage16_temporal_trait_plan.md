# Stage 16 Temporal Trait Plan (Hardened, Locked Runtime Mode, Strategy-Based)

Date: 2026-02-21  
Status: Implemented and Revalidated (2026-02-22)  
Scope: Implement Stage 16 temporal trait as a deterministic strategy layer with two temporal strategies (`LINEAR`, `CALENDAR`) and explicit timezone policy/DST-safe behavior, with one-time runtime selection and no request-time defaults.

## 1. Scope Boundary

Stage 16 includes:

- temporal-axis strategy interfaces and built-in strategy implementations,
- one-time temporal runtime configuration at `RouteCore` construction,
- deterministic day/bucket/fractional-bucket derivation for `CostEngine`,
- explicit timezone-policy enforcement for calendar behavior,
- deterministic `H16_*` validation mapping,
- temporal-only extensibility seam for Stage 18 composition.

Stage 16 excludes:

- transition trait execution changes (`NodeBased`/`EdgeBased`) (Stage 17),
- global trait-bundle registry/composition authority (Stage 18),
- HTTP/API v1 transport rollout (Stage 26),
- offline profile generation/compression/learning logic (Stages 21-24, 27-28).

## 2. Decision Freeze (User-Confirmed)

Locked decisions for this plan:

1. No temporal defaults in request path.
2. Temporal behavior is chosen once at runtime initialization and then locked for that `RouteCore` instance.
3. `LINEAR` ignores day masks and weekday gating completely.
4. Temporal metadata remains internal in Stage 16 (no response contract expansion required).
5. The two temporal modes are implemented as strategies behind one runtime strategy interface for extensibility.
6. `CALENDAR + MODEL_TIMEZONE` must fail startup when `metadata.profile_timezone` is missing or invalid.

Implementation interpretation:

- operator/deployer chooses temporal trait mode once (`LINEAR` or `CALENDAR`) with required timezone policy,
- all requests on that runtime follow the same locked temporal mode,
- request payload does not control temporal trait switching in Stage 16 baseline.

Weakness fixes applied in this revision:

1. Removed ambiguous runtime-lock error semantics from request path; lock now happens only at startup bind.
2. Added explicit startup binding architecture (trait -> strategy -> resolver) to avoid mixed responsibilities.
3. Added hot-path performance constraints (allocation and latency) to prevent timezone regressions.
4. Added deterministic cache design requirement for `CALENDAR + MODEL_TIMEZONE`.
5. Added rollout/canary and rollback sequencing so Stage 16 adoption is operationally safe.

## 3. Requirement Trace Baseline

| ID | Requirement | Source Anchor |
|---|---|---|
| R16-01 | Support temporal traits `LINEAR` and `CALENDAR`. | SSOT `FR-016`; stagewise Stage 16 functional requirements |
| R16-02 | Resolve bucket/day selection per selected temporal trait policy. | Stagewise Stage 16 functional requirements |
| R16-03 | Enforce explicit timezone policy for calendar behavior. | SSOT `FR-016`, `NFR-008` |
| R16-04 | Preserve low-latency translation in hot path. | Stagewise Stage 16 non-functional requirements |
| R16-05 | Preserve deterministic DST/timezone behavior. | Stagewise Stage 16 non-functional requirements |
| R16-06 | Keep stateless deterministic request-path behavior. | Stagewise Stage 16 non-functional requirements; SSOT `NFR-005` |
| R16-07 | Preserve Stage 13/14 parity and correctness contracts. | SSOT `FR-014`, `NFR-007` |
| R16-08 | Provide deterministic validation and reason-code mapping. | SSOT determinism contract |
| R16-09 | Cover linear periodic, calendar weekday/weekend, DST boundaries. | Stagewise Stage 16 equivalence classes |
| R16-10 | Keep Stage 16 bounded from Stage 17/18 concerns. | SSOT stage interface model |

## 4. Locked Architecture Decisions

1. Temporal runtime config is mandatory at `RouteCore.builder()` time.
2. No temporal defaults are inferred from missing request fields.
3. Runtime temporal mode is immutable for lifecycle of that `RouteCore` instance.
4. `CALENDAR` requires explicit timezone policy (`UTC` or `MODEL_TIMEZONE` baseline set).
5. `CALENDAR + MODEL_TIMEZONE` enforces strict startup validation of `metadata.profile_timezone` (no fallback to UTC).
6. `LINEAR` rejects timezone policy as not applicable.
7. Existing `departureTicks` semantics remain unchanged.
8. Temporal resolution is dispatched through strategy interface (`LINEAR` and `CALENDAR` implementations).
9. Planner search semantics (dominance, tie-break, termination) remain unchanged.
10. Stage 16 introduces `H16_*` reason codes only for temporal-domain failures.
11. Startup performs one-time trait/strategy binding via dedicated binder object; request path never rebinds.
12. `LINEAR` and `CALENDAR + UTC` paths must remain allocation-free in steady-state cost evaluation.
13. `CALENDAR + MODEL_TIMEZONE` path must use deterministic offset caching keyed by epoch-day.

## 5. Compatibility and Migration Contract

| Stage | Temporal Selection Model | Behavior |
|---|---|---|
| Stage 15 | implicit baked behavior | no trait selection |
| Stage 16 | one-time runtime lock | explicit `LINEAR` or `CALENDAR` selection |

Migration rules:

1. Runtime boot must fail fast if temporal runtime config is missing.
2. Existing request contracts remain stable; no temporal selector fields required in requests.
3. First migration profile should typically be `CALENDAR + UTC` for nearest behavioral continuity.
4. Switching to `LINEAR` is an explicit runtime-level choice, not a per-request override.

## 6. Planned API and Type Changes

1. Add temporal trait package:
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalTrait.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalTraitCatalog.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalResolutionStrategy.java`
- `src/main/java/org/Aayush/routing/traits/temporal/LinearTemporalResolutionStrategy.java`
- `src/main/java/org/Aayush/routing/traits/temporal/CalendarTemporalResolutionStrategy.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalStrategyRegistry.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinder.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalContextResolver.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalOffsetCache.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalTimezonePolicy.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalTimezonePolicyRegistry.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalPolicy.java`
- `src/main/java/org/Aayush/routing/traits/temporal/ResolvedTemporalContext.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalTelemetry.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalRuntimeConfig.java`
2. Extend `RouteCore.Builder` with mandatory temporal runtime dependencies/config:
- `temporalRuntimeConfig`,
- optional custom temporal catalog/policy/strategy-registry injections.
3. Keep `RouteRequest` and `MatrixRequest` unchanged in Stage 16 baseline (no temporal selector fields required).
4. Extend internal normalized requests:
- `src/main/java/org/Aayush/routing/core/InternalRouteRequest.java`
- `src/main/java/org/Aayush/routing/core/InternalMatrixRequest.java`
with `ResolvedTemporalContext`.
5. Extend `CostEngine` with temporal-context-aware overloads while keeping compatibility overloads:
- `computeEdgeCost(..., ResolvedTemporalContext temporalContext)`
- `explainEdgeCost(..., ResolvedTemporalContext temporalContext)`
6. Extend metadata/time utilities:
- `src/main/java/org/Aayush/serialization/flatbuffers/ModelContractValidator.java` (timezone parsing/validation helpers),
- `src/main/java/org/Aayush/core/time/TimeUtils.java` (timezone-aware local day/bucket/fractional helpers).
7. Update temporal-context callsites:
- `src/main/java/org/Aayush/routing/core/EdgeBasedRoutePlanner.java`
- `src/main/java/org/Aayush/routing/core/BidirectionalTdAStarPlanner.java`
- `src/main/java/org/Aayush/routing/core/OneToManyDijkstraMatrixPlanner.java`
- `src/main/java/org/Aayush/routing/core/PathEvaluator.java`
- `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`

Binding model:

- startup: `TemporalRuntimeBinder` validates config, resolves trait, resolves strategy, resolves timezone policy, builds immutable `TemporalContextResolver`,
- request path: `RouteCore` attaches this pre-bound resolver/context to internal requests,
- hot path: `CostEngine` consumes pre-bound resolver, avoiding strategy lookup per edge expansion.

## 7. Deterministic Validation and `H16_*` Codes

Planned reason-code set:

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

Compatibility rule:

- existing `H12_*` and `H15_*` mappings remain unchanged,
- temporal failures are wrapped deterministically (no raw Java time exceptions exposed).

## 8. Temporal Behavior Contract

### 8.1 `LINEAR`

- periodic bucket mapping from normalized ticks,
- day masks are ignored,
- weekday/weekend distinction is ignored,
- supports both discrete and interpolated sampling.

### 8.2 `CALENDAR`

- timezone-aware local day-of-week + local bucket mapping,
- day-mask-aware profile resolution via `ProfileStore`,
- inactive-day fallback remains deterministic (`DEFAULT_MULTIPLIER`).

### 8.3 DST Determinism

- instant-to-local conversion uses deterministic zone rules,
- spring-forward skipped hour and fall-back repeated hour are valid deterministic states,
- required boundary tests include:
- `2026-03-08` (`America/New_York`, spring-forward),
- `2026-11-01` (`America/New_York`, fall-back).

## 9. Integration Contract

### 9.1 Boot-Time Flow

1. validate temporal runtime config,
2. resolve temporal strategy + timezone policy once,
3. build locked temporal context template,
4. build immutable `TemporalContextResolver` for cost-engine hot path.

### 9.2 Request-Time Flow

1. validate algorithm/heuristic constraints,
2. normalize Stage 15 addressing,
3. attach locked temporal context,
4. execute planner with unchanged search semantics,
5. map response and telemetry.

Invariants:

- temporal mode does not vary per request within same runtime instance,
- planners still consume normalized internal ids and departure ticks,
- Stage 13/14 parity remains a hard release gate.

## 10. Implementation Units (U0-U13)

1. `U0 - Decision freeze`: lock one-time temporal runtime selection contract.
2. `U1 - Temporal core strategy types`: add strategy interface, built-in strategies, and registry; wire trait-to-strategy mapping.
3. `U2 - Startup binder`: implement `TemporalRuntimeBinder` and strict startup validation/fail-fast mapping.
4. `U3 - Time utilities`: add timezone-aware day/bucket/fractional helpers in `TimeUtils`.
5. `U4 - Metadata timezone validation`: add deterministic timezone parsing helpers in `ModelContractValidator`.
6. `U5 - Hot-path resolver + cache`: implement immutable resolver + deterministic epoch-day offset cache.
7. `U6 - CostEngine extension`: add temporal-context-aware scalar/explain paths consuming pre-bound resolver.
8. `U7 - Internal request propagation`: thread `ResolvedTemporalContext` through internal request models.
9. `U8 - RouteCore integration`: lock temporal context/resolver once, apply on every request.
10. `U9 - Planner/replay callsite updates`: pass temporal context to all cost calls and replay.
11. `U10 - Unit tests`: temporal strategy/binder/policy/timezone/cache validation tests.
12. `U11 - Contract tests`: route/matrix deterministic behavior + reason-code assertions.
13. `U12 - Stress/perf/parity`: concurrency determinism + DST boundary + Stage13/14 non-regression.
14. `U13 - Rollout + closure`: canary rollout checks, rollback validation, delta report, README/SSOT update.

## 11. Test Matrix and Target Files

New test classes:

- `src/test/java/org/Aayush/routing/core/Stage16TemporalTraitTest.java`
- `src/test/java/org/Aayush/routing/core/Stage16TemporalTraitStressPerfTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalTraitCatalogTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalStrategyRegistryTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinderTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalOffsetCacheTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalTimezonePolicyRegistryTest.java`
- `src/test/java/org/Aayush/routing/traits/temporal/TemporalPolicyTest.java`

Existing tests to extend:

- `src/test/java/org/Aayush/core/time/TimeUtilsTest.java`
- `src/test/java/org/Aayush/routing/cost/CostEngineTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreTest.java`
- `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java`
- `src/test/java/org/Aayush/serialization/flatbuffers/ModelContractValidatorTest.java`

Mandatory equivalence classes:

- boot-time missing temporal config rejection,
- unknown trait/policy rejection at boot,
- unknown strategy id rejection at boot,
- incompatible trait/policy combination rejection at boot,
- linear ignores day-mask behavior,
- calendar weekday/weekend day-mask behavior,
- calendar `UTC` vs `MODEL_TIMEZONE` policy behavior,
- DST boundaries (`2026-03-08`, `2026-11-01`) deterministic replay,
- legacy request-shape compatibility (no request payload change),
- route and matrix concurrency determinism under locked temporal mode.

## 12. Quantitative Exit Gates (Hard DoD)

Stage 16 closes only when all pass:

1. `R16-*` requirements mapped to explicit passing tests.
2. All planned `H16_*` codes covered with deterministic assertions.
3. Stage 13/14 parity mismatch count remains `0` on pinned suites.
4. Repeated and concurrent identical requests are byte-equivalent for fixed snapshot.
5. Performance regression budget:
- locked `CALENDAR + UTC` profile: p95 route latency regression <= `8%`, p95 matrix <= `10%` vs Stage 15 baseline.
- locked `LINEAR` profile: p95 route latency regression <= `10%`, p95 matrix <= `12%` on matched workloads.
6. Allocation regression budget:
- locked `LINEAR` and `CALENDAR + UTC`: per-route allocation delta <= `5%` vs Stage 15 baseline.
- `CALENDAR + MODEL_TIMEZONE`: per-route allocation delta <= `12%` with offset cache enabled.
7. `docs/stage16_delta_report.md` published with perf/parity/allocation evidence and rollback notes.

## 13. Rollback and Risk Control

Primary risks:

- timezone conversion overhead in hot path,
- unintended behavior shift from previous implicit semantics,
- model timezone metadata inconsistency.

Mitigations:

- explicit one-time runtime selection (no implicit defaults),
- strict boot-time config and timezone validation,
- startup binder isolates config errors before serving traffic,
- maintain compatibility overloads in `CostEngine`,
- deterministic offset cache for model-timezone path,
- keep deterministic regression suite as release gate.

Rollback target:

- revert to prior Stage 15 deployment artifact/config if Stage 16 gates fail.

Rollout sequence:

1. run Stage 16 with `CALENDAR + UTC` in canary,
2. compare parity/latency/allocation gates to Stage 15 baseline,
3. enable `CALENDAR + MODEL_TIMEZONE` only after startup-validation and DST suites are green.

## 14. Stage 17 Forward Readiness

Stage 16 must preserve:

1. transition context orthogonality from temporal context,
2. independently testable temporal vs transition contributions in `CostEngine`,
3. stable planner interfaces after Stage 16 integration,
4. reason-code namespace isolation (`H16_*` vs future `H17_*`).

## 15. Final Policy Lock

Calendar + model-timezone mode uses strict startup enforcement:

- missing `metadata.profile_timezone` => startup failure (`H16_MODEL_TIMEZONE_REQUIRED`),
- invalid `metadata.profile_timezone` => startup failure (`H16_INVALID_MODEL_TIMEZONE`),
- no permissive fallback to UTC in this mode.
