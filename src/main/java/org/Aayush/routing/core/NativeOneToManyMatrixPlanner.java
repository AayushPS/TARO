package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;

import java.util.Arrays;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Native one-to-many matrix planner.
 *
 * <p>For each source row, the planner runs one edge-based expansion and writes
 * results for all requested targets. It supports:
 * <ul>
 * <li>native one-to-many Dijkstra for {@code DIJKSTRA + NONE},</li>
 * <li>native one-to-many A* for bounded-target {@code A_STAR},</li>
 * <li>bounded batched compatibility fallback for oversized A* target sets.</li>
 * </ul></p>
 */
final class NativeOneToManyMatrixPlanner implements MatrixPlanner {
    static final String NATIVE_IMPLEMENTATION_NOTE =
            "Native one-to-many Dijkstra matrix planner.";
    static final String NATIVE_A_STAR_IMPLEMENTATION_NOTE =
            "Native one-to-many A* matrix planner.";
    static final String BATCHED_A_STAR_COMPATIBILITY_NOTE =
            "Compatibility mode: bounded batched pairwise A* matrix expansion via RouteCore.computeInternal.";

    private static final int NO_LABEL = -1;
    private static final String PROP_MAX_NATIVE_A_STAR_TARGETS = "taro.routing.stage14.maxNativeAStarTargets";
    private static final String PROP_A_STAR_BATCH_TARGETS = "taro.routing.stage14.aStarFallbackBatchTargets";
    private static final int DEFAULT_MAX_NATIVE_A_STAR_TARGETS = 96;
    private static final int DEFAULT_A_STAR_BATCH_TARGETS = 32;

    private final MatrixPlanner compatibilityPlanner;
    private final MatrixSearchBudget searchBudget;
    private final TerminationPolicy terminationPolicy;
    private final int maxNativeAStarTargets;
    private final int aStarFallbackBatchTargets;
    private final ThreadLocal<MatrixQueryContext> queryContext =
            ThreadLocal.withInitial(MatrixQueryContext::new);

    NativeOneToManyMatrixPlanner() {
        this(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.defaults(),
                TerminationPolicy.defaults(),
                readBoundedInt(PROP_MAX_NATIVE_A_STAR_TARGETS, DEFAULT_MAX_NATIVE_A_STAR_TARGETS),
                readBoundedInt(PROP_A_STAR_BATCH_TARGETS, DEFAULT_A_STAR_BATCH_TARGETS)
        );
    }

    NativeOneToManyMatrixPlanner(
            MatrixPlanner compatibilityPlanner,
            MatrixSearchBudget searchBudget,
            TerminationPolicy terminationPolicy
    ) {
        this(
                compatibilityPlanner,
                searchBudget,
                terminationPolicy,
                DEFAULT_MAX_NATIVE_A_STAR_TARGETS,
                DEFAULT_A_STAR_BATCH_TARGETS
        );
    }

    NativeOneToManyMatrixPlanner(
            MatrixPlanner compatibilityPlanner,
            MatrixSearchBudget searchBudget,
            TerminationPolicy terminationPolicy,
            int maxNativeAStarTargets,
            int aStarFallbackBatchTargets
    ) {
        this.compatibilityPlanner = Objects.requireNonNull(compatibilityPlanner, "compatibilityPlanner");
        this.searchBudget = Objects.requireNonNull(searchBudget, "searchBudget");
        this.terminationPolicy = Objects.requireNonNull(terminationPolicy, "terminationPolicy");
        this.maxNativeAStarTargets = maxNativeAStarTargets <= 0 ? Integer.MAX_VALUE : maxNativeAStarTargets;
        this.aStarFallbackBatchTargets = Math.max(1, aStarFallbackBatchTargets);
    }

    /**
     * Computes a matrix for a normalized request, choosing native or compatibility mode.
     */
    @Override
    public MatrixPlan compute(RouteCore routeCore, InternalMatrixRequest request) {
        Objects.requireNonNull(routeCore, "routeCore");
        Objects.requireNonNull(request, "request");

        if (request.algorithm() == RoutingAlgorithm.DIJKSTRA) {
            if (request.heuristicType() != HeuristicType.NONE) {
                return compatibilityPlanner.compute(routeCore, request);
            }
            return computeNative(
                    routeCore.edgeGraphContract(),
                    routeCore.costEngineContract(),
                    request,
                    null,
                    NATIVE_IMPLEMENTATION_NOTE
            );
        }

        if (request.algorithm() != RoutingAlgorithm.A_STAR) {
            return compatibilityPlanner.compute(routeCore, request);
        }

        MatrixTargetIndex targetIndex = new MatrixTargetIndex(request.targetNodeIds());
        if (targetIndex.uniqueTargetCount() > maxNativeAStarTargets) {
            return computeBatchedAStarCompatibility(routeCore, request);
        }

        GoalBoundHeuristic[] targetHeuristics = bindTargetHeuristics(
                routeCore,
                request.heuristicType(),
                targetIndex
        );
        return computeNative(
                routeCore.edgeGraphContract(),
                routeCore.costEngineContract(),
                request,
                targetHeuristics,
                NATIVE_A_STAR_IMPLEMENTATION_NOTE
        );
    }

    /**
     * Executes native one-to-many matrix planning for all source rows.
     */
    private MatrixPlan computeNative(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            InternalMatrixRequest request,
            GoalBoundHeuristic[] targetHeuristics,
            String implementationNote
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
        if (targetHeuristics != null && targetHeuristics.length != targetIndex.uniqueTargetCount()) {
            throw new IllegalArgumentException("targetHeuristics length must match uniqueTargetCount");
        }
        MatrixQueryContext context = queryContext.get();
        long[] requestWorkStates = new long[]{0L};
        long requestSettledStates = 0L;
        int requestLabelPeak = 0;
        int requestFrontierPeak = 0;
        var temporalContext = request.temporalContext();
        var transitionContext = request.transitionContext();

        for (int row = 0; row < sourceCount; row++) {
            computeRow(
                    edgeGraph,
                    costEngine,
                    request.sourceNodeIds()[row],
                    request.departureTicks(),
                    temporalContext,
                    transitionContext,
                    targetIndex,
                    targetHeuristics,
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
                implementationNote,
                executionStats
        );
    }

    /**
     * Executes A* compatibility planner in bounded target batches.
     */
    private MatrixPlan computeBatchedAStarCompatibility(RouteCore routeCore, InternalMatrixRequest request) {
        int sourceCount = request.sourceNodeIds().length;
        int targetCount = request.targetNodeIds().length;

        boolean[][] reachable = new boolean[sourceCount][targetCount];
        float[][] totalCosts = new float[sourceCount][targetCount];
        long[][] arrivalTicks = new long[sourceCount][targetCount];
        int[] rowWorkStates = new int[sourceCount];
        int[] rowSettledStates = new int[sourceCount];
        int[] rowLabelPeaks = new int[sourceCount];
        int[] rowFrontierPeaks = new int[sourceCount];

        long requestWorkStates = 0L;
        long requestSettledStates = 0L;
        int requestLabelPeak = 0;
        int requestFrontierPeak = 0;

        for (int start = 0; start < targetCount; start += aStarFallbackBatchTargets) {
            int end = Math.min(targetCount, start + aStarFallbackBatchTargets);
            int[] batchTargets = Arrays.copyOfRange(request.targetNodeIds(), start, end);
            InternalMatrixRequest batchRequest = new InternalMatrixRequest(
                    request.sourceNodeIds(),
                    batchTargets,
                    request.departureTicks(),
                    request.algorithm(),
                    request.heuristicType(),
                    request.temporalContext(),
                    request.transitionContext()
            );

            MatrixPlan batchPlan = compatibilityPlanner.compute(routeCore, batchRequest);
            copyBatchColumns(batchPlan, start, reachable, totalCosts, arrivalTicks);

            MatrixExecutionStats batchStats = batchPlan.executionStats();
            requestWorkStates += batchStats.requestWorkStates();
            requestSettledStates += batchStats.requestSettledStates();
            requestLabelPeak = Math.max(requestLabelPeak, batchStats.requestLabelPeak());
            requestFrontierPeak = Math.max(requestFrontierPeak, batchStats.requestFrontierPeak());

            int[] batchRowWork = batchStats.rowWorkStates();
            int[] batchRowSettled = batchStats.rowSettledStates();
            int[] batchRowLabelPeaks = batchStats.rowLabelPeaks();
            int[] batchRowFrontierPeaks = batchStats.rowFrontierPeaks();
            for (int row = 0; row < sourceCount; row++) {
                rowWorkStates[row] = saturatingAddInt(rowWorkStates[row], batchRowWork[row]);
                rowSettledStates[row] = saturatingAddInt(rowSettledStates[row], batchRowSettled[row]);
                rowLabelPeaks[row] = Math.max(rowLabelPeaks[row], batchRowLabelPeaks[row]);
                rowFrontierPeaks[row] = Math.max(rowFrontierPeaks[row], batchRowFrontierPeaks[row]);
            }
        }

        MatrixExecutionStats executionStats = MatrixExecutionStats.of(
                requestWorkStates,
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
                BATCHED_A_STAR_COMPATIBILITY_NOTE,
                executionStats
        );
    }

    /**
     * Computes one source row against deduplicated targets.
     */
    private void computeRow(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            int sourceNodeId,
            long departureTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext,
            MatrixTargetIndex targetIndex,
            GoalBoundHeuristic[] targetHeuristics,
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
            float transitionCost = costEngine.computeEdgeCost(
                    edgeId,
                    CostEngine.NO_PREDECESSOR,
                    departureTicks,
                    temporalContext,
                    transitionContext
            );
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

            int settledNode = edgeGraph.getEdgeDestination(edgeId);
            double priority = computePriority(targetHeuristics, context, settledNode, transitionCost);
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
                float transitionCost = costEngine.computeEdgeCost(
                        nextEdgeId,
                        settledEdgeId,
                        settledArrival,
                        temporalContext,
                        transitionContext
                );
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

                int nextNode = edgeGraph.getEdgeDestination(nextEdgeId);
                double priority = computePriority(targetHeuristics, context, nextNode, nextCost);
                ensureValidPriority(priority);
                frontier.add(new ForwardFrontierState(nextLabelId, nextEdgeId, nextArrival, priority));
                context.observeFrontierSize();
                searchBudget.checkRowFrontierSize(frontier.size());
            }
        }
    }

    /**
     * Returns true when all targets are resolved and frontier cannot improve them further.
     */
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

    /**
     * Inserts a label if it is non-dominated for the same edge.
     */
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

    private double computePriority(
            GoalBoundHeuristic[] targetHeuristics,
            MatrixQueryContext context,
            int nextNodeId,
            float gScore
    ) {
        if (targetHeuristics == null) {
            return gScore;
        }
        double heuristicFloor = minRemainingTargetHeuristic(targetHeuristics, context, nextNodeId);
        return gScore + heuristicFloor;
    }

    private static double minRemainingTargetHeuristic(
            GoalBoundHeuristic[] targetHeuristics,
            MatrixQueryContext context,
            int nodeId
    ) {
        if (context.unresolvedTargets() == 0) {
            return 0.0d;
        }

        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < targetHeuristics.length; i++) {
            if (context.isTargetReached(i)) {
                continue;
            }
            double estimate = targetHeuristics[i].estimateFromNode(nodeId);
            if (!Double.isFinite(estimate) || estimate < 0.0d) {
                estimate = 0.0d;
            }
            if (estimate < best) {
                best = estimate;
            }
        }

        if (!Double.isFinite(best)) {
            return 0.0d;
        }
        return best;
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

    private static int saturatingAddInt(int a, int b) {
        if (b <= 0) {
            return a;
        }
        if (a >= Integer.MAX_VALUE - b) {
            return Integer.MAX_VALUE;
        }
        return a + b;
    }

    /**
     * Applies shared numeric guardrails to frontier priorities.
     */
    private void ensureValidPriority(double priority) {
        // Uses TerminationPolicy numeric guardrails without changing stop behavior.
        terminationPolicy.shouldTerminate(Float.POSITIVE_INFINITY, priority);
    }

    private GoalBoundHeuristic[] bindTargetHeuristics(
            RouteCore routeCore,
            HeuristicType heuristicType,
            MatrixTargetIndex targetIndex
    ) {
        GoalBoundHeuristic[] heuristics = new GoalBoundHeuristic[targetIndex.uniqueTargetCount()];
        for (int i = 0; i < heuristics.length; i++) {
            heuristics[i] = routeCore.bindGoalHeuristicContract(heuristicType, targetIndex.uniqueTargetNodeId(i));
        }
        return heuristics;
    }

    private static void copyBatchColumns(
            MatrixPlan batchPlan,
            int targetColumnOffset,
            boolean[][] reachable,
            float[][] totalCosts,
            long[][] arrivalTicks
    ) {
        boolean[][] batchReachable = batchPlan.reachable();
        float[][] batchCosts = batchPlan.totalCosts();
        long[][] batchArrivals = batchPlan.arrivalTicks();
        for (int row = 0; row < reachable.length; row++) {
            int batchWidth = batchReachable[row].length;
            System.arraycopy(batchReachable[row], 0, reachable[row], targetColumnOffset, batchWidth);
            System.arraycopy(batchCosts[row], 0, totalCosts[row], targetColumnOffset, batchWidth);
            System.arraycopy(batchArrivals[row], 0, arrivalTicks[row], targetColumnOffset, batchWidth);
        }
    }

    private static int readBoundedInt(String property, int defaultValue) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Materializes row outputs from deduplicated-target buffers into original column order.
     */
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
