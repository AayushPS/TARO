package org.Aayush.routing.traits.transition;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.graph.TurnCostMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Stage 17 TransitionPolicy Tests")
class TransitionPolicyTest {

    @Test
    @DisplayName("Unknown transition trait is rejected deterministically")
    void testUnknownTraitRejected() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        TransitionPolicy.CompatibilityException ex = assertThrows(
                TransitionPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait("CUSTOM_TRAIT", "CUSTOM"),
                        new NodeBasedTransitionCostStrategy()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("EDGE_BASED rejects strategy that ignores finite turn penalties")
    void testEdgeBasedRejectsNodeStyleStrategy() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        TransitionPolicy.CompatibilityException ex = assertThrows(
                TransitionPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TransitionTraitCatalog.TRAIT_EDGE_BASED, TransitionStrategyRegistry.STRATEGY_EDGE_BASED),
                        new NodeBasedTransitionCostStrategy()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("NODE_BASED rejects strategy that applies finite turn penalties")
    void testNodeBasedRejectsEdgeStyleStrategy() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        TransitionPolicy.CompatibilityException ex = assertThrows(
                TransitionPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TransitionTraitCatalog.TRAIT_NODE_BASED, TransitionStrategyRegistry.STRATEGY_NODE_BASED),
                        new EdgeBasedTransitionCostStrategy()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("Compatible NODE_BASED and EDGE_BASED tuples are accepted")
    void testCompatibleTuplesAccepted() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        assertDoesNotThrow(() -> policy.validateCompatibility(
                trait(TransitionTraitCatalog.TRAIT_NODE_BASED, TransitionStrategyRegistry.STRATEGY_NODE_BASED),
                new NodeBasedTransitionCostStrategy()
        ));
        assertDoesNotThrow(() -> policy.validateCompatibility(
                trait(TransitionTraitCatalog.TRAIT_EDGE_BASED, TransitionStrategyRegistry.STRATEGY_EDGE_BASED),
                new EdgeBasedTransitionCostStrategy()
        ));
    }

    @Test
    @DisplayName("NODE_BASED rejects strategies that fail to block forbidden turns")
    void testNodeBasedRejectsStrategyThatIgnoresForbiddenTurns() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        TransitionCostStrategy badNodeStrategy = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "BAD_NODE_FORBIDDEN";
            }

            @Override
            public boolean appliesFiniteTurnPenalties() {
                return false;
            }

            @Override
            public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
                return TurnCostDecision.neutral();
            }
        };

        TransitionPolicy.CompatibilityException ex = assertThrows(
                TransitionPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TransitionTraitCatalog.TRAIT_NODE_BASED, "BAD_NODE_FORBIDDEN"),
                        badNodeStrategy
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.reasonCode());
        assertTrue(ex.getMessage().contains("forbidden-turn handling"));
    }

    @Test
    @DisplayName("EDGE_BASED rejects strategies that skip finite turn penalties even when flagged as enabled")
    void testEdgeBasedRejectsStrategyThatSkipsFiniteTurnPenalty() {
        TransitionPolicy policy = TransitionPolicy.defaults();
        TransitionCostStrategy badEdgeStrategy = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "BAD_EDGE_FINITE";
            }

            @Override
            public boolean appliesFiniteTurnPenalties() {
                return true;
            }

            @Override
            public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
                if (!hasPredecessor || turnCostMap == null) {
                    return TurnCostDecision.neutral();
                }
                if (turnCostMap.getCost(fromEdgeId, toEdgeId) == TurnCostMap.FORBIDDEN_TURN) {
                    return TurnCostDecision.forbidden();
                }
                return TurnCostDecision.neutral();
            }
        };

        TransitionPolicy.CompatibilityException ex = assertThrows(
                TransitionPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TransitionTraitCatalog.TRAIT_EDGE_BASED, "BAD_EDGE_FINITE"),
                        badEdgeStrategy
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.reasonCode());
        assertTrue(ex.getMessage().contains("EDGE_BASED finite-turn handling"));
    }

    private static TransitionTrait trait(String id, String strategyId) {
        return new TransitionTrait() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String strategyId() {
                return strategyId;
            }
        };
    }
}
