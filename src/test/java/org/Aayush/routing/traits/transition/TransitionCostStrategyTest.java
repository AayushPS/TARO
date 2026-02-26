package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 TransitionCostStrategy Tests")
class TransitionCostStrategyTest {

    @Test
    @DisplayName("Default evaluatePacked rejects null decision with deterministic reason code")
    void testEvaluatePackedRejectsNullDecision() {
        TransitionCostStrategy badStrategy = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "BAD_STRATEGY";
            }

            @Override
            public boolean appliesFiniteTurnPenalties() {
                return true;
            }

            @Override
            public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
                return null;
            }
        };

        TransitionCostStrategy.TransitionComputationException ex = assertThrows(
                TransitionCostStrategy.TransitionComputationException.class,
                () -> badStrategy.evaluatePacked(null, 1, 2, true)
        );

        assertEquals("H17_TRANSITION_RESOLUTION_FAILURE", ex.reasonCode());
        assertTrue(ex.getMessage().contains("BAD_STRATEGY"));
    }

    @Test
    @DisplayName("TurnCostDecision packed encoding round-trips canonical and finite values")
    void testTurnCostDecisionPackedRoundTrip() {
        long neutralPacked = TransitionCostStrategy.TurnCostDecision.neutral().packed();
        assertEquals(0.0f, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(neutralPacked), 1e-6f);
        assertFalse(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(neutralPacked));

        long zeroAppliedPacked = TransitionCostStrategy.TurnCostDecision.zeroApplied().packed();
        assertEquals(0.0f, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(zeroAppliedPacked), 1e-6f);
        assertTrue(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(zeroAppliedPacked));

        long forbiddenPacked = TransitionCostStrategy.TurnCostDecision.forbidden().packed();
        assertEquals(TurnCostMap.FORBIDDEN_TURN, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(forbiddenPacked));
        assertTrue(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(forbiddenPacked));
        TransitionCostStrategy.TurnCostDecision forbiddenDecision =
                TransitionCostStrategy.TurnCostDecision.fromPacked(forbiddenPacked);
        assertEquals(TurnCostMap.FORBIDDEN_TURN, forbiddenDecision.turnPenalty());
        assertTrue(forbiddenDecision.turnPenaltyApplied());

        long finitePacked = TransitionCostStrategy.TurnCostDecision.pack(2.75f, true);
        TransitionCostStrategy.TurnCostDecision finite = TransitionCostStrategy.TurnCostDecision.fromPacked(finitePacked);
        assertEquals(2.75f, finite.turnPenalty(), 1e-6f);
        assertTrue(finite.turnPenaltyApplied());
    }

    @Test
    @DisplayName("Forbidden decision canonicalizes regardless of turnPenaltyApplied flag")
    void testForbiddenDecisionCanonicalization() {
        TransitionCostStrategy.TurnCostDecision forbiddenFromOf =
                TransitionCostStrategy.TurnCostDecision.of(TurnCostMap.FORBIDDEN_TURN, false);
        assertSame(TransitionCostStrategy.TurnCostDecision.forbidden(), forbiddenFromOf);
        assertTrue(forbiddenFromOf.turnPenaltyApplied());
        assertTrue(forbiddenFromOf.isForbidden());

        long packedForbidden = TransitionCostStrategy.TurnCostDecision.pack(TurnCostMap.FORBIDDEN_TURN, false);
        assertEquals(TurnCostMap.FORBIDDEN_TURN, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(packedForbidden));
        assertTrue(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(packedForbidden));

        long rawPackedForbiddenUnapplied = Integer.toUnsignedLong(
                Float.floatToRawIntBits(TurnCostMap.FORBIDDEN_TURN)
        ) << 1;
        TransitionCostStrategy.TurnCostDecision fromRawPacked =
                TransitionCostStrategy.TurnCostDecision.fromPacked(rawPackedForbiddenUnapplied);
        assertSame(TransitionCostStrategy.TurnCostDecision.forbidden(), fromRawPacked);
        assertTrue(fromRawPacked.turnPenaltyApplied());
    }

    @Test
    @DisplayName("Built-in strategies preserve evaluate and evaluatePacked parity for neutral transitions")
    void testBuiltInEvaluateAndPackedParityForNeutralTransitions() {
        NodeBasedTransitionCostStrategy nodeBased = new NodeBasedTransitionCostStrategy();
        EdgeBasedTransitionCostStrategy edgeBased = new EdgeBasedTransitionCostStrategy();

        TransitionCostStrategy.TurnCostDecision nodeDecision = nodeBased.evaluate(null, 10, 11, false);
        long nodePacked = nodeBased.evaluatePacked(null, 10, 11, false);
        assertEquals(nodeDecision.turnPenalty(), TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(nodePacked), 1e-6f);
        assertEquals(nodeDecision.turnPenaltyApplied(), TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(nodePacked));

        TransitionCostStrategy.TurnCostDecision edgeDecision = edgeBased.evaluate(null, 10, 11, false);
        long edgePacked = edgeBased.evaluatePacked(null, 10, 11, false);
        assertEquals(edgeDecision.turnPenalty(), TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(edgePacked), 1e-6f);
        assertEquals(edgeDecision.turnPenaltyApplied(), TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(edgePacked));
    }
}
