package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;

import java.util.PriorityQueue;

/**
 * Stage 12 edge-based shortest path planner.
 */
final class EdgeBasedRoutePlanner implements RoutePlanner {
    private static final float INF = Float.POSITIVE_INFINITY;
    private static final int NO_LABEL = -1;

    private final boolean useHeuristic;

    EdgeBasedRoutePlanner(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    @Override
    public InternalRoutePlan compute(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            GoalBoundHeuristic heuristic,
            InternalRouteRequest request
    ) {
        int sourceNodeId = request.sourceNodeId();
        int targetNodeId = request.targetNodeId();
        long departureTicks = request.departureTicks();

        if (sourceNodeId == targetNodeId) {
            return new InternalRoutePlan(true, 0.0f, departureTicks, 0, new int[]{sourceNodeId});
        }

        LabelStore labelStore = new LabelStore();
        IntArrayList[] activeLabelsByEdge = new IntArrayList[edgeGraph.edgeCount()];
        PriorityQueue<FrontierState> frontier = new PriorityQueue<>();
        EdgeGraph.EdgeIterator iterator = edgeGraph.iterator();

        iterator.resetForNode(sourceNodeId);
        while (iterator.hasNext()) {
            int edgeId = iterator.next();
            float transitionCost = costEngine.computeEdgeCost(edgeId, CostEngine.NO_PREDECESSOR, departureTicks);
            if (!Float.isFinite(transitionCost)) {
                continue;
            }
            long arrivalTicks = saturatingAdd(departureTicks, toArrivalTicks(transitionCost));
            int labelId = addLabelIfNonDominated(
                    edgeId,
                    transitionCost,
                    arrivalTicks,
                    NO_LABEL,
                    activeLabelsByEdge,
                    labelStore
            );
            if (labelId != NO_LABEL) {
                double priority = computePriority(edgeGraph, heuristic, edgeId, transitionCost);
                frontier.add(new FrontierState(labelId, edgeId, transitionCost, arrivalTicks, priority));
            }
        }

        int settledStates = 0;
        int bestGoalLabelId = NO_LABEL;
        float bestGoalCost = INF;
        long bestGoalArrival = Long.MAX_VALUE;

        while (!frontier.isEmpty()) {
            FrontierState state = frontier.poll();
            int labelId = state.labelId();
            if (!labelStore.isActive(labelId)) {
                continue;
            }
            settledStates++;

            if (bestGoalLabelId != NO_LABEL && Double.compare(state.priority(), bestGoalCost) > 0) {
                break;
            }

            int edgeId = labelStore.edgeId(labelId);
            float bestG = labelStore.gScore(labelId);
            long bestArrival = labelStore.arrivalTicks(labelId);
            int settledNode = edgeGraph.getEdgeDestination(edgeId);
            if (settledNode == targetNodeId) {
                if (isBetter(bestG, bestArrival, bestGoalCost, bestGoalArrival)) {
                    bestGoalCost = bestG;
                    bestGoalArrival = bestArrival;
                    bestGoalLabelId = labelId;
                }
                continue;
            }

            iterator.reset(edgeId);
            while (iterator.hasNext()) {
                int nextEdgeId = iterator.next();
                float transitionCost = costEngine.computeEdgeCost(nextEdgeId, edgeId, bestArrival);
                if (!Float.isFinite(transitionCost)) {
                    continue;
                }

                float nextG = bestG + transitionCost;
                if (!Float.isFinite(nextG)) {
                    continue;
                }
                long nextArrival = saturatingAdd(bestArrival, toArrivalTicks(transitionCost));
                int nextLabelId = addLabelIfNonDominated(
                        nextEdgeId,
                        nextG,
                        nextArrival,
                        labelId,
                        activeLabelsByEdge,
                        labelStore
                );
                if (nextLabelId == NO_LABEL) {
                    continue;
                }

                double priority = computePriority(edgeGraph, heuristic, nextEdgeId, nextG);
                frontier.add(new FrontierState(nextLabelId, nextEdgeId, nextG, nextArrival, priority));
            }
        }

        if (bestGoalLabelId != NO_LABEL) {
            int[] path = buildNodePath(edgeGraph, labelStore, sourceNodeId, bestGoalLabelId);
            return new InternalRoutePlan(true, bestGoalCost, bestGoalArrival, settledStates, path);
        }
        return InternalRoutePlan.unreachable(departureTicks, settledStates);
    }

    private static boolean isBetter(float newCost, long newArrival, float currentCost, long currentArrival) {
        if (newCost < currentCost) {
            return true;
        }
        return Float.compare(newCost, currentCost) == 0 && newArrival < currentArrival;
    }

    private static boolean dominates(float lhsCost, long lhsArrival, float rhsCost, long rhsArrival) {
        return lhsCost <= rhsCost && lhsArrival <= rhsArrival;
    }

    private static int addLabelIfNonDominated(
            int edgeId,
            float gScore,
            long arrivalTicks,
            int predecessorLabelId,
            IntArrayList[] activeLabelsByEdge,
            LabelStore labelStore
    ) {
        IntArrayList activeLabels = activeLabelsByEdge[edgeId];
        if (activeLabels == null) {
            activeLabels = new IntArrayList();
            activeLabelsByEdge[edgeId] = activeLabels;
        }

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

    private double computePriority(EdgeGraph edgeGraph, GoalBoundHeuristic heuristic, int edgeId, float gScore) {
        if (!useHeuristic) {
            return gScore;
        }
        int nextNodeId = edgeGraph.getEdgeDestination(edgeId);
        double estimate = heuristic.estimateFromNode(nextNodeId);
        if (!Double.isFinite(estimate) || estimate < 0.0d) {
            estimate = 0.0d;
        }
        return gScore + estimate;
    }

    private static int[] buildNodePath(
            EdgeGraph edgeGraph,
            LabelStore labelStore,
            int sourceNodeId,
            int terminalLabelId
    ) {
        IntArrayList edgePathReversed = new IntArrayList();
        int cursor = terminalLabelId;
        while (cursor != NO_LABEL) {
            edgePathReversed.add(labelStore.edgeId(cursor));
            cursor = labelStore.predecessorLabelId(cursor);
        }

        int pathNodeCount = edgePathReversed.size() + 1;
        int[] nodePath = new int[pathNodeCount];
        nodePath[0] = sourceNodeId;

        int nodeIndex = 1;
        for (int i = edgePathReversed.size() - 1; i >= 0; i--) {
            int edgeId = edgePathReversed.getInt(i);
            nodePath[nodeIndex++] = edgeGraph.getEdgeDestination(edgeId);
        }
        return nodePath;
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

    private static final class LabelStore {
        private final IntArrayList edgeIdByLabel = new IntArrayList();
        private final FloatArrayList gScoreByLabel = new FloatArrayList();
        private final LongArrayList arrivalByLabel = new LongArrayList();
        private final IntArrayList predecessorLabelByLabel = new IntArrayList();
        private final BooleanArrayList activeByLabel = new BooleanArrayList();

        int add(int edgeId, float gScore, long arrivalTicks, int predecessorLabelId) {
            int labelId = edgeIdByLabel.size();
            edgeIdByLabel.add(edgeId);
            gScoreByLabel.add(gScore);
            arrivalByLabel.add(arrivalTicks);
            predecessorLabelByLabel.add(predecessorLabelId);
            activeByLabel.add(true);
            return labelId;
        }

        int edgeId(int labelId) {
            return edgeIdByLabel.getInt(labelId);
        }

        float gScore(int labelId) {
            return gScoreByLabel.getFloat(labelId);
        }

        long arrivalTicks(int labelId) {
            return arrivalByLabel.getLong(labelId);
        }

        int predecessorLabelId(int labelId) {
            return predecessorLabelByLabel.getInt(labelId);
        }

        boolean isActive(int labelId) {
            return activeByLabel.getBoolean(labelId);
        }

        void deactivate(int labelId) {
            activeByLabel.set(labelId, false);
        }
    }

    private record FrontierState(
            int labelId,
            int edgeId,
            float gScore,
            long arrivalTicks,
            double priority
    ) implements Comparable<FrontierState> {
        @Override
        public int compareTo(FrontierState other) {
            int byPriority = Double.compare(this.priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            int byArrival = Long.compare(this.arrivalTicks, other.arrivalTicks);
            if (byArrival != 0) {
                return byArrival;
            }
            int byEdge = Integer.compare(this.edgeId, other.edgeId);
            if (byEdge != 0) {
                return byEdge;
            }
            return Integer.compare(this.labelId, other.labelId);
        }
    }
}
