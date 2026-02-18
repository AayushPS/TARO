package org.Aayush.routing.core;

/**
 * Pairwise matrix compatibility planner.
 *
 * <p>This planner expands each source/target cell via {@link RouteCore#computeInternal(InternalRouteRequest)}.
 * It is intentionally simple and contract-preserving, and acts as compatibility mode for
 * execution paths that are not handled by the native one-to-many Dijkstra planner.</p>
 */
final class TemporaryMatrixPlanner implements MatrixPlanner {
    static final String PAIRWISE_COMPATIBILITY_NOTE =
            "Compatibility mode: pairwise matrix expansion via RouteCore.computeInternal.";

    /**
     * Expands matrix request via independent pairwise route calls.
     */
    @Override
    public MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request) {
        int sourceCount = request.sourceNodeIds().length;
        int targetCount = request.targetNodeIds().length;

        boolean[][] reachable = new boolean[sourceCount][targetCount];
        float[][] costs = new float[sourceCount][targetCount];
        long[][] arrivals = new long[sourceCount][targetCount];
        int[] rowWorkStates = new int[sourceCount];
        int[] rowSettledStates = new int[sourceCount];
        int[] rowLabelPeaks = new int[sourceCount];
        int[] rowFrontierPeaks = new int[sourceCount];
        long requestWorkStates = 0L;
        long requestSettledStates = 0L;

        for (int i = 0; i < sourceCount; i++) {
            int sourceNodeId = request.sourceNodeIds()[i];
            long rowWork = 0L;
            long rowSettled = 0L;
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
                rowWork += Math.max(0, plan.settledStates());
                rowSettled += Math.max(0, plan.settledStates());
            }
            rowWorkStates[i] = toBoundedInt(rowWork);
            rowSettledStates[i] = toBoundedInt(rowSettled);
            requestWorkStates += rowWork;
            requestSettledStates += rowSettled;
        }

        MatrixExecutionStats executionStats = MatrixExecutionStats.of(
                requestWorkStates,
                requestSettledStates,
                0,
                0,
                rowWorkStates,
                rowSettledStates,
                rowLabelPeaks,
                rowFrontierPeaks
        );
        return new MatrixPlan(reachable, costs, arrivals, PAIRWISE_COMPATIBILITY_NOTE, executionStats);
    }

    private static int toBoundedInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }
}
