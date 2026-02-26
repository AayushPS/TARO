package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;

/**
 * Node-based transition behavior:
 * finite turn penalties are ignored, forbidden turns still block.
 */
public final class NodeBasedTransitionCostStrategy implements TransitionCostStrategy {
    private static final long PACKED_NEUTRAL = TurnCostDecision.neutral().packed();
    private static final long PACKED_FORBIDDEN = TurnCostDecision.forbidden().packed();

    @Override
    public String id() {
        return TransitionStrategyRegistry.STRATEGY_NODE_BASED;
    }

    @Override
    public boolean appliesFiniteTurnPenalties() {
        return false;
    }

    @Override
    public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
        return TurnCostDecision.fromPacked(evaluatePacked(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor));
    }

    @Override
    public long evaluatePacked(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
        if (!hasPredecessor || turnCostMap == null) {
            return PACKED_NEUTRAL;
        }

        float rawTurn = turnCostMap.getCost(fromEdgeId, toEdgeId);
        if (rawTurn == TurnCostMap.FORBIDDEN_TURN) {
            return PACKED_FORBIDDEN;
        }

        return PACKED_NEUTRAL;
    }
}
