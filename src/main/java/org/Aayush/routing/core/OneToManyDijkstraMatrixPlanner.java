package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Stage 14 native one-to-many matrix planner for DIJKSTRA requests.
 */
final class OneToManyDijkstraMatrixPlanner implements MatrixPlanner {
    static final String STAGE14_NATIVE_IMPLEMENTATION_NOTE =
            "Stage 14 native one-to-many Dijkstra matrix planner.";

    private static final int NO_LABEL = -1;

    private final MatrixPlanner compatibilityPlanner;
    private final MatrixSearchBudget searchBudget;
    private final TerminationPolicy terminationPolicy;
    private final ThreadLocal<MatrixQueryContext> queryContext =
            ThreadLocal.withInitial(MatrixQueryContext::new);

    OneToManyDijkstraMatrixPlanner() {
        this(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.defaults(),
                TerminationPolicy.defaults()
        );
    }

    OneToManyDijkstraMatrixPlanner(
            MatrixPlanner compatibilityPlanner,
            MatrixSearchBudget searchBudget,
            TerminationPolicy terminationPolicy
    ) {
        this.compatibilityPlanner = Objects.requireNonNull(compatibilityPlanner, "compatibilityPlanner");
        this.searchBudget = Objects.requireNonNull(searchBudget, "searchBudget");
        this.terminationPolicy = Objects.requireNonNull(terminationPolicy, "terminationPolicy");
    }

    @Override
    public MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request) {
        Objects.requireNonNull(routeCore, "routeCore");
        Objects.requireNonNull(request, "request");

        if (request.algorithm() != RoutingAlgorithm.DIJKSTRA || request.heuristicType() != HeuristicType.NONE) {
            return compatibilityPlanner.compute(routeCore, request);
        }

        return computeNative(
                routeCore.edgeGraphContract(),
                routeCore.costEngineContract(),
                request
        );
    }

    private MatrixPlan computeNative(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            InternalMatrixRequest request
    ) {
        int sourceCount = request.sourceNodeIds().length;
        int targetCount = request.targetNodeIds().length;

        boolean[][] reachable = new boolean[sourceCount][targetCount];
        float[][] totalCosts = new float[sourceCount][targetCount];
        long[][] arrivalTicks = new long[sourceCount][targetCount];
        int[] rowWorkStates = new int[sourceCount];
        int[] rowSettledStates = new int[sourceCount];
        int[] rowLabelPeaks = new int[sourceCount];
        int[] rowFrontierPeaks = new int[sourceCount];

        MatrixTargetIndex targetIndex = new MatrixTargetIndex(request.targetNodeIds());
        MatrixQueryContext context = queryContext.get();
        long[] requestWorkStates = new long[]{0L};
        long requestSettledStates = 0L;
        int requestLabelPeak = 0;
        int requestFrontierPeak = 0;

        for (int row = 0; row < sourceCount; row++) {
            computeRow(
                    edgeGraph,
                    costEngine,
                    request.sourceNodeIds()[row],
                    request.departureTicks(),
                    targetIndex,
                    context,
                    requestWorkStates
            );
            writeRowOutput(
                    targetIndex,
                    context,
                    request.departureTicks(),
                    reachable[row],
                    totalCosts[row],
                    arrivalTicks[row]
            );
            rowWorkStates[row] = context.rowWorkStates();
            rowSettledStates[row] = context.rowSettledStates();
            rowLabelPeaks[row] = context.rowLabelPeak();
            rowFrontierPeaks[row] = context.rowFrontierPeak();
            requestSettledStates += rowSettledStates[row];
            requestLabelPeak = Math.max(requestLabelPeak, rowLabelPeaks[row]);
            requestFrontierPeak = Math.max(requestFrontierPeak, rowFrontierPeaks[row]);
        }

        MatrixExecutionStats executionStats = MatrixExecutionStats.of(
                requestWorkStates[0],
                requestSettledStates,
                requestLabelPeak,
                requestFrontierPeak,
                rowWorkStates,
                rowSettledStates,
                rowLabelPeaks,
                rowFrontierPeaks
        );
        return new MatrixPlan(
                reachable,
                totalCosts,
                arrivalTicks,
                STAGE14_NATIVE_IMPLEMENTATION_NOTE,
                executionStats
        );
    }

    private void computeRow(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            int sourceNodeId,
            long departureTicks,
            MatrixTargetIndex targetIndex,
            MatrixQueryContext context,
            long[] requestWorkStates
    ) {
        context.resetForRow(targetIndex.uniqueTargetCount());
        int sourceTargetUniqueIndex = targetIndex.uniqueIndexOfNode(sourceNodeId);
        if (sourceTargetUniqueIndex != MatrixTargetIndex.NOT_FOUND) {
            context.updateTargetBest(sourceTargetUniqueIndex, 0.0f, departureTicks);
        }
        if (context.unresolvedTargets() == 0) {
            return;
        }

        DominanceLabelStore labelStore = context.labelStore();
        PriorityQueue<ForwardFrontierState> frontier = context.frontier();
        EdgeGraph.EdgeIterator iterator = edgeGraph.iterator();

        iterator.resetForNode(sourceNodeId);
        while (iterator.hasNext()) {
            int edgeId = iterator.next();
            float transitionCost = costEngine.computeEdgeCost(edgeId, CostEngine.NO_PREDECESSOR, departureTicks);
            if (!Float.isFinite(transitionCost)) {
                continue;
            }
            long arrival = saturatingAdd(departureTicks, toArrivalTicks(transitionCost));
            int labelId = addLabelIfNonDominated(
                    context,
                    edgeId,
                    transitionCost,
                    arrival,
                    NO_LABEL,
                    labelStore
            );
            if (labelId == NO_LABEL) {
                continue;
            }
            context.observeLabelCount();
            searchBudget.checkRowLabelCount(labelStore.size());

            double priority = transitionCost;
            ensureValidPriority(priority);
            frontier.add(new ForwardFrontierState(labelId, edgeId, arrival, priority));
            context.observeFrontierSize();
            searchBudget.checkRowFrontierSize(frontier.size());
        }

        while (!frontier.isEmpty()) {
            if (canTerminateAfterAllTargetsResolved(context, frontier)) {
                break;
            }

            ForwardFrontierState state = frontier.poll();
            int rowWorkStates = context.incrementRowWorkStates();
            requestWorkStates[0]++;
            searchBudget.checkRowWorkStates(rowWorkStates);
            searchBudget.checkRequestWorkStates(requestWorkStates[0]);

            int labelId = state.labelId();
            if (!labelStore.isActive(labelId)) {
                continue;
            }
            context.incrementRowSettledStates();

            int settledEdgeId = labelStore.edgeId(labelId);
            float settledCost = labelStore.gScore(labelId);
            long settledArrival = labelStore.arrivalTicks(labelId);
            int settledNode = edgeGraph.getEdgeDestination(settledEdgeId);

            int targetUniqueIndex = targetIndex.uniqueIndexOfNode(settledNode);
            if (targetUniqueIndex != MatrixTargetIndex.NOT_FOUND) {
                context.updateTargetBest(targetUniqueIndex, settledCost, settledArrival);
            }

            iterator.reset(settledEdgeId);
            while (iterator.hasNext()) {
                int nextEdgeId = iterator.next();
                float transitionCost = costEngine.computeEdgeCost(nextEdgeId, settledEdgeId, settledArrival);
                if (!Float.isFinite(transitionCost)) {
                    continue;
                }

                float nextCost = settledCost + transitionCost;
                if (!Float.isFinite(nextCost)) {
                    continue;
                }
                long nextArrival = saturatingAdd(settledArrival, toArrivalTicks(transitionCost));
                int nextLabelId = addLabelIfNonDominated(
                        context,
                        nextEdgeId,
                        nextCost,
                        nextArrival,
                        labelId,
                        labelStore
                );
                if (nextLabelId == NO_LABEL) {
                    continue;
                }
                context.observeLabelCount();
                searchBudget.checkRowLabelCount(labelStore.size());

                double priority = nextCost;
                ensureValidPriority(priority);
                frontier.add(new ForwardFrontierState(nextLabelId, nextEdgeId, nextArrival, priority));
                context.observeFrontierSize();
                searchBudget.checkRowFrontierSize(frontier.size());
            }
        }
    }

    private boolean canTerminateAfterAllTargetsResolved(
            MatrixQueryContext context,
            PriorityQueue<ForwardFrontierState> frontier
    ) {
        if (context.unresolvedTargets() != 0) {
            return false;
        }
        ForwardFrontierState next = frontier.peek();
        if (next == null) {
            return true;
        }
        return terminationPolicy.shouldTerminate(
                (float) context.maxResolvedTargetCost(),
                next.priority()
        );
    }

    private static int addLabelIfNonDominated(
            MatrixQueryContext context,
            int edgeId,
            float gScore,
            long arrivalTicks,
            int predecessorLabelId,
            DominanceLabelStore labelStore
    ) {
        IntArrayList activeLabels = context.activeLabelsForEdge(edgeId);
        int index = 0;
        while (index < activeLabels.size()) {
            int activeLabelId = activeLabels.getInt(index);
            if (!labelStore.isActive(activeLabelId)) {
                activeLabels.removeInt(index);
                continue;
            }

            float activeCost = labelStore.gScore(activeLabelId);
            long activeArrival = labelStore.arrivalTicks(activeLabelId);
            if (dominates(activeCost, activeArrival, gScore, arrivalTicks)) {
                return NO_LABEL;
            }
            if (dominates(gScore, arrivalTicks, activeCost, activeArrival)) {
                labelStore.deactivate(activeLabelId);
                activeLabels.removeInt(index);
                continue;
            }
            index++;
        }

        int labelId = labelStore.add(edgeId, gScore, arrivalTicks, predecessorLabelId);
        activeLabels.add(labelId);
        return labelId;
    }

    private static boolean dominates(float lhsCost, long lhsArrival, float rhsCost, long rhsArrival) {
        return lhsCost <= rhsCost && lhsArrival <= rhsArrival;
    }

    private static long toArrivalTicks(float transitionCost) {
        if (!Float.isFinite(transitionCost) || transitionCost <= 0.0f) {
            return 0L;
        }
        double ceil = Math.ceil(transitionCost);
        if (ceil >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) ceil;
    }

    private static long saturatingAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private void ensureValidPriority(double priority) {
        // Uses TerminationPolicy numeric guardrails without changing stop behavior.
        terminationPolicy.shouldTerminate(Float.POSITIVE_INFINITY, priority);
    }

    private static void writeRowOutput(
            MatrixTargetIndex targetIndex,
            MatrixQueryContext context,
            long departureTicks,
            boolean[] reachableRow,
            float[] totalCostsRow,
            long[] arrivalTicksRow
    ) {
        for (int column = 0; column < targetIndex.columnCount(); column++) {
            int uniqueIndex = targetIndex.uniqueIndexForColumn(column);
            if (context.isTargetReached(uniqueIndex)) {
                reachableRow[column] = true;
                totalCostsRow[column] = context.targetBestCost(uniqueIndex);
                arrivalTicksRow[column] = context.targetBestArrival(uniqueIndex);
            } else {
                reachableRow[column] = false;
                totalCostsRow[column] = Float.POSITIVE_INFINITY;
                arrivalTicksRow[column] = departureTicks;
            }
        }
    }
}
