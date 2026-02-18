package org.Aayush.routing.core;

/**
 * Internal matrix planner abstraction.
 *
 * <p>Implementations accept fully normalized internal requests and return raw planner
 * outputs plus execution metadata consumed by {@link RouteCore}.</p>
 */
interface MatrixPlanner {
    /**
     * Computes many-to-many routing outputs for a normalized request.
     *
     * @param routeCore route core runtime contract.
     * @param request normalized matrix request in internal node-id space.
     * @return matrix planning result.
     */
    MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request);
}
