package org.Aayush.routing.traits.addressing;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;

import java.util.Objects;

/**
 * Startup-only binder for Stage 15 addressing trait runtime configuration.
 */
public final class AddressingRuntimeBinder {

    /**
     * Resolves one runtime config into immutable addressing execution binding.
     *
     * @param runtimeConfig addressing runtime config selected at startup.
     * @param traitCatalog addressing trait catalog.
     * @return immutable runtime binding.
     */
    public Binding bind(
            AddressingRuntimeConfig runtimeConfig,
            AddressingTraitCatalog traitCatalog
    ) {
        return bind(runtimeConfig, traitCatalog, CoordinateStrategyRegistry.defaultRegistry());
    }

    /**
     * Resolves one runtime config into immutable addressing execution binding.
     *
     * @param runtimeConfig addressing runtime config selected at startup.
     * @param traitCatalog addressing trait catalog.
     * @param coordinateStrategyRegistry coordinate-strategy registry.
     * @return immutable runtime binding.
     */
    public Binding bind(
            AddressingRuntimeConfig runtimeConfig,
            AddressingTraitCatalog traitCatalog,
            CoordinateStrategyRegistry coordinateStrategyRegistry
    ) {
        if (runtimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_ADDRESSING_CONFIG_REQUIRED,
                    "addressingRuntimeConfig must be provided at startup"
            );
        }
        AddressingTraitCatalog nonNullCatalog = Objects.requireNonNull(traitCatalog, "traitCatalog");
        CoordinateStrategyRegistry nonNullCoordinateStrategyRegistry =
                Objects.requireNonNull(coordinateStrategyRegistry, "coordinateStrategyRegistry");
        String traitId = normalizeRequiredId(
                runtimeConfig.getAddressingTraitId(),
                RouteCore.REASON_ADDRESSING_CONFIG_REQUIRED,
                "addressingTraitId must be provided at startup"
        );

        AddressingTrait trait = nonNullCatalog.trait(traitId);
        if (trait == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_ADDRESSING_TRAIT,
                    "unknown addressing trait id: " + traitId
            );
        }

        String coordinateStrategyId = null;
        CoordinateDistanceStrategy coordinateStrategy = null;
        if (trait.supports(AddressType.COORDINATES)) {
            coordinateStrategyId = normalizeRequiredId(
                    runtimeConfig.getCoordinateDistanceStrategyId(),
                    RouteCore.REASON_COORDINATE_STRATEGY_REQUIRED,
                    "coordinateDistanceStrategyId must be provided at startup for addressing trait " + trait.id()
            );
            coordinateStrategy = nonNullCoordinateStrategyRegistry.strategy(coordinateStrategyId);
            if (coordinateStrategy == null) {
                throw new RouteCoreException(
                        RouteCore.REASON_UNKNOWN_COORDINATE_STRATEGY,
                        "unknown coordinateDistanceStrategyId: " + coordinateStrategyId
                );
            }
        } else {
            coordinateStrategyId = normalizeOptionalId(runtimeConfig.getCoordinateDistanceStrategyId());
            if (coordinateStrategyId != null) {
                throw new RouteCoreException(
                        RouteCore.REASON_ADDRESSING_CONFIG_INCOMPATIBLE,
                        "coordinateDistanceStrategyId is not allowed for addressing trait " + trait.id()
                );
            }
        }

        return Binding.builder()
                .addressingTrait(trait)
                .coordinateStrategyId(coordinateStrategyId)
                .coordinateStrategy(coordinateStrategy)
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
     * Immutable runtime binding result.
     */
    @Value
    @Builder
    public static class Binding {
        AddressingTrait addressingTrait;
        String coordinateStrategyId;
        CoordinateDistanceStrategy coordinateStrategy;
    }
}
