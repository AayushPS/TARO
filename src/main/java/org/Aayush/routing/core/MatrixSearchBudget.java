package org.Aayush.routing.core;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Deterministic bounds for one-to-many matrix planner work and memory.
 */
final class MatrixSearchBudget {
    static final int UNBOUNDED = Integer.MAX_VALUE;

    static final String REASON_ROW_WORK_EXCEEDED = "H14_BUDGET_ROW_WORK_EXCEEDED";
    static final String REASON_ROW_LABEL_EXCEEDED = "H14_BUDGET_ROW_LABEL_EXCEEDED";
    static final String REASON_ROW_FRONTIER_EXCEEDED = "H14_BUDGET_ROW_FRONTIER_EXCEEDED";
    static final String REASON_REQUEST_WORK_EXCEEDED = "H14_BUDGET_REQUEST_WORK_EXCEEDED";

    private static final String PROP_MAX_ROW_WORK = "taro.routing.stage14.maxRowWorkStates";
    private static final String PROP_MAX_ROW_LABELS = "taro.routing.stage14.maxRowLabels";
    private static final String PROP_MAX_ROW_FRONTIER = "taro.routing.stage14.maxRowFrontierSize";
    private static final String PROP_MAX_REQUEST_WORK = "taro.routing.stage14.maxRequestWorkStates";

    private final int maxRowWorkStates;
    private final int maxRowLabels;
    private final int maxRowFrontierSize;
    private final int maxRequestWorkStates;

    private MatrixSearchBudget(
            int maxRowWorkStates,
            int maxRowLabels,
            int maxRowFrontierSize,
            int maxRequestWorkStates
    ) {
        this.maxRowWorkStates = normalizeBound(maxRowWorkStates);
        this.maxRowLabels = normalizeBound(maxRowLabels);
        this.maxRowFrontierSize = normalizeBound(maxRowFrontierSize);
        this.maxRequestWorkStates = normalizeBound(maxRequestWorkStates);
    }

    /**
     * Creates matrix-search budget with explicit deterministic bounds.
     */
    static MatrixSearchBudget of(
            int maxRowWorkStates,
            int maxRowLabels,
            int maxRowFrontierSize,
            int maxRequestWorkStates
    ) {
        return new MatrixSearchBudget(maxRowWorkStates, maxRowLabels, maxRowFrontierSize, maxRequestWorkStates);
    }

    /**
     * Loads deterministic matrix-search budget values from system properties.
     */
    static MatrixSearchBudget defaults() {
        return MatrixSearchBudget.of(
                readBound(PROP_MAX_ROW_WORK),
                readBound(PROP_MAX_ROW_LABELS),
                readBound(PROP_MAX_ROW_FRONTIER),
                readBound(PROP_MAX_REQUEST_WORK)
        );
    }

    /**
     * Validates row work-state budget.
     */
    void checkRowWorkStates(int rowWorkStates) {
        if (rowWorkStates > maxRowWorkStates) {
            throw new BudgetExceededException(
                    REASON_ROW_WORK_EXCEEDED,
                    "row work-state budget exceeded: " + rowWorkStates + " > " + maxRowWorkStates
            );
        }
    }

    /**
     * Validates row active-label budget.
     */
    void checkRowLabelCount(int labelCount) {
        if (labelCount > maxRowLabels) {
            throw new BudgetExceededException(
                    REASON_ROW_LABEL_EXCEEDED,
                    "row label budget exceeded: " + labelCount + " > " + maxRowLabels
            );
        }
    }

    /**
     * Validates row frontier-size budget.
     */
    void checkRowFrontierSize(int frontierSize) {
        if (frontierSize > maxRowFrontierSize) {
            throw new BudgetExceededException(
                    REASON_ROW_FRONTIER_EXCEEDED,
                    "row frontier budget exceeded: " + frontierSize + " > " + maxRowFrontierSize
            );
        }
    }

    /**
     * Validates request-wide work-state budget.
     */
    void checkRequestWorkStates(long requestWorkStates) {
        if (requestWorkStates > maxRequestWorkStates) {
            throw new BudgetExceededException(
                    REASON_REQUEST_WORK_EXCEEDED,
                    "request work-state budget exceeded: " + requestWorkStates + " > " + maxRequestWorkStates
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
     * Deterministic exception for matrix budget fail-fast paths.
     */
    @Getter
    @Accessors(fluent = true)
    static final class BudgetExceededException extends RuntimeException {
        private final String reasonCode;

        BudgetExceededException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
