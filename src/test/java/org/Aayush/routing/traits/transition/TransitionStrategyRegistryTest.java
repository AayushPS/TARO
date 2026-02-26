package org.Aayush.routing.traits.transition;

import org.Aayush.routing.graph.TurnCostMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 TransitionStrategyRegistry Tests")
class TransitionStrategyRegistryTest {

    @Test
    @DisplayName("Default registry exposes NODE_BASED and EDGE_BASED strategies")
    void testDefaultRegistryBuiltIns() {
        TransitionStrategyRegistry registry = TransitionStrategyRegistry.defaultRegistry();
        assertNotNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_NODE_BASED));
        assertNotNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_EDGE_BASED));
        assertEquals(
                TransitionStrategyRegistry.STRATEGY_NODE_BASED,
                registry.strategy(TransitionStrategyRegistry.STRATEGY_NODE_BASED).id()
        );
        assertEquals(
                TransitionStrategyRegistry.STRATEGY_EDGE_BASED,
                registry.strategy(TransitionStrategyRegistry.STRATEGY_EDGE_BASED).id()
        );
    }

    @Test
    @DisplayName("Custom strategy overrides built-in id deterministically")
    void testCustomStrategyOverride() {
        TransitionCostStrategy custom = new TransitionCostStrategy() {
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
                return TurnCostDecision.neutral();
            }
        };

        TransitionStrategyRegistry registry = new TransitionStrategyRegistry(List.of(custom));
        assertTrue(registry.strategyIds().contains(TransitionStrategyRegistry.STRATEGY_NODE_BASED));
        assertEquals(custom, registry.strategy(TransitionStrategyRegistry.STRATEGY_NODE_BASED));
    }

    @Test
    @DisplayName("Null strategy lookup returns null")
    void testNullLookupReturnsNull() {
        TransitionStrategyRegistry registry = TransitionStrategyRegistry.defaultRegistry();
        assertNull(registry.strategy(null));
    }

    @Test
    @DisplayName("Null custom strategy collection falls back to built-ins")
    void testNullCustomCollectionUsesBuiltIns() {
        TransitionStrategyRegistry registry = new TransitionStrategyRegistry((Collection<? extends TransitionCostStrategy>) null);
        assertNotNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_NODE_BASED));
        assertNotNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_EDGE_BASED));
    }

    @Test
    @DisplayName("Explicit registry can exclude built-ins")
    void testExplicitRegistryWithoutBuiltIns() {
        TransitionCostStrategy custom = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "CUSTOM";
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

        TransitionStrategyRegistry registry = new TransitionStrategyRegistry(List.of(custom), false);
        assertNotNull(registry.strategy("CUSTOM"));
        assertNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_NODE_BASED));
        assertNull(registry.strategy(TransitionStrategyRegistry.STRATEGY_EDGE_BASED));
    }

    @Test
    @DisplayName("Registry rejects null strategy entries")
    void testRegistryRejectsNullEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new TransitionStrategyRegistry(java.util.Arrays.asList((TransitionCostStrategy) null), false)
        );
    }

    @Test
    @DisplayName("Registry rejects blank strategy id")
    void testRegistryRejectsBlankStrategyId() {
        TransitionCostStrategy blank = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "   ";
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

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransitionStrategyRegistry(List.of(blank), false)
        );
        assertTrue(ex.getMessage().contains("strategy.id"));
    }

    @Test
    @DisplayName("Packed decision fallback works for evaluate-only custom strategies")
    void testPackedFallbackCompatibilityForEvaluateOnlyCustomStrategy() {
        TransitionCostStrategy custom = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "CUSTOM_PACKED";
            }

            @Override
            public boolean appliesFiniteTurnPenalties() {
                return true;
            }

            @Override
            public TurnCostDecision evaluate(
                    TurnCostMap turnCostMap,
                    int fromEdgeId,
                    int toEdgeId,
                    boolean hasPredecessor
            ) {
                return hasPredecessor
                        ? TurnCostDecision.of(2.5f, true)
                        : TurnCostDecision.neutral();
            }
        };

        long packed = custom.evaluatePacked(null, 1, 2, true);
        assertEquals(2.5f, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(packed), 1e-6f);
        assertTrue(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(packed));

        long packedNeutral = custom.evaluatePacked(null, 1, 2, false);
        assertEquals(0.0f, TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(packedNeutral), 1e-6f);
        assertFalse(TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(packedNeutral));
    }
}
