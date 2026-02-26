package org.Aayush.routing.traits.transition;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;

import java.util.Objects;

/**
 * Startup-only transition runtime binder.
 */
public final class TransitionRuntimeBinder {

    /**
     * Binds one transition runtime config into immutable execution contracts.
     */
    public Binding bind(
            TransitionRuntimeConfig runtimeConfig,
            TransitionTraitCatalog traitCatalog,
            TransitionStrategyRegistry strategyRegistry,
            TransitionPolicy transitionPolicy
    ) {
        if (runtimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRANSITION_CONFIG_REQUIRED,
                    "transitionRuntimeConfig must be provided at startup"
            );
        }

        TransitionRuntimeConfig nonNullConfig = runtimeConfig;
        TransitionTraitCatalog nonNullTraitCatalog = Objects.requireNonNull(traitCatalog, "traitCatalog");
        TransitionStrategyRegistry nonNullStrategyRegistry = Objects.requireNonNull(strategyRegistry, "strategyRegistry");
        TransitionPolicy nonNullTransitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy");

        String traitId = normalizeRequiredId(
                nonNullConfig.getTransitionTraitId(),
                RouteCore.REASON_TRANSITION_CONFIG_REQUIRED,
                "transitionTraitId must be provided"
        );
        TransitionTrait trait = nonNullTraitCatalog.trait(traitId);
        if (trait == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_TRANSITION_TRAIT,
                    "unknown transition trait id: " + traitId
            );
        }

        String strategyId = normalizeRequiredId(
                trait.strategyId(),
                RouteCore.REASON_UNKNOWN_TRANSITION_STRATEGY,
                "strategy id must be present for trait " + trait.id()
        );
        TransitionCostStrategy strategy = nonNullStrategyRegistry.strategy(strategyId);
        if (strategy == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_TRANSITION_STRATEGY,
                    "unknown transition strategy id: " + strategyId + " for trait " + trait.id()
            );
        }

        try {
            nonNullTransitionPolicy.validateCompatibility(trait, strategy);
        } catch (TransitionPolicy.CompatibilityException ex) {
            throw new RouteCoreException(ex.reasonCode(), ex.getMessage(), ex);
        }

        ResolvedTransitionContext context = ResolvedTransitionContext.builder()
                .transitionTraitId(trait.id())
                .transitionStrategyId(strategy.id())
                .finiteTurnPenaltiesEnabled(strategy.appliesFiniteTurnPenalties())
                .strategy(strategy)
                .build();
        TransitionTelemetry telemetry = TransitionTelemetry.builder()
                .transitionTraitId(trait.id())
                .transitionStrategyId(strategy.id())
                .finiteTurnPenaltiesEnabled(strategy.appliesFiniteTurnPenalties())
                .build();

        return Binding.builder()
                .resolvedTransitionContext(context)
                .transitionTelemetry(telemetry)
                .build();
    }

    private static String normalizeRequiredId(String id, String reasonCode, String message) {
        String normalized = normalizeOptionalId(id);
        if (normalized == null) {
            throw new RouteCoreException(reasonCode, message);
        }
        return normalized;
    }

    private static String normalizeOptionalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    /**
     * Immutable transition runtime binding output.
     */
    @Value
    @Builder
    public static class Binding {
        /**
         * Locked transition context attached to normalized requests.
         */
        ResolvedTransitionContext resolvedTransitionContext;

        /**
         * Startup telemetry for bound transition mode.
         */
        TransitionTelemetry transitionTelemetry;
    }
}
