package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;
import org.Aayush.routing.profile.ProfileStore;

import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Bidirectional time-dependent A* planner with deterministic guardrails.
 *
 * <p>Execution model:</p>
 * <ul>
 * <li>Forward lane runs exact edge-based expansion using full {@link CostEngine} semantics.</li>
 * <li>Backward lane runs on a reverse adjacency index and accumulates lower-bound distances.</li>
 * <li>Forward priorities use {@code g + max(heuristic, reverseBoundWhenAvailable)}.</li>
 * <li>Termination is delegated to {@link TerminationPolicy} for numeric-safety consistency.</li>
 * </ul>
 */
final class BidirectionalTdAStarPlanner implements RoutePlanner {
    private static final float INF = Float.POSITIVE_INFINITY;
    private static final int NO_LABEL = -1;
    private static final String REASON_GRAPH_CONTRACT_MISMATCH = "H13_GRAPH_CONTRACT_MISMATCH";
    private static final String REASON_PROFILE_CONTRACT_MISMATCH = "H13_PROFILE_CONTRACT_MISMATCH";

    private final EdgeGraph plannerGraphContract;
    private final ProfileStore plannerProfileContract;
    private final ReverseEdgeIndex reverseEdgeIndex;
    private final float[] reverseLowerBoundByEdge;
    private final SearchBudget searchBudget;
    private final TerminationPolicy terminationPolicy;
    private final PathEvaluator pathEvaluator;
    private final ThreadLocal<PlannerQueryContext> queryContext =
            ThreadLocal.withInitial(PlannerQueryContext::new);

    BidirectionalTdAStarPlanner(EdgeGraph edgeGraph, CostEngine costEngine) {
        this(
                edgeGraph,
                costEngine,
                SearchBudget.defaults(),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
    }

    BidirectionalTdAStarPlanner(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            SearchBudget searchBudget,
            TerminationPolicy terminationPolicy,
            PathEvaluator pathEvaluator
    ) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(costEngine, "costEngine");
        if (costEngine.edgeGraph() != edgeGraph) {
            throw new IllegalArgumentException(
                    REASON_GRAPH_CONTRACT_MISMATCH + ": planner graph contract does not match cost engine graph instance"
            );
        }
        this.plannerGraphContract = edgeGraph;
        this.plannerProfileContract = costEngine.profileStore();
        this.reverseEdgeIndex = ReverseEdgeIndex.build(edgeGraph);
        this.reverseLowerBoundByEdge = computeReverseLowerBoundByEdge(edgeGraph, plannerProfileContract);
        this.searchBudget = Objects.requireNonNull(searchBudget, "searchBudget");
        this.terminationPolicy = Objects.requireNonNull(terminationPolicy, "terminationPolicy");
        this.pathEvaluator = Objects.requireNonNull(pathEvaluator, "pathEvaluator");
    }

    /**
     * Computes one route request using bidirectional time-dependent A*.
     *
     * <p>On success, the winning edge path is replayed through {@link PathEvaluator} to
     * guarantee response cost/arrival exactly matches the authoritative cost engine.</p>
     */
    @Override
    public InternalRoutePlan compute(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            GoalBoundHeuristic heuristic,
            InternalRouteRequest request
    ) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(costEngine, "costEngine");
        Objects.requireNonNull(heuristic, "heuristic");
        Objects.requireNonNull(request, "request");
        validatePlannerContract(edgeGraph, costEngine);

        int sourceNodeId = request.sourceNodeId();
        int targetNodeId = request.targetNodeId();
        long departureTicks = request.departureTicks();
        var temporalContext = request.temporalContext();
        var transitionContext = request.transitionContext();

        if (sourceNodeId == targetNodeId) {
            return new InternalRoutePlan(true, 0.0f, departureTicks, 0, new int[]{sourceNodeId});
        }

        PlannerQueryContext context = queryContext.get();
        context.reset();
        DominanceLabelStore labelStore = context.labelStore();
        PriorityQueue<ForwardFrontierState> forwardFrontier = context.forwardFrontier();
        PriorityQueue<BackwardFrontierState> backwardFrontier = context.backwardFrontier();

        context.setReverseBest(targetNodeId, 0.0f);
        backwardFrontier.add(new BackwardFrontierState(targetNodeId, 0.0f));
        checkFrontierBudget(forwardFrontier, backwardFrontier);

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
            long arrivalTicks = saturatingAdd(departureTicks, toArrivalTicks(transitionCost));
            int labelId = addLabelIfNonDominated(
                    context,
                    edgeId,
                    transitionCost,
                    arrivalTicks,
                    NO_LABEL,
                    labelStore
            );
            if (labelId == NO_LABEL) {
                continue;
            }
            searchBudget.checkLabelCount(labelStore.size());

            int settledNode = edgeGraph.getEdgeDestination(edgeId);
            double priority = computeForwardPriority(heuristic, settledNode, transitionCost, context);
            forwardFrontier.add(new ForwardFrontierState(labelId, edgeId, arrivalTicks, priority));
            checkFrontierBudget(forwardFrontier, backwardFrontier);
        }

        int settledStates = 0;
        int budgetedWorkStates = 0;
        int bestGoalLabelId = NO_LABEL;
        float bestGoalCost = INF;
        long bestGoalArrival = Long.MAX_VALUE;

        while (!forwardFrontier.isEmpty()) {
            budgetedWorkStates = expandBackwardOne(edgeGraph, context, backwardFrontier, forwardFrontier, budgetedWorkStates);

            ForwardFrontierState state = forwardFrontier.poll();
            budgetedWorkStates = incrementBudgetedWorkStates(budgetedWorkStates);
            int labelId = state.labelId();
            if (!labelStore.isActive(labelId)) {
                continue;
            }
            settledStates++;

            if (terminationPolicy.shouldTerminate(bestGoalCost, state.priority())) {
                break;
            }

            int settledEdgeId = labelStore.edgeId(labelId);
            float settledCost = labelStore.gScore(labelId);
            long settledArrival = labelStore.arrivalTicks(labelId);
            int settledNode = edgeGraph.getEdgeDestination(settledEdgeId);

            if (settledNode == targetNodeId) {
                if (isBetter(settledCost, settledArrival, bestGoalCost, bestGoalArrival)) {
                    bestGoalCost = settledCost;
                    bestGoalArrival = settledArrival;
                    bestGoalLabelId = labelId;
                }
                continue;
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
                if (!canImproveGoal(nextCost, nextArrival, bestGoalCost, bestGoalArrival)) {
                    continue;
                }

                int nextNodeId = edgeGraph.getEdgeDestination(nextEdgeId);
                double priority = computeForwardPriority(heuristic, nextNodeId, nextCost, context);
                if (Float.isFinite(bestGoalCost) && Double.compare(priority, bestGoalCost) > 0) {
                    continue;
                }

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
                searchBudget.checkLabelCount(labelStore.size());

                forwardFrontier.add(new ForwardFrontierState(nextLabelId, nextEdgeId, nextArrival, priority));
                checkFrontierBudget(forwardFrontier, backwardFrontier);
            }
        }

        if (bestGoalLabelId == NO_LABEL) {
            return InternalRoutePlan.unreachable(departureTicks, settledStates);
        }

        int[] edgePath = buildEdgePath(labelStore, bestGoalLabelId);
        PathEvaluator.Evaluation evaluation = pathEvaluator.evaluateEdgePath(
                costEngine,
                edgePath,
                departureTicks,
                temporalContext,
                transitionContext
        );
        int[] nodePath = pathEvaluator.toNodePath(edgeGraph, sourceNodeId, edgePath);
        return new InternalRoutePlan(true, evaluation.totalCost(), evaluation.arrivalTicks(), settledStates, nodePath);
    }

    private int expandBackwardOne(
            EdgeGraph edgeGraph,
            PlannerQueryContext context,
            PriorityQueue<BackwardFrontierState> backwardFrontier,
            PriorityQueue<ForwardFrontierState> forwardFrontier,
            int budgetedWorkStates
    ) {
        while (!backwardFrontier.isEmpty()) {
            BackwardFrontierState state = backwardFrontier.poll();
            budgetedWorkStates = incrementBudgetedWorkStates(budgetedWorkStates);
            int settledNode = state.nodeId();
            float settledDistance = state.lowerBoundDistance();

            if (context.isReverseSettled(settledNode)) {
                continue;
            }
            if (Float.compare(settledDistance, context.reverseBest(settledNode)) != 0) {
                continue;
            }

            context.markReverseSettled(settledNode);
            int incomingStart = reverseEdgeIndex.incomingStart(settledNode);
            int incomingEnd = reverseEdgeIndex.incomingEnd(settledNode);
            for (int i = incomingStart; i < incomingEnd; i++) {
                int incomingEdgeId = reverseEdgeIndex.incomingEdgeIdAt(i);
                int predecessorNode = edgeGraph.getEdgeOrigin(incomingEdgeId);
                float lowerBoundEdgeCost = reverseLowerBoundByEdge[incomingEdgeId];
                float nextDistance = settledDistance + lowerBoundEdgeCost;
                if (!Float.isFinite(nextDistance)) {
                    continue;
                }
                if (nextDistance < context.reverseBest(predecessorNode)) {
                    context.setReverseBest(predecessorNode, nextDistance);
                    backwardFrontier.add(new BackwardFrontierState(predecessorNode, nextDistance));
                    checkFrontierBudget(forwardFrontier, backwardFrontier);
                }
            }
            return budgetedWorkStates;
        }
        return budgetedWorkStates;
    }

    /**
     * Inserts a forward label when it is not dominated on the same edge.
     *
     * <p>Dominated existing labels are deactivated eagerly to keep frontier and label-store
     * growth bounded by useful states only.</p>
     */
    private static int addLabelIfNonDominated(
            PlannerQueryContext context,
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

    private static boolean isBetter(float newCost, long newArrival, float currentCost, long currentArrival) {
        if (newCost < currentCost) {
            return true;
        }
        return Float.compare(newCost, currentCost) == 0 && newArrival < currentArrival;
    }

    private static boolean canImproveGoal(float nextCost, long nextArrival, float bestGoalCost, long bestGoalArrival) {
        if (!Float.isFinite(bestGoalCost)) {
            return true;
        }
        if (nextCost < bestGoalCost) {
            return true;
        }
        return Float.compare(nextCost, bestGoalCost) == 0 && nextArrival < bestGoalArrival;
    }

    /**
     * Reconstructs source-to-goal edge path from predecessor label chain.
     */
    private static int[] buildEdgePath(DominanceLabelStore labelStore, int terminalLabelId) {
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

    /**
     * Computes forward frontier priority as {@code g + boundedEstimate}.
     */
    private double computeForwardPriority(
            GoalBoundHeuristic heuristic,
            int nodeId,
            float gScore,
            PlannerQueryContext context
    ) {
        double baseEstimate = sanitizeHeuristic(heuristic.estimateFromNode(nodeId));
        if (context.isReverseSettled(nodeId)) {
            baseEstimate = Math.max(baseEstimate, context.reverseBest(nodeId));
        }
        return gScore + baseEstimate;
    }

    private static double sanitizeHeuristic(double estimate) {
        if (!Double.isFinite(estimate) || estimate < 0.0d) {
            return 0.0d;
        }
        return estimate;
    }

    /**
     * Enforces total frontier-size budget across forward and backward lanes.
     */
    private void checkFrontierBudget(
            PriorityQueue<ForwardFrontierState> forwardFrontier,
            PriorityQueue<BackwardFrontierState> backwardFrontier
    ) {
        searchBudget.checkFrontierSize(forwardFrontier.size() + backwardFrontier.size());
    }

    /**
     * Increments global work-state counter and enforces settled/work budget.
     */
    private int incrementBudgetedWorkStates(int currentWorkStates) {
        int nextWorkStates = currentWorkStates + 1;
        searchBudget.checkSettledStates(nextWorkStates);
        return nextWorkStates;
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

    /**
     * Builds per-edge reverse lower bounds used by backward search.
     */
    private static float[] computeReverseLowerBoundByEdge(EdgeGraph edgeGraph, ProfileStore profileStore) {
        int edgeCount = edgeGraph.edgeCount();
        float[] lowerBounds = new float[edgeCount];

        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            float baseWeight = edgeGraph.getBaseWeight(edgeId);
            int profileId = edgeGraph.getProfileId(edgeId);
            ProfileStore.ProfileMetadata metadata = profileStore.getMetadata(profileId);
            float minTemporal = metadata.minMultiplier();
            if (!Float.isFinite(minTemporal) || minTemporal < 0.0f) {
                minTemporal = 0.0f;
            }
            minTemporal = Math.min(minTemporal, 1.0f);

            double bound = (double) baseWeight * minTemporal;
            if (!Double.isFinite(bound) || bound < 0.0d || bound > Float.MAX_VALUE) {
                lowerBounds[edgeId] = 0.0f;
            } else {
                lowerBounds[edgeId] = (float) bound;
            }
        }
        return lowerBounds;
    }

    /**
     * Validates that runtime graph/profile instances match planner construction contracts.
     */
    private void validatePlannerContract(EdgeGraph edgeGraph, CostEngine costEngine) {
        if (edgeGraph != plannerGraphContract || costEngine.edgeGraph() != plannerGraphContract) {
            throw new IllegalArgumentException(
                    REASON_GRAPH_CONTRACT_MISMATCH + ": planner graph contract does not match runtime graph instance"
            );
        }
        if (costEngine.profileStore() != plannerProfileContract) {
            throw new IllegalArgumentException(
                    REASON_PROFILE_CONTRACT_MISMATCH + ": planner profile contract does not match runtime profile instance"
            );
        }
    }
}
