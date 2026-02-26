package org.Aayush.routing.traits.transition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable registry for transition-cost strategies.
 */
public final class TransitionStrategyRegistry {
    public static final String STRATEGY_NODE_BASED = "NODE_BASED";
    public static final String STRATEGY_EDGE_BASED = "EDGE_BASED";

    private static final TransitionCostStrategy NODE_BASED_STRATEGY = new NodeBasedTransitionCostStrategy();
    private static final TransitionCostStrategy EDGE_BASED_STRATEGY = new EdgeBasedTransitionCostStrategy();

    private final Map<String, TransitionCostStrategy> strategiesById;

    /**
     * Creates a registry with built-in strategies only.
     */
    public TransitionStrategyRegistry() {
        this.strategiesById = Map.copyOf(materialize(defaultStrategies()));
    }

    /**
     * Creates a registry by merging built-ins with custom strategies.
     */
    public TransitionStrategyRegistry(Collection<? extends TransitionCostStrategy> customStrategies) {
        this.strategiesById = Map.copyOf(materialize(mergeWithBuiltIns(customStrategies)));
    }

    /**
     * Creates an explicit registry from provided strategies.
     */
    public TransitionStrategyRegistry(Collection<? extends TransitionCostStrategy> strategies, boolean includeBuiltIns) {
        Collection<? extends TransitionCostStrategy> source = includeBuiltIns ? mergeWithBuiltIns(strategies) : strategies;
        this.strategiesById = Map.copyOf(materialize(source));
    }

    /**
     * Returns strategy by id, or null when not registered.
     */
    public TransitionCostStrategy strategy(String strategyId) {
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
    public static TransitionStrategyRegistry defaultRegistry() {
        return new TransitionStrategyRegistry();
    }

    private static Collection<? extends TransitionCostStrategy> defaultStrategies() {
        return java.util.List.of(NODE_BASED_STRATEGY, EDGE_BASED_STRATEGY);
    }

    private static Collection<? extends TransitionCostStrategy> mergeWithBuiltIns(
            Collection<? extends TransitionCostStrategy> customStrategies
    ) {
        LinkedHashMap<String, TransitionCostStrategy> merged = new LinkedHashMap<>();
        for (TransitionCostStrategy strategy : defaultStrategies()) {
            merged.put(strategy.id(), strategy);
        }
        if (customStrategies != null) {
            for (TransitionCostStrategy strategy : customStrategies) {
                TransitionCostStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");
                merged.put(normalizeRequiredId(nonNullStrategy.id(), "strategy.id"), nonNullStrategy);
            }
        }
        return merged.values();
    }

    private static LinkedHashMap<String, TransitionCostStrategy> materialize(
            Collection<? extends TransitionCostStrategy> strategies
    ) {
        Objects.requireNonNull(strategies, "strategies");
        LinkedHashMap<String, TransitionCostStrategy> map = new LinkedHashMap<>();
        for (TransitionCostStrategy strategy : strategies) {
            TransitionCostStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");
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
