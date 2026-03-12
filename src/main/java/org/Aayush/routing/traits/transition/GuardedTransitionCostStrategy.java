package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;

/**
 * Runtime guard that enforces exact Stage 17 semantics for non-built-in strategies.
 */
final class GuardedTransitionCostStrategy implements TransitionCostStrategy {
    private static final long PACKED_NEUTRAL = TurnCostDecision.neutral().packed();
    private static final long PACKED_ZERO_APPLIED = TurnCostDecision.zeroApplied().packed();
    private static final long PACKED_FORBIDDEN = TurnCostDecision.forbidden().packed();

    private final String transitionTraitId;
    private final TransitionCostStrategy delegate;

    private GuardedTransitionCostStrategy(String transitionTraitId, TransitionCostStrategy delegate) {
        this.transitionTraitId = transitionTraitId;
        this.delegate = delegate;
    }

    static TransitionCostStrategy guard(String transitionTraitId, TransitionCostStrategy strategy) {
        if (strategy instanceof NodeBasedTransitionCostStrategy
                || strategy instanceof EdgeBasedTransitionCostStrategy
                || strategy instanceof GuardedTransitionCostStrategy) {
            return strategy;
        }
        return new GuardedTransitionCostStrategy(transitionTraitId, strategy);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public boolean appliesFiniteTurnPenalties() {
        return delegate.appliesFiniteTurnPenalties();
    }

    @Override
    public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
        return TurnCostDecision.fromPacked(evaluatePacked(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor));
    }

    @Override
    public long evaluatePacked(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
        final long packedDecision;
        try {
            packedDecision = delegate.evaluatePacked(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor);
        } catch (TransitionComputationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure(
                    "transition strategy " + id()
                            + " failed during guarded evaluation for transition "
                            + fromEdgeId + " -> " + toEdgeId,
                    ex
            );
        }

        TurnCostDecision actualDecision = decodeValidatedDecision(packedDecision, fromEdgeId, toEdgeId);
        TurnCostDecision expectedDecision = expectedDecision(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor);
        if (!sameDecision(actualDecision, expectedDecision)) {
            throw failure(
                    "transition strategy " + id()
                            + " violated Stage 17 semantics for trait " + transitionTraitId
                            + " on transition " + fromEdgeId + " -> " + toEdgeId
                            + " (expected penalty=" + expectedDecision.turnPenalty()
                            + ", applied=" + expectedDecision.turnPenaltyApplied()
                            + "; actual penalty=" + actualDecision.turnPenalty()
                            + ", applied=" + actualDecision.turnPenaltyApplied() + ")",
                    null
            );
        }

        return expectedDecision.packed();
    }

    private TurnCostDecision expectedDecision(
            TurnCostMap turnCostMap,
            int fromEdgeId,
            int toEdgeId,
            boolean hasPredecessor
    ) {
        if (!hasPredecessor || turnCostMap == null) {
            return TurnCostDecision.neutral();
        }

        float rawTurnPenalty = turnCostMap.getCost(fromEdgeId, toEdgeId);
        if (rawTurnPenalty == TurnCostMap.FORBIDDEN_TURN) {
            return TurnCostDecision.forbidden();
        }
        if (TransitionTraitCatalog.TRAIT_NODE_BASED.equals(transitionTraitId)) {
            return TurnCostDecision.neutral();
        }
        if (Float.compare(rawTurnPenalty, 0.0f) == 0) {
            return TurnCostDecision.zeroApplied();
        }
        return TurnCostDecision.of(rawTurnPenalty, true);
    }

    private TurnCostDecision decodeValidatedDecision(long packedDecision, int fromEdgeId, int toEdgeId) {
        float turnPenalty = TurnCostDecision.unpackTurnPenalty(packedDecision);
        boolean turnPenaltyApplied = TurnCostDecision.unpackTurnPenaltyApplied(packedDecision);
        if (turnPenalty == TurnCostMap.FORBIDDEN_TURN) {
            turnPenaltyApplied = true;
        }
        if (Float.isNaN(turnPenalty) || turnPenalty < 0.0f || turnPenalty == Float.NEGATIVE_INFINITY) {
            throw failure(
                    "transition strategy " + id()
                            + " returned invalid turn decision for transition "
                            + fromEdgeId + " -> " + toEdgeId
                            + " (packed=0x" + Long.toHexString(packedDecision) + ")",
                    null
            );
        }
        if (!turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) != 0) {
            throw failure(
                    "transition strategy " + id()
                            + " returned inconsistent turn decision for transition "
                            + fromEdgeId + " -> " + toEdgeId
                            + " (packed=0x" + Long.toHexString(packedDecision) + ")",
                    null
            );
        }
        return TurnCostDecision.of(turnPenalty, turnPenaltyApplied);
    }

    private static boolean sameDecision(TurnCostDecision left, TurnCostDecision right) {
        return Float.compare(left.turnPenalty(), right.turnPenalty()) == 0
                && left.turnPenaltyApplied() == right.turnPenaltyApplied();
    }

    private static TransitionComputationException failure(String message, Throwable cause) {
        return new TransitionComputationException("H17_TRANSITION_RESOLUTION_FAILURE", message, cause);
    }
}
