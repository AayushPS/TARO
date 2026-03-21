package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Direct multi-scenario planner used for expected-ETA and robust/P90 route selection.
 */
final class FutureRouteObjectivePlanner {
    private static final double PROBABILITY_TOLERANCE = 1.0e-6d;
    private static final GoalBoundHeuristic ZERO_HEURISTIC = nodeId -> 0.0d;
    private static final int NO_LABEL = -1;

    private final SearchBudget searchBudget;
    private final PathEvaluator pathEvaluator;
    private final ThreadLocal<QueryContext> queryContext = ThreadLocal.withInitial(QueryContext::new);

    FutureRouteObjectivePlanner() {
        this(SearchBudget.defaults(), new PathEvaluator());
    }

    FutureRouteObjectivePlanner(SearchBudget searchBudget, PathEvaluator pathEvaluator) {
        this.searchBudget = Objects.requireNonNull(searchBudget, "searchBudget");
        this.pathEvaluator = Objects.requireNonNull(pathEvaluator, "pathEvaluator");
    }

    InternalRoutePlan compute(
            RouteCore routeCore,
            InternalRouteRequest request,
            List<ScenarioCostSurface> scenarios,
            ObjectiveMode objectiveMode
    ) {
        RouteCore nonNullRouteCore = Objects.requireNonNull(routeCore, "routeCore");
        InternalRouteRequest nonNullRequest = Objects.requireNonNull(request, "request");
        List<ScenarioCostSurface> nonNullScenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        ObjectiveMode nonNullObjectiveMode = Objects.requireNonNull(objectiveMode, "objectiveMode");
        if (nonNullScenarios.isEmpty()) {
            throw new IllegalArgumentException("scenarios must be non-empty");
        }

        EdgeGraph edgeGraph = nonNullRouteCore.edgeGraphContract();
        long departureTicks = nonNullRequest.departureTicks();
        int sourceNodeId = nonNullRequest.sourceNodeId();
        int targetNodeId = nonNullRequest.targetNodeId();
        if (sourceNodeId == targetNodeId) {
            return new InternalRoutePlan(true, 0.0f, departureTicks, 0, new int[]{sourceNodeId}, new int[0]);
        }

        double[] probabilities = probabilities(nonNullScenarios);
        GoalBoundHeuristic heuristic = resolveGoalBoundHeuristic(nonNullRouteCore, nonNullRequest);

        QueryContext context = queryContext.get();
        context.reset();
        AggregateLabelStore labelStore = context.labelStore();
        PriorityQueue<AggregateFrontierState> frontier = context.frontier();

        EdgeGraph.EdgeIterator iterator = edgeGraph.iterator();
        iterator.resetForNode(sourceNodeId);

        int settledStates = 0;
        int workStates = 0;
        int bestGoalLabelId = NO_LABEL;
        double bestGoalObjective = Double.POSITIVE_INFINITY;
        long bestGoalArrival = Long.MAX_VALUE;

        while (iterator.hasNext()) {
            int edgeId = iterator.next();
            float[] scenarioCosts = new float[nonNullScenarios.size()];
            long[] scenarioArrivals = new long[nonNullScenarios.size()];
            computeInitialEdgeSamples(nonNullScenarios, nonNullRequest, departureTicks, edgeId, scenarioCosts, scenarioArrivals);
            double objectiveValue = objectiveValue(scenarioCosts, probabilities, nonNullObjectiveMode);
            if (!Double.isFinite(objectiveValue)) {
                continue;
            }
            int targetNode = edgeGraph.getEdgeDestination(edgeId);
            double priority = priority(objectiveValue, heuristic, targetNode);
            if (!Double.isFinite(priority)) {
                continue;
            }

            int labelId = addLabelIfNonDominated(
                    context,
                    edgeId,
                    scenarioCosts,
                    scenarioArrivals,
                    objectiveValue,
                    NO_LABEL,
                    labelStore
            );
            if (labelId == NO_LABEL) {
                continue;
            }
            searchBudget.checkLabelCount(labelStore.size());
            frontier.add(new AggregateFrontierState(labelId, edgeId, maxArrival(scenarioArrivals), priority));
            searchBudget.checkFrontierSize(frontier.size());
        }

        while (!frontier.isEmpty()) {
            AggregateFrontierState state = frontier.poll();
            workStates++;
            searchBudget.checkSettledStates(workStates);

            int labelId = state.labelId();
            if (!labelStore.isActive(labelId)) {
                continue;
            }

            settledStates++;
            if (Double.isFinite(bestGoalObjective) && bestGoalObjective <= state.priority() + PROBABILITY_TOLERANCE) {
                break;
            }

            int settledEdgeId = labelStore.edgeId(labelId);
            int settledNode = edgeGraph.getEdgeDestination(settledEdgeId);
            double settledObjective = labelStore.objectiveValue(labelId);
            long settledArrival = labelStore.maxArrival(labelId);

            if (settledNode == targetNodeId) {
                if (isBetterGoal(settledObjective, settledArrival, settledEdgeId, bestGoalObjective, bestGoalArrival,
                        bestGoalLabelId == NO_LABEL ? Integer.MAX_VALUE : labelStore.edgeId(bestGoalLabelId))) {
                    bestGoalObjective = settledObjective;
                    bestGoalArrival = settledArrival;
                    bestGoalLabelId = labelId;
                }
                continue;
            }

            iterator.reset(settledEdgeId);
            while (iterator.hasNext()) {
                int nextEdgeId = iterator.next();
                float[] nextScenarioCosts = labelStore.copyScenarioCosts(labelId);
                long[] nextScenarioArrivals = labelStore.copyScenarioArrivals(labelId);
                extendSamples(nonNullScenarios, nonNullRequest, settledEdgeId, nextEdgeId, nextScenarioCosts, nextScenarioArrivals);

                double objectiveValue = objectiveValue(nextScenarioCosts, probabilities, nonNullObjectiveMode);
                if (!Double.isFinite(objectiveValue)) {
                    continue;
                }

                int nextNodeId = edgeGraph.getEdgeDestination(nextEdgeId);
                double priority = priority(objectiveValue, heuristic, nextNodeId);
                if (!Double.isFinite(priority)) {
                    continue;
                }
                if (Double.isFinite(bestGoalObjective) && priority > bestGoalObjective + PROBABILITY_TOLERANCE) {
                    continue;
                }

                int nextLabelId = addLabelIfNonDominated(
                        context,
                        nextEdgeId,
                        nextScenarioCosts,
                        nextScenarioArrivals,
                        objectiveValue,
                        labelId,
                        labelStore
                );
                if (nextLabelId == NO_LABEL) {
                    continue;
                }
                searchBudget.checkLabelCount(labelStore.size());
                frontier.add(new AggregateFrontierState(nextLabelId, nextEdgeId, maxArrival(nextScenarioArrivals), priority));
                searchBudget.checkFrontierSize(frontier.size());
            }
        }

        if (bestGoalLabelId == NO_LABEL) {
            return InternalRoutePlan.unreachable(departureTicks, settledStates);
        }

        int[] edgePath = buildEdgePath(labelStore, bestGoalLabelId);
        int[] nodePath = pathEvaluator.toNodePath(edgeGraph, sourceNodeId, edgePath);
        long arrivalTicks = labelStore.maxArrival(bestGoalLabelId);
        double objectiveValue = labelStore.objectiveValue(bestGoalLabelId);
        float totalCost = !Double.isFinite(objectiveValue) || objectiveValue > Float.MAX_VALUE
                ? Float.POSITIVE_INFINITY
                : (float) objectiveValue;
        return new InternalRoutePlan(true, totalCost, arrivalTicks, settledStates, nodePath, edgePath);
    }

    private static GoalBoundHeuristic resolveGoalBoundHeuristic(RouteCore routeCore, InternalRouteRequest request) {
        if (request.algorithm() == RoutingAlgorithm.DIJKSTRA) {
            return ZERO_HEURISTIC;
        }
        return routeCore.bindGoalHeuristicContract(request.heuristicType(), request.targetNodeId());
    }

    private static double[] probabilities(List<ScenarioCostSurface> scenarios) {
        double[] probabilities = new double[scenarios.size()];
        for (int i = 0; i < scenarios.size(); i++) {
            probabilities[i] = scenarios.get(i).probability();
        }
        return probabilities;
    }

    private static void computeInitialEdgeSamples(
            List<ScenarioCostSurface> scenarios,
            InternalRouteRequest request,
            long departureTicks,
            int edgeId,
            float[] scenarioCosts,
            long[] scenarioArrivals
    ) {
        for (int i = 0; i < scenarios.size(); i++) {
            CostEngine costEngine = scenarios.get(i).costEngine();
            try {
                float transitionCost = costEngine.computeEdgeCost(
                        edgeId,
                        CostEngine.NO_PREDECESSOR,
                        departureTicks,
                        request.temporalContext(),
                        request.transitionContext()
                );
                if (!Float.isFinite(transitionCost)) {
                    scenarioCosts[i] = Float.POSITIVE_INFINITY;
                    scenarioArrivals[i] = Long.MAX_VALUE;
                    continue;
                }
                scenarioCosts[i] = transitionCost;
                scenarioArrivals[i] = saturatingAdd(departureTicks, toArrivalTicks(transitionCost));
            } catch (RuntimeException ex) {
                scenarioCosts[i] = Float.POSITIVE_INFINITY;
                scenarioArrivals[i] = Long.MAX_VALUE;
            }
        }
    }

    private static void extendSamples(
            List<ScenarioCostSurface> scenarios,
            InternalRouteRequest request,
            int predecessorEdgeId,
            int edgeId,
            float[] scenarioCosts,
            long[] scenarioArrivals
    ) {
        for (int i = 0; i < scenarios.size(); i++) {
            if (!Float.isFinite(scenarioCosts[i]) || scenarioArrivals[i] == Long.MAX_VALUE) {
                continue;
            }
            CostEngine costEngine = scenarios.get(i).costEngine();
            try {
                float transitionCost = costEngine.computeEdgeCost(
                        edgeId,
                        predecessorEdgeId,
                        scenarioArrivals[i],
                        request.temporalContext(),
                        request.transitionContext()
                );
                if (!Float.isFinite(transitionCost)) {
                    scenarioCosts[i] = Float.POSITIVE_INFINITY;
                    scenarioArrivals[i] = Long.MAX_VALUE;
                    continue;
                }
                float nextCost = scenarioCosts[i] + transitionCost;
                if (!Float.isFinite(nextCost)) {
                    scenarioCosts[i] = Float.POSITIVE_INFINITY;
                    scenarioArrivals[i] = Long.MAX_VALUE;
                    continue;
                }
                scenarioCosts[i] = nextCost;
                scenarioArrivals[i] = saturatingAdd(scenarioArrivals[i], toArrivalTicks(transitionCost));
            } catch (RuntimeException ex) {
                scenarioCosts[i] = Float.POSITIVE_INFINITY;
                scenarioArrivals[i] = Long.MAX_VALUE;
            }
        }
    }

    private static double priority(double objectiveValue, GoalBoundHeuristic heuristic, int nodeId) {
        double heuristicValue = heuristic.estimateFromNode(nodeId);
        if (!Double.isFinite(heuristicValue) || heuristicValue < 0.0d) {
            heuristicValue = 0.0d;
        }
        double priority = objectiveValue + heuristicValue;
        return priority < 0.0d ? Double.POSITIVE_INFINITY : priority;
    }

    private static int addLabelIfNonDominated(
            QueryContext context,
            int edgeId,
            float[] scenarioCosts,
            long[] scenarioArrivals,
            double objectiveValue,
            int predecessorLabelId,
            AggregateLabelStore labelStore
    ) {
        IntArrayList activeLabels = context.activeLabelsForEdge(edgeId);
        int index = 0;
        while (index < activeLabels.size()) {
            int activeLabelId = activeLabels.getInt(index);
            if (!labelStore.isActive(activeLabelId)) {
                activeLabels.removeInt(index);
                continue;
            }
            if (dominates(
                    labelStore.scenarioCosts(activeLabelId),
                    labelStore.scenarioArrivals(activeLabelId),
                    scenarioCosts,
                    scenarioArrivals
            )) {
                return NO_LABEL;
            }
            if (dominates(
                    scenarioCosts,
                    scenarioArrivals,
                    labelStore.scenarioCosts(activeLabelId),
                    labelStore.scenarioArrivals(activeLabelId)
            )) {
                labelStore.deactivate(activeLabelId);
                activeLabels.removeInt(index);
                continue;
            }
            index++;
        }

        int labelId = labelStore.add(edgeId, scenarioCosts, scenarioArrivals, objectiveValue, predecessorLabelId);
        activeLabels.add(labelId);
        return labelId;
    }

    private static boolean dominates(float[] lhsCosts, long[] lhsArrivals, float[] rhsCosts, long[] rhsArrivals) {
        boolean strict = false;
        for (int i = 0; i < lhsCosts.length; i++) {
            int byCost = Float.compare(lhsCosts[i], rhsCosts[i]);
            int byArrival = Long.compare(lhsArrivals[i], rhsArrivals[i]);
            if (byCost > 0 || byArrival > 0) {
                return false;
            }
            if (byCost < 0 || byArrival < 0) {
                strict = true;
            }
        }
        return strict;
    }

    private static boolean isBetterGoal(
            double newObjective,
            long newArrival,
            int newEdgeId,
            double currentObjective,
            long currentArrival,
            int currentEdgeId
    ) {
        if (newObjective + PROBABILITY_TOLERANCE < currentObjective) {
            return true;
        }
        if (Math.abs(newObjective - currentObjective) > PROBABILITY_TOLERANCE) {
            return false;
        }
        if (newArrival < currentArrival) {
            return true;
        }
        if (newArrival > currentArrival) {
            return false;
        }
        return newEdgeId < currentEdgeId;
    }

    private static double objectiveValue(float[] scenarioCosts, double[] probabilities, ObjectiveMode objectiveMode) {
        return switch (objectiveMode) {
            case EXPECTED_ETA -> expectedValue(scenarioCosts, probabilities);
            case ROBUST_P90 -> percentileValue(scenarioCosts, probabilities, 0.90d);
        };
    }

    private static double expectedValue(float[] scenarioCosts, double[] probabilities) {
        double weighted = 0.0d;
        for (int i = 0; i < scenarioCosts.length; i++) {
            float cost = scenarioCosts[i];
            if (!Float.isFinite(cost) && probabilities[i] > 0.0d) {
                return Double.POSITIVE_INFINITY;
            }
            weighted += probabilities[i] * cost;
        }
        return Double.isFinite(weighted) ? weighted : Double.POSITIVE_INFINITY;
    }

    private static double percentileValue(float[] scenarioCosts, double[] probabilities, double percentile) {
        ArrayList<PercentileSample> sorted = new ArrayList<>(scenarioCosts.length);
        for (int i = 0; i < scenarioCosts.length; i++) {
            sorted.add(new PercentileSample(scenarioCosts[i], probabilities[i]));
        }
        sorted.sort((lhs, rhs) -> Float.compare(lhs.cost(), rhs.cost()));

        double totalProbability = 0.0d;
        for (PercentileSample sample : sorted) {
            totalProbability += sample.probability();
        }
        double threshold = percentile * totalProbability;
        double cumulative = 0.0d;
        for (PercentileSample sample : sorted) {
            cumulative += sample.probability();
            if (cumulative + PROBABILITY_TOLERANCE >= threshold) {
                return sample.cost();
            }
        }
        return sorted.get(sorted.size() - 1).cost();
    }

    private static long maxArrival(long[] scenarioArrivals) {
        long max = 0L;
        for (long arrival : scenarioArrivals) {
            if (arrival == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            max = Math.max(max, arrival);
        }
        return max;
    }

    private static int[] buildEdgePath(AggregateLabelStore labelStore, int terminalLabelId) {
        IntArrayList reversed = new IntArrayList();
        int cursor = terminalLabelId;
        while (cursor != NO_LABEL) {
            reversed.add(labelStore.edgeId(cursor));
            cursor = labelStore.predecessorLabelId(cursor);
        }

        int[] edgePath = new int[reversed.size()];
        for (int i = reversed.size() - 1, w = 0; i >= 0; i--, w++) {
            edgePath[w] = reversed.getInt(i);
        }
        return edgePath;
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

    enum ObjectiveMode {
        EXPECTED_ETA,
        ROBUST_P90
    }

    record ScenarioCostSurface(double probability, CostEngine costEngine) {
        ScenarioCostSurface {
            Objects.requireNonNull(costEngine, "costEngine");
        }
    }

    private record PercentileSample(float cost, double probability) {
    }

    private record AggregateFrontierState(
            int labelId,
            int edgeId,
            long maxArrivalTicks,
            double priority
    ) implements Comparable<AggregateFrontierState> {
        @Override
        public int compareTo(AggregateFrontierState other) {
            int byPriority = Double.compare(this.priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            int byArrival = Long.compare(this.maxArrivalTicks, other.maxArrivalTicks);
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

    private static final class QueryContext {
        private final AggregateLabelStore labelStore = new AggregateLabelStore();
        private final IntArrayList touchedEdges = new IntArrayList();
        private final Int2ObjectOpenHashMap<IntArrayList> activeLabelsByEdge = new Int2ObjectOpenHashMap<>();
        private final PriorityQueue<AggregateFrontierState> frontier = new PriorityQueue<>();

        void reset() {
            labelStore.clear();
            for (int i = 0; i < touchedEdges.size(); i++) {
                activeLabelsByEdge.remove(touchedEdges.getInt(i));
            }
            touchedEdges.clear();
            frontier.clear();
        }

        AggregateLabelStore labelStore() {
            return labelStore;
        }

        PriorityQueue<AggregateFrontierState> frontier() {
            return frontier;
        }

        IntArrayList activeLabelsForEdge(int edgeId) {
            IntArrayList labels = activeLabelsByEdge.get(edgeId);
            if (labels == null) {
                labels = new IntArrayList();
                activeLabelsByEdge.put(edgeId, labels);
            }
            if (labels.isEmpty()) {
                touchedEdges.add(edgeId);
            }
            return labels;
        }
    }

    private static final class AggregateLabelStore {
        private final IntArrayList edgeIdByLabel = new IntArrayList();
        private final IntArrayList predecessorLabelByLabel = new IntArrayList();
        private final DoubleArrayList objectiveValueByLabel = new DoubleArrayList();
        private final LongArrayList maxArrivalByLabel = new LongArrayList();
        private final ArrayList<float[]> scenarioCostsByLabel = new ArrayList<>();
        private final ArrayList<long[]> scenarioArrivalsByLabel = new ArrayList<>();
        private final BooleanArrayList activeByLabel = new BooleanArrayList();

        void clear() {
            edgeIdByLabel.clear();
            predecessorLabelByLabel.clear();
            objectiveValueByLabel.clear();
            maxArrivalByLabel.clear();
            scenarioCostsByLabel.clear();
            scenarioArrivalsByLabel.clear();
            activeByLabel.clear();
        }

        int add(int edgeId, float[] scenarioCosts, long[] scenarioArrivals, double objectiveValue, int predecessorLabelId) {
            int labelId = edgeIdByLabel.size();
            edgeIdByLabel.add(edgeId);
            predecessorLabelByLabel.add(predecessorLabelId);
            objectiveValueByLabel.add(objectiveValue);
            maxArrivalByLabel.add(FutureRouteObjectivePlanner.maxArrival(scenarioArrivals));
            scenarioCostsByLabel.add(scenarioCosts.clone());
            scenarioArrivalsByLabel.add(scenarioArrivals.clone());
            activeByLabel.add(true);
            return labelId;
        }

        int size() {
            return edgeIdByLabel.size();
        }

        int edgeId(int labelId) {
            return edgeIdByLabel.getInt(labelId);
        }

        int predecessorLabelId(int labelId) {
            return predecessorLabelByLabel.getInt(labelId);
        }

        double objectiveValue(int labelId) {
            return objectiveValueByLabel.getDouble(labelId);
        }

        long maxArrival(int labelId) {
            return maxArrivalByLabel.getLong(labelId);
        }

        float[] scenarioCosts(int labelId) {
            return scenarioCostsByLabel.get(labelId);
        }

        long[] scenarioArrivals(int labelId) {
            return scenarioArrivalsByLabel.get(labelId);
        }

        float[] copyScenarioCosts(int labelId) {
            return scenarioCosts(labelId).clone();
        }

        long[] copyScenarioArrivals(int labelId) {
            return scenarioArrivals(labelId).clone();
        }

        boolean isActive(int labelId) {
            return activeByLabel.getBoolean(labelId);
        }

        void deactivate(int labelId) {
            activeByLabel.set(labelId, false);
        }
    }
}
