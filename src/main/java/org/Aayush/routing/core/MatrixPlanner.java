package org.Aayush.routing.core;

/**
 * Internal matrix planner abstraction.
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
