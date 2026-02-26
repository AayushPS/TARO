package org.Aayush.routing.traits.transition;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
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
