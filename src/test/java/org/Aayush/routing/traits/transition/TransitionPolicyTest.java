package org.Aayush.routing.traits.transition;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.graph.TurnCostMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
