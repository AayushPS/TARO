package org.Aayush.routing.core;

/**
 * Per-query deterministic bounds for planner work and memory growth.
 */
final class SearchBudget {
    static final int UNBOUNDED = Integer.MAX_VALUE;

    static final String REASON_SETTLED_EXCEEDED = "H13_BUDGET_SETTLED_EXCEEDED";
    static final String REASON_LABEL_EXCEEDED = "H13_BUDGET_LABEL_EXCEEDED";
    static final String REASON_FRONTIER_EXCEEDED = "H13_BUDGET_FRONTIER_EXCEEDED";

    private static final String PROP_MAX_SETTLED = "taro.routing.stage13.maxSettledStates";
    private static final String PROP_MAX_LABELS = "taro.routing.stage13.maxLabels";
    private static final String PROP_MAX_FRONTIER = "taro.routing.stage13.maxFrontierSize";

    private final int maxSettledStates;
    private final int maxLabels;
    private final int maxFrontierSize;

    private SearchBudget(int maxSettledStates, int maxLabels, int maxFrontierSize) {
        this.maxSettledStates = normalizeBound(maxSettledStates);
        this.maxLabels = normalizeBound(maxLabels);
        this.maxFrontierSize = normalizeBound(maxFrontierSize);
    }

    /**
     * Creates a budget with explicit bounds.
     */
    static SearchBudget of(int maxSettledStates, int maxLabels, int maxFrontierSize) {
        return new SearchBudget(maxSettledStates, maxLabels, maxFrontierSize);
    }

    /**
     * Loads budget values from deterministic system properties.
     */
    static SearchBudget defaults() {
        return SearchBudget.of(
                readBound(PROP_MAX_SETTLED),
                readBound(PROP_MAX_LABELS),
                readBound(PROP_MAX_FRONTIER)
        );
    }

    /**
     * Validates total settled/work-state count against configured bound.
     */
    void checkSettledStates(int workStates) {
        if (workStates > maxSettledStates) {
            throw new BudgetExceededException(
                    REASON_SETTLED_EXCEEDED,
                    "settled/work-state budget exceeded: " + workStates + " > " + maxSettledStates
            );
        }
    }

    /**
     * Validates active label count against configured bound.
     */
    void checkLabelCount(int labelCount) {
        if (labelCount > maxLabels) {
            throw new BudgetExceededException(
                    REASON_LABEL_EXCEEDED,
                    "label budget exceeded: " + labelCount + " > " + maxLabels
            );
        }
    }

    /**
     * Validates total frontier size against configured bound.
     */
    void checkFrontierSize(int frontierSize) {
        if (frontierSize > maxFrontierSize) {
            throw new BudgetExceededException(
                    REASON_FRONTIER_EXCEEDED,
                    "frontier budget exceeded: " + frontierSize + " > " + maxFrontierSize
            );
        }
    }

    private static int normalizeBound(int bound) {
        if (bound <= 0) {
            return UNBOUNDED;
        }
        return bound;
    }

    private static int readBound(String property) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return UNBOUNDED;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return UNBOUNDED;
        }
    }

    /**
     * Deterministic exception for budget fail-fast paths.
     */
    static final class BudgetExceededException extends RuntimeException {
        private final String reasonCode;

        BudgetExceededException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }
}
