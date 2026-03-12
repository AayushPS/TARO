# Stage 18 Delta Report

Date: 2026-03-11  
Stage: 18 (Trait Registry and Configuration)  
Status: Implemented / Revalidated  
Owner: TARO runtime team  
Commit Range: working tree

## 1. Scope Summary

- Added startup bundle authority in `org.Aayush.routing.traits.registry` with named bundle lookup, inline bundle selection, deterministic dependency validation, and startup-only bundle binding.
- Locked addressing coordinate strategy at startup by extending `AddressingRuntimeConfig`/`AddressingRuntimeBinder` and moving `AddressingTraitEngine` off request-time strategy selection.
- Integrated bundle resolution, selector soft-cutover, selected-bundle telemetry/context exposure, and `H18_*` reason codes into `RouteCore`.
- Added Stage 18 unit and integration coverage while preserving Stage 13-17 non-regression suites and maintainability guardrails.

## 2. Requirement Coverage (`R18-*`)

| Requirement ID | Description | Implementation Evidence | Test Evidence | Status |
|---|---|---|---|---|
| R18-01 | Register trait bundles | `TraitBundleRegistry`, `TraitBundleSpec`, `TraitBundleRuntimeConfig`, `TraitBundleRuntimeBinder` | `TraitBundleRuntimeBinderTest.testNamedBundleResolution`, `Stage18TraitRegistryTest.testNamedBundleRouteAndMatrixSuccess` | Pass |
| R18-02 | Validate compatibility/dependencies | `TraitBundleCompatibilityPolicy`, composed Stage 15/16/17 binders, bundle/legacy conflict checks | `TraitBundleRuntimeBinderTest.testMissingCoordinateStrategyDependencyRejected`, `TraitBundleRuntimeBinderTest.testIncompatibleCoordinateStrategyRejected`, `TraitBundleRuntimeBinderTest.testBundlePathConflictsWithLegacyConfigs`, `Stage18TraitRegistryTest.testConstructorRejectsConflictingBundleAndLegacyConfigs` | Pass |
| R18-03 | Expose selected bundle | `ResolvedTraitBundleContext`, `TraitBundleTelemetry`, `RouteCore.traitBundleContextContract()`, `RouteCore.traitBundleTelemetryContract()` | `Stage18TraitRegistryTest.testNamedBundleRouteAndMatrixSuccess` | Pass |
| R18-04 | Fast startup validation | single startup bind via `TraitBundleRuntimeBinder` with no request-time rebinding | `TraitBundleRuntimeBinderTest` suite, full `mvn -Pcoverage test` pass | Pass |
| R18-05 | Deterministic diagnostics | `H18_*` reason-code mapping in `RouteCore` and bundle binder | `TraitBundleRuntimeBinderTest`, `Stage18TraitRegistryTest`, adjusted Stage 15 soft-cutover assertions | Pass |
| R18-06 | Stable trait hash | `TraitBundleHasher` canonical serializer + SHA-256, hash stored in bundle context/telemetry | `TraitBundleHasherTest.testTraitHashStableAcrossRepeatedAndConcurrentCalls`, `TraitBundleRuntimeBinderTest.testTraitHashFailureMapped` | Pass |
| R18-07 | Startup lock + no request switching | startup-bound coordinate strategy and bundle bind; request selectors downgraded to hints | `Stage18TraitRegistryTest.testSelectorMismatchRejected`, `Stage15AddressingTraitTest.testCoordinateStrategyIdRequiredForCoordinates` | Pass |
| R18-08 | Soft-cutover selector behavior | request selector hints accepted only on exact startup match | `Stage18TraitRegistryTest.testNamedBundleRouteAndMatrixSuccess`, `Stage18TraitRegistryTest.testSelectorMismatchRejected`, `Stage15AddressingTraitTest.testRequestTraitHintMustMatchStartupTrait` | Pass |

## 3. `H18_*` Coverage

Validated codes:

- H18_TRAIT_BUNDLE_CONFIG_REQUIRED
- H18_UNKNOWN_TRAIT_BUNDLE
- H18_TRAIT_BUNDLE_CONFIG_CONFLICT
- H18_TRAIT_BUNDLE_INCOMPATIBLE
- H18_MISSING_TRAIT_DEPENDENCY
- H18_TRAIT_HASH_GENERATION_FAILED
- H18_REQUEST_TRAIT_SELECTOR_MISMATCH

## 4. Runtime Behavior Evidence

- `RouteCore` now binds a `TraitBundleRuntimeBinder.Binding` at startup and exposes `ResolvedTraitBundleContext` / `TraitBundleTelemetry` alongside the existing Stage 15/16/17 runtime artifacts.
- Coordinate inputs are resolved only with the startup-bound `coordinateDistanceStrategyId`; request-time `coordinateDistanceStrategyId` no longer selects runtime behavior.
- Deprecated request selector fields are soft-cutover hints only: exact match proceeds, mismatch fails with `H18_REQUEST_TRAIT_SELECTOR_MISMATCH`.
- Bundle-path and legacy-path conflicts are rejected deterministically before serving starts.

## 5. Perf and Stress Evidence

- Full regression command: `mvn -Pcoverage test`
- Result: Pass.
- Existing Stage 13-17 perf/stress suites remained green under Stage 18 integration, including:
  - `Stage15AddressingTraitStressPerfTest`
  - `Stage16TemporalTraitStressPerfTest`
  - `Stage17TransitionTraitStressPerfTest`
  - `RouteCoreStressPerfTest`
  - `SystemIntegrationStressPerfTest`
- Maintainability guardrails remained green:
  - `RouteCore.java`: 730 lines (budget 750)
  - `AddressingTraitEngine.java`: 950 lines (budget 980)

## 6. Residual Risks

- Stage 18 package coverage is above the project gate but still tight relative to the new code surface; future registry growth should keep adding direct unit coverage instead of relying only on RouteCore integration tests.
- Bundle registry currently has no built-in curated bundles; operators must provide named or inline bundle selections explicitly.

## 7. Files Added/Updated

- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleSpec.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeConfig.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRegistry.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleCompatibilityPolicy.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleHasher.java`
- `src/main/java/org/Aayush/routing/traits/registry/ResolvedTraitBundleContext.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleTelemetry.java`
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeBinder.java`
- `src/main/java/org/Aayush/routing/core/RouteCore.java`
- `src/main/java/org/Aayush/routing/core/RouteRequest.java`
- `src/main/java/org/Aayush/routing/core/MatrixRequest.java`
- `src/main/java/org/Aayush/routing/traits/addressing/AddressingRuntimeConfig.java`
- `src/main/java/org/Aayush/routing/traits/addressing/AddressingRuntimeBinder.java`
- `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`
- `src/test/java/org/Aayush/routing/traits/registry/TraitBundleHasherTest.java`
- `src/test/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeBinderTest.java`
- `src/test/java/org/Aayush/routing/core/Stage18TraitRegistryTest.java`
- `src/test/java/org/Aayush/routing/core/Stage15AddressingTraitTest.java`

## 8. Closure Checklist

- [x] All `R18-*` mapped to passing tests.
- [x] All `H18_*` reason codes covered.
- [x] Startup bundle lock enforced.
- [x] No implicit defaults are used for bundle/trait selection.
- [x] No request-time trait switching.
- [x] Stage 13-17 suites remain green.
- [x] Stage 18 docs and status updates completed.

## 9. Coverage Snapshot

Command:

```bash
mvn -Pcoverage test
```

Current line coverage snapshot:
- Overall bundle: **93.72%** (`4777/5097` lines)
- `org.Aayush.routing.traits.registry`: **90.34%** (`290/321` lines)
- `org.Aayush.routing.core`: **95.66%** (`1434/1499` lines)
- `org.Aayush.routing.traits.addressing`: **91.17%** (`630/691` lines)

Coverage gate (`pom.xml`, profile `coverage`) remains satisfied.
