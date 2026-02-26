package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;

/**
 * Strategy contract for transition/turn-cost semantics.
 */
public interface TransitionCostStrategy {

    /**
     * Stable strategy identifier.
     */
    String id();

    /**
     * Returns true when finite turn penalties are part of effective-cost composition.
     */
    boolean appliesFiniteTurnPenalties();

    /**
     * Evaluates turn contribution for one predecessor-to-next-edge transition.
     */
    TurnCostDecision evaluate(
            TurnCostMap turnCostMap,
            int fromEdgeId,
            int toEdgeId,
            boolean hasPredecessor
    );

    /**
     * Allocation-lean packed evaluation for hot-path callers.
     *
     * <p>Default implementation keeps backward compatibility with existing custom
     * strategies that only implement {@link #evaluate(TurnCostMap, int, int, boolean)}.</p>
     */
    default long evaluatePacked(
            TurnCostMap turnCostMap,
            int fromEdgeId,
            int toEdgeId,
            boolean hasPredecessor
    ) {
        TurnCostDecision decision = evaluate(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor);
        if (decision == null) {
            throw new TransitionComputationException(
                    "H17_TRANSITION_RESOLUTION_FAILURE",
                    "transition strategy " + id() + " returned null turn decision",
                    null
            );
        }
        return decision.packed();
    }

    /**
     * Immutable turn-cost decision.
     */
    record TurnCostDecision(float turnPenalty, boolean turnPenaltyApplied) {
        private static final TurnCostDecision NEUTRAL = new TurnCostDecision(0.0f, false);
        private static final TurnCostDecision ZERO_APPLIED = new TurnCostDecision(0.0f, true);
        private static final TurnCostDecision FORBIDDEN = new TurnCostDecision(TurnCostMap.FORBIDDEN_TURN, true);

        private static final long PACKED_NEUTRAL = rawPack(0.0f, false);
        private static final long PACKED_ZERO_APPLIED = rawPack(0.0f, true);
        private static final long PACKED_FORBIDDEN = rawPack(TurnCostMap.FORBIDDEN_TURN, true);

        public static TurnCostDecision neutral() {
            return NEUTRAL;
        }

        public static TurnCostDecision zeroApplied() {
            return ZERO_APPLIED;
        }

        public static TurnCostDecision forbidden() {
            return FORBIDDEN;
        }

        public static TurnCostDecision of(float turnPenalty, boolean turnPenaltyApplied) {
            if (turnPenalty == TurnCostMap.FORBIDDEN_TURN) {
                return FORBIDDEN;
            }
            if (!turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) == 0) {
                return NEUTRAL;
            }
            if (turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) == 0) {
                return ZERO_APPLIED;
            }
            return new TurnCostDecision(turnPenalty, turnPenaltyApplied);
        }

        public long packed() {
            return pack(turnPenalty, turnPenaltyApplied);
        }

        public static long pack(float turnPenalty, boolean turnPenaltyApplied) {
            if (turnPenalty == TurnCostMap.FORBIDDEN_TURN) {
                return PACKED_FORBIDDEN;
            }
            if (!turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) == 0) {
                return PACKED_NEUTRAL;
            }
            if (turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) == 0) {
                return PACKED_ZERO_APPLIED;
            }
            return rawPack(turnPenalty, turnPenaltyApplied);
        }

        public static TurnCostDecision fromPacked(long packed) {
            if (packed == PACKED_NEUTRAL) {
                return NEUTRAL;
            }
            if (packed == PACKED_ZERO_APPLIED) {
                return ZERO_APPLIED;
            }
            if (packed == PACKED_FORBIDDEN) {
                return FORBIDDEN;
            }
            return of(unpackTurnPenalty(packed), unpackTurnPenaltyApplied(packed));
        }

        public static float unpackTurnPenalty(long packed) {
            return Float.intBitsToFloat((int) (packed >>> 1));
        }

        public static boolean unpackTurnPenaltyApplied(long packed) {
            return (packed & 1L) != 0L;
        }

        private static long rawPack(float turnPenalty, boolean turnPenaltyApplied) {
            return (Integer.toUnsignedLong(Float.floatToRawIntBits(turnPenalty)) << 1) | (turnPenaltyApplied ? 1L : 0L);
        }

        public boolean isForbidden() {
            return turnPenalty == TurnCostMap.FORBIDDEN_TURN;
        }
    }

    /**
     * Deterministic transition-evaluation failure used for RouteCore reason-code mapping.
     */
    final class TransitionComputationException extends RuntimeException {
        private final String reasonCode;

        public TransitionComputationException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
