package org.Aayush.routing.future;

/**
 * Explains whether a served route family came from a per-scenario optimum or required aggregate-objective rescue.
 */
public enum RouteSelectionProvenance {
    UNREACHABLE,
    SCENARIO_OPTIMAL,
    AGGREGATE_OBJECTIVE
}
