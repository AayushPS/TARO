# Trait Runtime Lock Audit Report

Date: 2026-02-23  
Scope: Audit current implementation against the principle: "all trait axes must be decided before runtime serving; request path must not switch trait behavior."

Update: 2026-03-20

- Stage 15 request-time addressing selectors have been removed from `RouteRequest` and `MatrixRequest`.
- `AddressingTraitEngine` no longer reads request selector hints and now resolves strictly from startup-bound runtime binding.
- Stage 16 strictness findings from this audit are already reflected in current code: internal requests require explicit temporal context and core hot-path APIs do not expose implicit temporal fallback overloads.

## 1. Principle Under Audit

Target principle:

1. Trait selection is startup-bound system behavior.
2. Trait choice is immutable for a `RouteCore` runtime instance.
3. Request payload does not select or switch trait mode.

## 2. Stage-by-Stage Findings

### 2.1 Stage 15 Addressing Trait

Status: Aligned after migration on 2026-03-20.

Previous gap (now removed):

- `AddressingTraitEngine` used request selector hints (`addressingTraitId`, `coordinateDistanceStrategyId`) as part of request validation.
- Public request contracts exposed addressing selector fields even though startup runtime config already bound the actual mode.

Code references:

- `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`
- `src/main/java/org/Aayush/routing/core/RouteRequest.java`
- `src/main/java/org/Aayush/routing/core/MatrixRequest.java`

Conclusion:

- Stage 15 now follows the startup-lock principle: requests only carry address data, while runtime trait and coordinate strategy are selected once at startup.

### 2.2 Stage 16 Temporal Trait

Status: Aligned after hardening already present in current code.

Aligned behavior:

- `RouteCore` requires startup `temporalRuntimeConfig` and binds one temporal context.
- Runtime temporal mode is immutable per `RouteCore` instance.
- `InternalRouteRequest` and `InternalMatrixRequest` require explicit `ResolvedTemporalContext`.
- `CostEngine` and `PathEvaluator` use explicit temporal-context APIs.
- `ResolvedTemporalContext` no longer exposes a global default fallback helper.

Code references:

- `src/main/java/org/Aayush/routing/core/RouteCore.java`
- `src/main/java/org/Aayush/routing/core/InternalRouteRequest.java`
- `src/main/java/org/Aayush/routing/core/InternalMatrixRequest.java`
- `src/main/java/org/Aayush/routing/cost/CostEngine.java`
- `src/main/java/org/Aayush/routing/core/PathEvaluator.java`
- `src/main/java/org/Aayush/routing/traits/temporal/ResolvedTemporalContext.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinder.java`

Conclusion:

- Stage 16 now enforces strict startup-locked temporal behavior across request and internal execution flow.

### 2.3 Stage 17 Transition Trait (Planned)

Status: Locked to startup-bound model in current plan.

Policy now fixed in:

- `docs/stage17_transition_trait_plan.md`

Key locks:

- transition trait selected at startup via runtime config,
- immutable per `RouteCore`,
- no request-time transition trait switching,
- natural fallback when `TurnCostMap` absent,
- forbidden-turn blocking when turn map marks transition forbidden.

## 3. Risk Summary

1. Cross-axis inconsistency between Stage 15 and Stage 16 has been removed.
2. Residual strictness risk is now mostly limited to ensuring future APIs do not reintroduce request-time trait selectors.
3. Stage 18 bundle-level startup lock is now reinforced by the public request contract instead of merely validated at runtime.

## 4. Recommended Remediation Path

### 4.1 Stage 15 (Completed)

1. `AddressingRuntimeConfig` and startup binder are in place.
2. Request-time `addressingTraitId` and `coordinateDistanceStrategyId` have been removed from public request contracts.
3. Requests now carry only address data; resolution uses startup-selected addressing mode.

### 4.2 Stage 16 (Completed Hardening)

1. Default temporal fallbacks in internal request constructors are removed.
2. No-context `CostEngine` and `PathEvaluator` temporal shortcuts are not part of the strict request flow.
3. Strict mode tests cover absence of implicit temporal default paths.

### 4.3 Stage 17 (Execute as locked)

1. Implement startup-bound transition binder/config.
2. Ensure no request field can switch transition mode.
3. Validate fallback and forbidden-turn behavior in both strategies.

## 5. Final Audit Verdict

- The startup-lock principle is now enforced across Stage 15, Stage 16, Stage 17, and Stage 18 request flow.
- Remaining maintenance work is hardening-by-regression-test rather than architectural migration.
