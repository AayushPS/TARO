package org.Aayush.routing.traits.transition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable Stage 17 transition-trait catalog.
 */
public final class TransitionTraitCatalog {
    public static final String TRAIT_NODE_BASED = "NODE_BASED";
    public static final String TRAIT_EDGE_BASED = "EDGE_BASED";

    private static final TransitionTrait NODE_BASED_TRAIT =
            new StaticTransitionTrait(TRAIT_NODE_BASED, TransitionStrategyRegistry.STRATEGY_NODE_BASED);
    private static final TransitionTrait EDGE_BASED_TRAIT =
            new StaticTransitionTrait(TRAIT_EDGE_BASED, TransitionStrategyRegistry.STRATEGY_EDGE_BASED);

    private final Map<String, TransitionTrait> traitsById;

    /**
     * Creates a catalog with built-in transition traits only.
     */
    public TransitionTraitCatalog() {
        this.traitsById = Map.copyOf(materialize(defaultTraits()));
    }

    /**
     * Creates a catalog by merging built-ins with custom transition traits.
     */
    public TransitionTraitCatalog(Collection<? extends TransitionTrait> customTraits) {
        this.traitsById = Map.copyOf(materialize(mergeWithBuiltIns(customTraits)));
    }

    /**
     * Creates an explicit catalog from the given traits.
     */
    public TransitionTraitCatalog(Collection<? extends TransitionTrait> traits, boolean includeBuiltIns) {
        Collection<? extends TransitionTrait> source = includeBuiltIns ? mergeWithBuiltIns(traits) : traits;
        this.traitsById = Map.copyOf(materialize(source));
    }

    /**
     * Returns transition trait by id, or null when not registered.
     */
    public TransitionTrait trait(String traitId) {
        if (traitId == null) {
            return null;
        }
        return traitsById.get(traitId);
    }

    /**
     * Returns immutable set of registered trait ids.
     */
    public Set<String> traitIds() {
        return traitsById.keySet();
    }

    /**
     * Returns a new default catalog instance.
     */
    public static TransitionTraitCatalog defaultCatalog() {
        return new TransitionTraitCatalog();
    }

    private static Collection<? extends TransitionTrait> defaultTraits() {
        return java.util.List.of(NODE_BASED_TRAIT, EDGE_BASED_TRAIT);
    }

    private static Collection<? extends TransitionTrait> mergeWithBuiltIns(
            Collection<? extends TransitionTrait> customTraits
    ) {
        LinkedHashMap<String, TransitionTrait> merged = new LinkedHashMap<>();
        for (TransitionTrait trait : defaultTraits()) {
            merged.put(trait.id(), trait);
        }
        if (customTraits != null) {
            for (TransitionTrait trait : customTraits) {
                TransitionTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
                String id = normalizeRequiredId(nonNullTrait.id(), "trait.id");
                String strategyId = normalizeRequiredId(nonNullTrait.strategyId(), "trait.strategyId");
                merged.put(id, new StaticTransitionTrait(id, strategyId));
            }
        }
        return merged.values();
    }

    private static LinkedHashMap<String, TransitionTrait> materialize(Collection<? extends TransitionTrait> traits) {
        Objects.requireNonNull(traits, "traits");
        LinkedHashMap<String, TransitionTrait> map = new LinkedHashMap<>();
        for (TransitionTrait trait : traits) {
            TransitionTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
            String id = normalizeRequiredId(nonNullTrait.id(), "trait.id");
            String strategyId = normalizeRequiredId(nonNullTrait.strategyId(), "trait.strategyId");
            map.put(id, new StaticTransitionTrait(id, strategyId));
        }
        return map;
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = Objects.requireNonNull(id, fieldName).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return normalized;
    }

    private record StaticTransitionTrait(String id, String strategyId) implements TransitionTrait {
        private StaticTransitionTrait {
            id = normalizeRequiredId(id, "id");
            strategyId = normalizeRequiredId(strategyId, "strategyId");
        }
    }
}
