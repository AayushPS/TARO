# Trait Runtime Lock Audit Report

Date: 2026-02-23  
Scope: Audit current implementation against the principle: "all trait axes must be decided before runtime serving; request path must not switch trait behavior."

## 1. Principle Under Audit

Target principle:

1. Trait selection is startup-bound system behavior.
2. Trait choice is immutable for a `RouteCore` runtime instance.
3. Request payload does not select or switch trait mode.

## 2. Stage-by-Stage Findings

### 2.1 Stage 15 Addressing Trait

Status: Not aligned (major gap).

Evidence:

- `AddressingTraitEngine` resolves trait from request field `addressingTraitId`.
- Typed requests require per-request trait id (`H15_ADDRESSING_TRAIT_REQUIRED` path).
- Coordinate strategy is also request-selected (`coordinateDistanceStrategyId`).

Code references:

- `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`
- `src/main/java/org/Aayush/routing/core/RouteRequest.java`
- `src/main/java/org/Aayush/routing/core/MatrixRequest.java`

Conclusion:

- Stage 15 currently violates startup-lock principle by design.

### 2.2 Stage 16 Temporal Trait

Status: Mostly aligned, with fallback leakage in lower-level APIs.

Aligned behavior:

- `RouteCore` requires startup `temporalRuntimeConfig` and binds one temporal context.
- Runtime temporal mode is immutable per `RouteCore` instance.

Code references:

- `src/main/java/org/Aayush/routing/core/RouteCore.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinder.java`

Leakage (not aligned with strict principle):

- `InternalRouteRequest` and `InternalMatrixRequest` have compatibility constructors/defaults to `defaultCalendarUtc`.
- `CostEngine` has default temporal context overloads.
- `PathEvaluator` has default temporal context overload.
- `ResolvedTemporalContext.defaultCalendarUtc()` exists as globally available fallback.

Code references:

- `src/main/java/org/Aayush/routing/core/InternalRouteRequest.java`
- `src/main/java/org/Aayush/routing/core/InternalMatrixRequest.java`
- `src/main/java/org/Aayush/routing/cost/CostEngine.java`
- `src/main/java/org/Aayush/routing/core/PathEvaluator.java`
- `src/main/java/org/Aayush/routing/traits/temporal/ResolvedTemporalContext.java`

Conclusion:

- RouteCore request path is startup-locked, but internal compatibility APIs still allow bypassing strict lock semantics outside RouteCore flow.

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

1. Cross-axis inconsistency currently exists: Stage 15 is request-selected, Stage 16 is startup-selected.
2. Stage 16 fallback APIs can reintroduce implicit behavior if used outside RouteCore path.
3. Without cleanup, Stage 18 bundle-level startup lock may be partially undermined by compatibility overloads.

## 4. Recommended Remediation Path

### 4.1 Stage 15 (Required for full alignment)

1. Introduce `AddressingRuntimeConfig` and startup binder.
2. Remove request-time `addressingTraitId` and `coordinateDistanceStrategyId` from public request contracts (or deprecate, then remove).
3. Keep only address data in requests; resolve using startup-selected addressing mode.

### 4.2 Stage 16 (Hardening for strictness)

1. Deprecate/remove default temporal fallbacks in internal request constructors.
2. Deprecate/remove no-context `CostEngine` and `PathEvaluator` overloads in strict mode.
3. Keep strict mode tests proving no implicit temporal default path is reachable.

### 4.3 Stage 17 (Execute as locked)

1. Implement startup-bound transition binder/config.
2. Ensure no request field can switch transition mode.
3. Validate fallback and forbidden-turn behavior in both strategies.

## 5. Final Audit Verdict

- The "all traits fixed before runtime" main idea is currently missing in Stage 15 and partially leaked in Stage 16 compatibility APIs.
- Stage 17 planning is now aligned to that principle and can act as the template for bringing all trait axes under one startup-lock model in Stage 18.
