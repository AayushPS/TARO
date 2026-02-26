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
        if (runtimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_ADDRESSING_CONFIG_REQUIRED,
                    "addressingRuntimeConfig must be provided at startup"
            );
        }
        AddressingTraitCatalog nonNullCatalog = Objects.requireNonNull(traitCatalog, "traitCatalog");
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

        return Binding.builder()
                .addressingTrait(trait)
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
    }
}
