package org.Aayush.routing.traits.transition;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.graph.TurnCostMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 TransitionRuntimeBinder Tests")
class TransitionRuntimeBinderTest {

    @Test
    @DisplayName("Missing runtime config is rejected")
    void testMissingRuntimeConfigRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        null,
                        TransitionTraitCatalog.defaultCatalog(),
                        TransitionStrategyRegistry.defaultRegistry(),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Blank trait id is rejected as missing config")
    void testBlankTraitRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TransitionRuntimeConfig.builder().transitionTraitId("   ").build(),
                        TransitionTraitCatalog.defaultCatalog(),
                        TransitionStrategyRegistry.defaultRegistry(),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown transition trait is rejected with deterministic reason code")
    void testUnknownTraitRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TransitionRuntimeConfig.builder().transitionTraitId("UNKNOWN").build(),
                        TransitionTraitCatalog.defaultCatalog(),
                        TransitionStrategyRegistry.defaultRegistry(),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TRANSITION_TRAIT, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown transition strategy id is rejected deterministically")
    void testUnknownStrategyRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        TransitionTrait brokenTrait = new TransitionTrait() {
            @Override
            public String id() {
                return TransitionTraitCatalog.TRAIT_EDGE_BASED;
            }

            @Override
            public String strategyId() {
                return "MISSING_STRATEGY";
            }
        };
        TransitionTraitCatalog catalog = new TransitionTraitCatalog(List.of(brokenTrait));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TransitionRuntimeConfig.edgeBased(),
                        catalog,
                        TransitionStrategyRegistry.defaultRegistry(),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TRANSITION_STRATEGY, ex.getReasonCode());
    }

    @Test
    @DisplayName("Incompatible trait/strategy tuple is rejected deterministically")
    void testIncompatibleTupleRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        TransitionTrait edgeTraitPointingToNodeStrategy = new TransitionTrait() {
            @Override
            public String id() {
                return TransitionTraitCatalog.TRAIT_EDGE_BASED;
            }

            @Override
            public String strategyId() {
                return TransitionStrategyRegistry.STRATEGY_NODE_BASED;
            }
        };

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TransitionRuntimeConfig.edgeBased(),
                        new TransitionTraitCatalog(List.of(edgeTraitPointingToNodeStrategy)),
                        TransitionStrategyRegistry.defaultRegistry(),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Semantic Stage 17 strategy contract violations are rejected during startup bind")
    void testSemanticContractViolationRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
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
        TransitionTrait customEdgeTrait = new TransitionTrait() {
            @Override
            public String id() {
                return TransitionTraitCatalog.TRAIT_EDGE_BASED;
            }

            @Override
            public String strategyId() {
                return "BAD_EDGE_FINITE";
            }
        };

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TransitionRuntimeConfig.edgeBased(),
                        new TransitionTraitCatalog(List.of(customEdgeTrait)),
                        new TransitionStrategyRegistry(List.of(badEdgeStrategy)),
                        TransitionPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, ex.getReasonCode());
        assertTrue(ex.getMessage().contains("EDGE_BASED finite-turn handling"));
    }

    @Test
    @DisplayName("Custom strategies that drift after startup probes are guarded at runtime")
    void testCustomStrategyRuntimeSemanticDriftRejected() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        TransitionCostStrategy driftingStrategy = new TransitionCostStrategy() {
            @Override
            public String id() {
                return "DRIFTING_EDGE";
            }

            @Override
            public boolean appliesFiniteTurnPenalties() {
                return true;
            }

            @Override
            public TurnCostDecision evaluate(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
                return TurnCostDecision.neutral();
            }

            @Override
            public long evaluatePacked(TurnCostMap turnCostMap, int fromEdgeId, int toEdgeId, boolean hasPredecessor) {
                if (!hasPredecessor) {
                    return TurnCostDecision.neutral().packed();
                }
                if (turnCostMap == null) {
                    if (fromEdgeId == 11 && toEdgeId == 12) {
                        return TurnCostDecision.neutral().packed();
                    }
                    return TurnCostDecision.zeroApplied().packed();
                }
                if (fromEdgeId == 11 && toEdgeId == 12) {
                    return TurnCostDecision.of(2.5f, true).packed();
                }
                if (fromEdgeId == 11 && toEdgeId == 13) {
                    return TurnCostDecision.forbidden().packed();
                }
                return TurnCostDecision.zeroApplied().packed();
            }
        };
        TransitionTrait customEdgeTrait = new TransitionTrait() {
            @Override
            public String id() {
                return TransitionTraitCatalog.TRAIT_EDGE_BASED;
            }

            @Override
            public String strategyId() {
                return "DRIFTING_EDGE";
            }
        };

        TransitionRuntimeBinder.Binding binding = binder.bind(
                TransitionRuntimeConfig.edgeBased(),
                new TransitionTraitCatalog(List.of(customEdgeTrait)),
                new TransitionStrategyRegistry(List.of(driftingStrategy)),
                TransitionPolicy.defaults()
        );

        TransitionCostStrategy.TransitionComputationException ex = assertThrows(
                TransitionCostStrategy.TransitionComputationException.class,
                () -> binding.getResolvedTransitionContext().getStrategy().evaluatePacked(null, 21, 22, true)
        );
        assertEquals(RouteCore.REASON_TRANSITION_RESOLUTION_FAILURE, ex.reasonCode());
        assertTrue(ex.getMessage().contains("DRIFTING_EDGE"));
        assertTrue(ex.getMessage().contains("violated Stage 17 semantics"));
    }

    @Test
    @DisplayName("Trait id is trimmed before lookup")
    void testTraitIdTrimmedBeforeLookup() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();
        TransitionRuntimeBinder.Binding binding = binder.bind(
                TransitionRuntimeConfig.builder()
                        .transitionTraitId("  " + TransitionTraitCatalog.TRAIT_NODE_BASED + "  ")
                        .build(),
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        );

        assertEquals(TransitionTraitCatalog.TRAIT_NODE_BASED, binding.getResolvedTransitionContext().getTransitionTraitId());
        assertEquals(
                TransitionStrategyRegistry.STRATEGY_NODE_BASED,
                binding.getResolvedTransitionContext().getTransitionStrategyId()
        );
    }

    @Test
    @DisplayName("EDGE_BASED and NODE_BASED bindings expose deterministic telemetry")
    void testBuiltInBindings() {
        TransitionRuntimeBinder binder = new TransitionRuntimeBinder();

        TransitionRuntimeBinder.Binding edgeBinding = binder.bind(
                TransitionRuntimeConfig.edgeBased(),
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        );
        assertNotNull(edgeBinding.getResolvedTransitionContext());
        assertNotNull(edgeBinding.getTransitionTelemetry());
        assertEquals(TransitionTraitCatalog.TRAIT_EDGE_BASED, edgeBinding.getResolvedTransitionContext().getTransitionTraitId());
        assertTrue(edgeBinding.getResolvedTransitionContext().isFiniteTurnPenaltiesEnabled());

        TransitionRuntimeBinder.Binding nodeBinding = binder.bind(
                TransitionRuntimeConfig.nodeBased(),
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        );
        assertEquals(TransitionTraitCatalog.TRAIT_NODE_BASED, nodeBinding.getResolvedTransitionContext().getTransitionTraitId());
        assertTrue(!nodeBinding.getResolvedTransitionContext().isFiniteTurnPenaltiesEnabled());
    }
}
