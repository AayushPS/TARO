# Stage 18 Trait Registry and Configuration Plan (Startup-Locked Bundle Authority)

Date: 2026-03-11  
Status: Refined to implemented baseline  
Scope: Stage 18 is the startup authority for trait composition across addressing, temporal, and transition axes, with deterministic compatibility validation, stable trait-hash generation, startup-only runtime binding, and soft-cutover request selector validation.

## 1. Scope Boundary

Stage 18 includes:

- global trait-bundle registry and startup bundle binding,
- cross-axis compatibility/dependency validation across Stage 15/16/17 traits,
- stable canonical trait-hash generation for lineage and telemetry,
- runtime exposure of selected bundle to internal components,
- deterministic `H18_*` rejection mapping,
- soft-cutover handling for deprecated request trait selector fields.

Stage 18 excludes:

- HTTP/API transport changes (Stage 26),
- offline compiler/export lineage wiring changes (Stages 19-24),
- telemetry ingestion persistence pipeline (Stage 27),
- hard removal of deprecated request trait selector fields (deferred post-cutover).

## 2. Requirement Trace Baseline

| ID | Requirement | Source Anchor |
|---|---|---|
| R18-01 | Register available trait implementations as bundle(s). | SSOT FR-018 |
| R18-02 | Validate trait compatibility and required dependencies. | Stagewise Stage 18 functional requirements |
| R18-03 | Expose selected trait bundle to runtime components. | Stagewise Stage 18 functional requirements |
| R18-04 | Fast startup-time validation. | Stagewise Stage 18 non-functional requirements |
| R18-05 | Clear deterministic rejection diagnostics. | Stagewise Stage 18 non-functional requirements |
| R18-06 | Stable trait-hash generation for lineage. | Stagewise Stage 18 non-functional requirements |
| R18-07 | Startup-bound trait lock with no request-time mode switching. | Trait runtime lock principle + Stage 16/17 model |
| R18-08 | Soft-cutover compatibility for request selector fields. | Stage 18 migration decision |

## 3. Locked Decisions

1. Trait composition is startup-only and immutable per `RouteCore`.
2. Stage 18 adds a bundle-level config path and binder.
3. Existing per-axis startup configs remain as compatibility fallback in Stage 18, but all axis values must be explicitly provided.
4. Request selector fields (`addressingTraitId`, `coordinateDistanceStrategyId`) are deprecated hints only.
5. Selector mismatch versus startup bundle hard-fails the query.
6. Addressing coordinate strategy becomes startup-bound via runtime config.
7. `allowMixedAddressing` and `maxSnapDistance` remain request-scoped.
8. Trait hash algorithm is fixed to SHA-256 over canonical UTF-8 config representation.
9. No implicit defaults are allowed for bundle or trait-axis selection; startup must receive explicit user/operator choices.
10. Existing axis-local missing-config diagnostics (`H15_*`, `H16_*`, `H17_*`) remain preserved on the legacy compatibility path; `H18_TRAIT_BUNDLE_CONFIG_REQUIRED` is used for invalid/empty Stage 18 bundle-path selection.

## 4. Planned API and Type Changes

### 4.1 New package

Add `src/main/java/org/Aayush/routing/traits/registry/`:

- `TraitBundleRuntimeConfig`
- `TraitBundleSpec`
- `TraitBundleRegistry`
- `TraitBundleCompatibilityPolicy`
- `TraitBundleHasher`
- `ResolvedTraitBundleContext`
- `TraitBundleTelemetry`
- `TraitBundleRuntimeBinder`

### 4.2 Existing type changes

- `RouteCore`:
  - add builder input `traitBundleRuntimeConfig`,
  - add optional bundle binder/registry/policy injections,
  - bundle path precedence over legacy per-axis path,
  - expose startup bundle telemetry/context contract.
- `AddressingRuntimeConfig`:
  - add startup coordinate strategy id support.
- `AddressingRuntimeBinder`:
  - bind startup coordinate strategy and validate availability.
- `AddressingTraitEngine`:
  - resolve coordinates with startup-bound strategy,
  - keep request selector fields as optional hints only.
- `RouteRequest` and `MatrixRequest`:
  - keep selector fields for soft cutover,
  - annotate/document as deprecated hint fields.

## 5. Startup Binding Precedence Contract

At `RouteCore` construction:

1. If `traitBundleRuntimeConfig` is present: use Stage 18 bundle path.
2. If bundle path and legacy per-axis configs are both provided: all legacy values must resolve to the same selection, else fail with `H18_TRAIT_BUNDLE_CONFIG_CONFLICT`.
3. Else if all legacy per-axis runtime configs are present: synthesize inline bundle and use Stage 18 binder.
4. Else preserve existing axis-local required-code failures (`H15_ADDRESSING_CONFIG_REQUIRED`, `H16_TEMPORAL_CONFIG_REQUIRED`, `H17_TRANSITION_CONFIG_REQUIRED`) instead of inventing an implicit bundle fallback.
5. Use `H18_TRAIT_BUNDLE_CONFIG_REQUIRED` only when the Stage 18 bundle path itself is empty/invalid (for example neither `traitBundleId` nor `inlineTraitBundleSpec` is supplied).
6. Never auto-select a bundle, trait, or strategy when startup config is missing.

## 6. Compatibility and Dependency Contract

Bundle validation checks:

1. Addressing trait exists in addressing catalog.
2. Temporal trait exists and maps to valid temporal strategy/policy.
3. Transition trait exists and maps to valid transition strategy.
4. Addressing coordinate-capable mode requires startup coordinate strategy id.
5. Coordinate strategy id must exist in coordinate registry.
6. External-id-only addressing must not carry a startup coordinate strategy id.
7. Stage 16 and Stage 17 policy hooks continue to validate temporal and transition tuples.
8. Bundle resolution never falls back to implicit default ids.

## 7. Deterministic `H18_*` Reason Codes

Add in `RouteCore`:

- `H18_TRAIT_BUNDLE_CONFIG_REQUIRED`
- `H18_UNKNOWN_TRAIT_BUNDLE`
- `H18_TRAIT_BUNDLE_CONFIG_CONFLICT`
- `H18_TRAIT_BUNDLE_INCOMPATIBLE`
- `H18_MISSING_TRAIT_DEPENDENCY`
- `H18_TRAIT_HASH_GENERATION_FAILED`
- `H18_REQUEST_TRAIT_SELECTOR_MISMATCH`

Compatibility rule:

- Keep existing `H15_*`, `H16_*`, `H17_*` mappings for axis-local failures.
- Use `H18_*` only for bundle-layer composition, dependency, hash, and selector mismatch failures.

## 8. Trait Hash and Lineage Contract

1. Build canonical trait-config string with fixed key order and explicit null tokens.
2. Compute SHA-256 digest in lowercase hex.
3. Store hash in `ResolvedTraitBundleContext` and `TraitBundleTelemetry`.
4. No per-request recomputation; hash is startup artifact only.
5. Bundle alias (`bundleId`) does not contribute to the hash; hash stability is based on resolved behavior, not registry naming.
6. The canonical hash input includes resolved temporal/transition strategy ids and resolved zone id so custom catalog remaps cannot silently reuse an old hash.
7. If model metadata `traits_hash` is present in future loader integration, comparison is deterministic and reason-coded.

## 9. Request-Time Behavior Contract

1. Request cannot switch addressing/temporal/transition mode.
2. If request selector hints are absent: proceed.
3. If request selector hints are present and match startup bundle: proceed.
4. If request selector hints are present and mismatch startup bundle: fail query with `H18_REQUEST_TRAIT_SELECTOR_MISMATCH`.
5. Planner and cost semantics remain unchanged after normalized context attachment.

## 10. Implementation Units (U0-U11)

1. `U0 - Documentation sync`: add Stage 18 plan doc and delta report skeleton.
2. `U1 - Bundle model types`: add bundle config/spec/context/telemetry classes.
3. `U2 - Bundle registry`: implement named bundle registration and explicit lookup (no auto-default selection).
4. `U3 - Bundle compatibility policy`: implement deterministic cross-axis validation.
5. `U4 - Bundle hasher`: canonical serializer + SHA-256 hash generation.
6. `U5 - Addressing startup strategy lock`: extend addressing runtime config/binder.
7. `U6 - Bundle runtime binder`: compose Stage 15/16/17 binders and produce resolved context.
8. `U7 - RouteCore integration`: add builder precedence and startup bundle exposure.
9. `U8 - Request hint soft-cutover`: enforce selector mismatch rejection deterministically.
10. `U9 - Unit tests`: registry/policy/hasher/binder coverage.
11. `U10 - Integration + stress/perf`: RouteCore path, determinism, and non-regression gates.
12. `U11 - Closure artifacts`: Stage 18 delta report, README update, stagewise status update.

## 11. Test Matrix

### Functional

- valid explicit bundle-id resolution,
- inline bundle resolution from legacy per-axis configs,
- unknown bundle id rejection,
- missing startup coordinate strategy for coordinate-capable addressing rejection,
- invalid empty bundle-path rejection,
- conflicting bundle/legacy config rejection.

### Validation

- bundle conflict between named and inline config path,
- bundle conflict between new and legacy config path,
- external-id-only addressing + coordinate strategy incompatibility rejection,
- request selector hint mismatch rejection.

### Determinism

- trait hash stable across repeated startup with same config,
- trait hash stable under concurrent startup runs,
- fixed snapshot route/matrix determinism unchanged.

### Non-regression

- Stage 13/14 parity remains zero mismatch,
- Stage 15/16/17 correctness tests remain green,
- existing stress/perf gates stay within established bounds,
- maintainability budgets remain green (`RouteCore <= 750`, `AddressingTraitEngine <= 980`).

## 12. Hard Exit Gates (Definition of Done)

Stage 18 closes only when:

1. all `R18-*` requirements map to passing tests,
2. all `H18_*` codes have deterministic coverage,
3. startup bundle authority is enforced end-to-end,
4. request-time trait switching is impossible,
5. selector hint soft-cutover behavior is enforced exactly,
6. Stage 13-17 test suites remain green with no behavior regressions,
7. Stage 18 delta report is published.

## 13. Assumptions and No-Default Policy

1. Soft cutover is active in Stage 18 (selector fields retained as deprecated hints).
2. Startup must explicitly provide either `traitBundleRuntimeConfig` or all legacy per-axis runtime configs.
3. No bundle id, trait id, or strategy id is auto-inferred when missing.
4. Route/matrix response schemas do not change in Stage 18.
5. Hard removal of request selector fields is deferred to a later stage.
