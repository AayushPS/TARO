package org.Aayush.routing.core;

/**
 * Internal matrix planner abstraction for Stage 12 route orchestration.
 */
interface MatrixPlanner {
    MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request);
}

