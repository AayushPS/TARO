package org.Aayush.routing.core;

/**
 * Stage 12 temporary matrix planner.
 *
 * NOTE(Stage 14 revisit): Replace this pairwise route expansion with
 * dedicated one-to-many Dijkstra matrix execution and parity/perf gates.
 */
final class TemporaryMatrixPlanner implements MatrixPlanner {
    static final String STAGE14_REVISIT_NOTE =
            "NOTE(Stage 14 revisit): Replace Stage 12 temporary matrix execution path with the dedicated one-to-many Dijkstra matrix engine, and enforce A*/Dijkstra parity and performance gates before closing Stage 14.";

    @Override
    public MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request) {
        int sourceCount = request.sourceNodeIds().length;
        int targetCount = request.targetNodeIds().length;

        boolean[][] reachable = new boolean[sourceCount][targetCount];
        float[][] costs = new float[sourceCount][targetCount];
        long[][] arrivals = new long[sourceCount][targetCount];

        for (int i = 0; i < sourceCount; i++) {
            int sourceNodeId = request.sourceNodeIds()[i];
            for (int j = 0; j < targetCount; j++) {
                int targetNodeId = request.targetNodeIds()[j];
                InternalRouteRequest routeRequest = new InternalRouteRequest(
                        sourceNodeId,
                        targetNodeId,
                        request.departureTicks(),
                        request.algorithm(),
                        request.heuristicType()
                );

                InternalRoutePlan plan = routeCore.computeInternal(routeRequest);
                reachable[i][j] = plan.reachable();
                costs[i][j] = plan.totalCost();
                arrivals[i][j] = plan.arrivalTicks();
            }
        }

        return new MatrixPlan(reachable, costs, arrivals, STAGE14_REVISIT_NOTE);
    }
}

