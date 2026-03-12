package org.Aayush.routing.traits.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable Stage 18 registry for named trait bundles.
 */
public final class TraitBundleRegistry {
    private final Map<String, TraitBundleSpec> bundlesById;

    /**
     * Creates an empty registry.
     */
    public TraitBundleRegistry() {
        this.bundlesById = Map.of();
    }

    /**
     * Creates a registry from explicit bundle specs keyed by {@code bundleId}.
     */
    public TraitBundleRegistry(Collection<? extends TraitBundleSpec> bundleSpecs) {
        LinkedHashMap<String, TraitBundleSpec> bundles = new LinkedHashMap<>();
        if (bundleSpecs != null) {
            for (TraitBundleSpec bundleSpec : bundleSpecs) {
                TraitBundleSpec nonNullSpec = Objects.requireNonNull(bundleSpec, "bundleSpec");
                String bundleId = normalizeRequiredId(nonNullSpec.getBundleId(), "bundleSpec.bundleId");
                TraitBundleSpec normalizedSpec = normalizeBundleId(nonNullSpec, bundleId);
                TraitBundleSpec previous = bundles.putIfAbsent(bundleId, normalizedSpec);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate trait bundle id: " + bundleId);
                }
            }
        }
        this.bundlesById = Map.copyOf(bundles);
    }

    /**
     * Returns a bundle spec by id, or {@code null} when not registered.
     */
    public TraitBundleSpec bundle(String bundleId) {
        String normalizedBundleId = normalizeOptionalId(bundleId);
        if (normalizedBundleId == null) {
            return null;
        }
        return bundlesById.get(normalizedBundleId);
    }

    /**
     * Returns immutable registered bundle ids.
     */
    public Set<String> bundleIds() {
        return bundlesById.keySet();
    }

    /**
     * Returns an empty default registry shape.
     */
    public static TraitBundleRegistry defaultRegistry() {
        return new TraitBundleRegistry();
    }

    private static TraitBundleSpec normalizeBundleId(TraitBundleSpec bundleSpec, String bundleId) {
        return TraitBundleSpec.builder()
                .bundleId(bundleId)
                .addressingTraitId(bundleSpec.getAddressingTraitId())
                .coordinateDistanceStrategyId(bundleSpec.getCoordinateDistanceStrategyId())
                .temporalTraitId(bundleSpec.getTemporalTraitId())
                .timezonePolicyId(bundleSpec.getTimezonePolicyId())
                .modelProfileTimezone(bundleSpec.getModelProfileTimezone())
                .transitionTraitId(bundleSpec.getTransitionTraitId())
                .build();
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = normalizeOptionalId(Objects.requireNonNull(id, fieldName));
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
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
}
