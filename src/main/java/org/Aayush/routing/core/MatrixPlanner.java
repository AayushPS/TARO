package org.Aayush.routing.core;

/**
 * Internal matrix planner abstraction for Stage 12 route orchestration.
 */
interface MatrixPlanner {
    /**
     * Computes many-to-many routing outputs for a normalized request.
     *
     * @param routeCore route core used to execute pairwise/internal searches.
     * @param request normalized matrix request in internal node-id space.
     * @return matrix planning result.
     */
    MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request);
}
