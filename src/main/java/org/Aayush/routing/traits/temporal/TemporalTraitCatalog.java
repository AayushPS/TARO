package org.Aayush.routing.traits.temporal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable Stage 16 temporal-trait catalog.
 *
 * <p>The default catalog contains two built-ins:</p>
 * <ul>
 * <li>{@code LINEAR} -> {@code LINEAR} strategy</li>
 * <li>{@code CALENDAR} -> {@code CALENDAR} strategy</li>
 * </ul>
 */
public final class TemporalTraitCatalog {
    public static final String TRAIT_LINEAR = "LINEAR";
    public static final String TRAIT_CALENDAR = "CALENDAR";

    private static final TemporalTrait LINEAR_TRAIT =
            new StaticTemporalTrait(TRAIT_LINEAR, TemporalStrategyRegistry.STRATEGY_LINEAR);
    private static final TemporalTrait CALENDAR_TRAIT =
            new StaticTemporalTrait(TRAIT_CALENDAR, TemporalStrategyRegistry.STRATEGY_CALENDAR);

    private final Map<String, TemporalTrait> traitsById;

    /**
     * Creates a catalog with built-in temporal traits only.
     */
    public TemporalTraitCatalog() {
        this.traitsById = Map.copyOf(materialize(defaultTraits()));
    }

    /**
     * Creates a catalog by merging built-ins with custom temporal traits.
     *
     * <p>Custom trait ids override built-ins when ids collide.</p>
     */
    public TemporalTraitCatalog(Collection<? extends TemporalTrait> customTraits) {
        this.traitsById = Map.copyOf(materialize(mergeWithBuiltIns(customTraits)));
    }

    /**
     * Creates an explicit catalog from the given traits.
     */
    public TemporalTraitCatalog(Collection<? extends TemporalTrait> traits, boolean includeBuiltIns) {
        Collection<? extends TemporalTrait> source = includeBuiltIns ? mergeWithBuiltIns(traits) : traits;
        this.traitsById = Map.copyOf(materialize(source));
    }

    /**
     * Returns temporal trait by id, or {@code null} when not registered.
     */
    public TemporalTrait trait(String traitId) {
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
    public static TemporalTraitCatalog defaultCatalog() {
        return new TemporalTraitCatalog();
    }

    private static Collection<? extends TemporalTrait> mergeWithBuiltIns(Collection<? extends TemporalTrait> customTraits) {
        LinkedHashMap<String, TemporalTrait> merged = new LinkedHashMap<>();
        for (TemporalTrait trait : defaultTraits()) {
            merged.put(trait.id(), trait);
        }
        if (customTraits != null) {
            for (TemporalTrait trait : customTraits) {
                TemporalTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
                String id = normalizeRequiredId(nonNullTrait.id(), "trait.id");
                String strategyId = normalizeRequiredId(nonNullTrait.strategyId(), "trait.strategyId");
                merged.put(id, new StaticTemporalTrait(id, strategyId));
            }
        }
        return merged.values();
    }

    private static LinkedHashMap<String, TemporalTrait> materialize(Collection<? extends TemporalTrait> traits) {
        Objects.requireNonNull(traits, "traits");
        LinkedHashMap<String, TemporalTrait> map = new LinkedHashMap<>();
        for (TemporalTrait trait : traits) {
            TemporalTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
            String id = normalizeRequiredId(nonNullTrait.id(), "trait.id");
            String strategyId = normalizeRequiredId(nonNullTrait.strategyId(), "trait.strategyId");
            map.put(id, new StaticTemporalTrait(id, strategyId));
        }
        return map;
    }

    private static Collection<? extends TemporalTrait> defaultTraits() {
        return java.util.List.of(LINEAR_TRAIT, CALENDAR_TRAIT);
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = Objects.requireNonNull(id, fieldName).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return normalized;
    }

    private record StaticTemporalTrait(String id, String strategyId) implements TemporalTrait {
        private StaticTemporalTrait {
            id = normalizeRequiredId(id, "id");
            strategyId = normalizeRequiredId(strategyId, "strategyId");
        }
    }
}
