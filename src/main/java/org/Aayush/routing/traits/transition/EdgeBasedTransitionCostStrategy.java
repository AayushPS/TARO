package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;

/**
 * Edge-based transition behavior:
 * finite turn penalties are applied when predecessor and turn map exist.
 */
public final class EdgeBasedTransitionCostStrategy implements TransitionCostStrategy {
    private static final long PACKED_NEUTRAL = TurnCostDecision.neutral().packed();
    private static final long PACKED_ZERO_APPLIED = TurnCostDecision.zeroApplied().packed();
    private static final long PACKED_FORBIDDEN = TurnCostDecision.forbidden().packed();

    @Override
    public String id() {
        return TransitionStrategyRegistry.STRATEGY_EDGE_BASED;
    }

    @Override
    public boolean appliesFiniteTurnPenalties() {
        return true;
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

        float turnPenalty = turnCostMap.getCost(fromEdgeId, toEdgeId);
        if (turnPenalty == TurnCostMap.FORBIDDEN_TURN) {
            return PACKED_FORBIDDEN;
        }
        if (Float.compare(turnPenalty, 0.0f) == 0) {
            return PACKED_ZERO_APPLIED;
        }
        return TurnCostDecision.pack(turnPenalty, true);
    }
}
