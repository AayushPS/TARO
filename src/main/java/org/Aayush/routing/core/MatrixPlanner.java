package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;

import java.util.Objects;

/**
 * Matrix planner strategy.
 *
 * <p>Implementations accept fully normalized internal requests and return raw planner
 * outputs plus execution metadata consumed by {@link RouteCore}.</p>
 */
public interface MatrixPlanner {
    /**
     * Computes many-to-many routing outputs for a normalized request.
     *
     * @param routeCore route core runtime contract.
     * @param request normalized matrix request in internal node-id space.
     * @return matrix planning result.
     */
    MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request);

    /**
     * Computes many-to-many routing outputs for a normalized request using an explicit cost engine.
     *
     * <p>Legacy matrix-planner overrides may ignore the explicit cost engine and continue using the
     * route core's bound runtime contract. Scenario-aware planners should override this method.</p>
     */
    default MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request, CostEngine activeCostEngine) {
        Objects.requireNonNull(routeCore, "routeCore");
        Objects.requireNonNull(activeCostEngine, "activeCostEngine");
        return compute(routeCore, request);
    }
}
