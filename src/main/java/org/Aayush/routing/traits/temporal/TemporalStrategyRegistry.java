package org.Aayush.routing.traits.temporal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable registry for temporal resolution strategies.
 */
public final class TemporalStrategyRegistry {
    public static final String STRATEGY_LINEAR = "LINEAR";
    public static final String STRATEGY_CALENDAR = "CALENDAR";

    private static final TemporalResolutionStrategy LINEAR_STRATEGY = new LinearTemporalResolutionStrategy();
    private static final TemporalResolutionStrategy CALENDAR_STRATEGY = new CalendarTemporalResolutionStrategy();

    private final Map<String, TemporalResolutionStrategy> strategiesById;

    /**
     * Creates a registry with built-in strategies only.
     */
    public TemporalStrategyRegistry() {
        this.strategiesById = Map.copyOf(materialize(defaultStrategies()));
    }

    /**
     * Creates a registry by merging built-ins with custom strategies.
     *
     * <p>Custom strategy ids override built-ins when ids collide.</p>
     */
    public TemporalStrategyRegistry(Collection<? extends TemporalResolutionStrategy> customStrategies) {
        this.strategiesById = Map.copyOf(materialize(mergeWithBuiltIns(customStrategies)));
    }

    /**
     * Creates an explicit registry from provided strategies.
     */
    public TemporalStrategyRegistry(Collection<? extends TemporalResolutionStrategy> strategies, boolean includeBuiltIns) {
        Collection<? extends TemporalResolutionStrategy> source =
                includeBuiltIns ? mergeWithBuiltIns(strategies) : strategies;
        this.strategiesById = Map.copyOf(materialize(source));
    }

    /**
     * Returns strategy by id, or {@code null} when not registered.
     */
    public TemporalResolutionStrategy strategy(String strategyId) {
        if (strategyId == null) {
            return null;
        }
        return strategiesById.get(strategyId);
    }

    /**
     * Returns immutable set of registered strategy ids.
     */
    public Set<String> strategyIds() {
        return strategiesById.keySet();
    }

    /**
     * Returns a new default registry instance.
     */
    public static TemporalStrategyRegistry defaultRegistry() {
        return new TemporalStrategyRegistry();
    }

    private static Collection<? extends TemporalResolutionStrategy> defaultStrategies() {
        return java.util.List.of(LINEAR_STRATEGY, CALENDAR_STRATEGY);
    }

    private static Collection<? extends TemporalResolutionStrategy> mergeWithBuiltIns(
            Collection<? extends TemporalResolutionStrategy> customStrategies
    ) {
        LinkedHashMap<String, TemporalResolutionStrategy> merged = new LinkedHashMap<>();
        for (TemporalResolutionStrategy strategy : defaultStrategies()) {
            merged.put(strategy.id(), strategy);
        }
        if (customStrategies != null) {
            for (TemporalResolutionStrategy strategy : customStrategies) {
                TemporalResolutionStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");
                merged.put(normalizeRequiredId(nonNullStrategy.id(), "strategy.id"), nonNullStrategy);
            }
        }
        return merged.values();
    }

    private static LinkedHashMap<String, TemporalResolutionStrategy> materialize(
            Collection<? extends TemporalResolutionStrategy> strategies
    ) {
        Objects.requireNonNull(strategies, "strategies");
        LinkedHashMap<String, TemporalResolutionStrategy> map = new LinkedHashMap<>();
        for (TemporalResolutionStrategy strategy : strategies) {
            TemporalResolutionStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");
            map.put(normalizeRequiredId(nonNullStrategy.id(), "strategy.id"), nonNullStrategy);
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
}
