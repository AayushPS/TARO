package org.Aayush.routing.traits.registry;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.traits.addressing.AddressType;
import org.Aayush.routing.traits.addressing.AddressingTrait;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;

import java.util.Objects;

/**
 * Stage 18 compatibility policy for cross-axis bundle dependencies.
 */
public final class TraitBundleCompatibilityPolicy {

    /**
     * Validates cross-axis bundle dependencies not owned by Stage 15/16/17 binders.
     */
    public void validate(TraitBundleSpec bundleSpec, AddressingTraitCatalog addressingTraitCatalog) {
        Objects.requireNonNull(bundleSpec, "bundleSpec");
        AddressingTraitCatalog nonNullCatalog = Objects.requireNonNull(addressingTraitCatalog, "addressingTraitCatalog");

        AddressingTrait addressingTrait = nonNullCatalog.trait(bundleSpec.getAddressingTraitId());
        if (addressingTrait == null) {
            return;
        }

        String coordinateStrategyId = normalizeOptionalId(bundleSpec.getCoordinateDistanceStrategyId());
        if (addressingTrait.supports(AddressType.COORDINATES) && coordinateStrategyId == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_MISSING_TRAIT_DEPENDENCY,
                    "addressing trait " + addressingTrait.id()
                            + " requires startup coordinateDistanceStrategyId"
            );
        }
        if (!addressingTrait.supports(AddressType.COORDINATES) && coordinateStrategyId != null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRAIT_BUNDLE_INCOMPATIBLE,
                    "addressing trait " + addressingTrait.id()
                            + " does not permit coordinateDistanceStrategyId"
            );
        }
    }

    /**
     * Returns the default bundle compatibility policy.
     */
    public static TraitBundleCompatibilityPolicy defaults() {
        return new TraitBundleCompatibilityPolicy();
    }

    private static String normalizeOptionalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
