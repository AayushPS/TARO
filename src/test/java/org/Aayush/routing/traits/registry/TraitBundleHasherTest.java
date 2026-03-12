package org.Aayush.routing.traits.registry;

import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalPolicy;
import org.Aayush.routing.traits.temporal.TemporalRuntimeBinder;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.transition.EdgeBasedTransitionCostStrategy;
import org.Aayush.routing.traits.transition.TransitionCostStrategy;
import org.Aayush.routing.traits.transition.TransitionPolicy;
import org.Aayush.routing.traits.transition.TransitionRuntimeBinder;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionStrategyRegistry;
import org.Aayush.routing.traits.transition.TransitionTrait;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 18 TraitBundleHasher Tests")
class TraitBundleHasherTest {

    @Test
    @DisplayName("Trait hash is stable across repeated calls, concurrent calls, and bundle aliases")
    void testTraitHashStableAcrossRepeatedAndConcurrentCalls() {
        TraitBundleSpec bundleA = bundleSpec("BUNDLE_A");
        TraitBundleSpec bundleB = bundleSpec("BUNDLE_B");
        TemporalRuntimeBinder.Binding temporalBinding = new TemporalRuntimeBinder().bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );
        TransitionRuntimeBinder.Binding transitionBinding = new TransitionRuntimeBinder().bind(
                TransitionRuntimeConfig.edgeBased(),
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        );
        TraitBundleHasher hasher = new TraitBundleHasher();

        String hashA = hasher.hash(
                bundleA,
                temporalBinding.getResolvedTemporalContext(),
                transitionBinding.getResolvedTransitionContext()
        );
        String hashB = hasher.hash(
                bundleB,
                temporalBinding.getResolvedTemporalContext(),
                transitionBinding.getResolvedTransitionContext()
        );

        assertEquals(hashA, hashB, "bundleId aliases must not change the trait hash");
        assertEquals(64, hashA.length());
        assertTrue(hashA.matches("[0-9a-f]{64}"));

        Set<String> concurrentHashes = IntStream.range(0, 32)
                .parallel()
                .mapToObj(index -> hasher.hash(
                        bundleA,
                        temporalBinding.getResolvedTemporalContext(),
                        transitionBinding.getResolvedTransitionContext()
                ))
                .collect(Collectors.toSet());
        assertEquals(Set.of(hashA), concurrentHashes);
    }

    @Test
    @DisplayName("Trait hash changes when resolved transition strategy remaps under the same bundle spec")
    void testTraitHashIncludesResolvedTransitionStrategy() {
        TraitBundleSpec bundle = bundleSpec("BUNDLE_A");
        TemporalRuntimeBinder.Binding temporalBinding = new TemporalRuntimeBinder().bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );
        TransitionRuntimeBinder.Binding defaultTransitionBinding = new TransitionRuntimeBinder().bind(
                TransitionRuntimeConfig.edgeBased(),
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        );
        TransitionRuntimeBinder.Binding remappedTransitionBinding = new TransitionRuntimeBinder().bind(
                TransitionRuntimeConfig.edgeBased(),
                new TransitionTraitCatalog(List.of(new StaticTransitionTrait(
                        TransitionTraitCatalog.TRAIT_EDGE_BASED,
                        "EDGE_BASED_ALIAS"
                ))),
                new TransitionStrategyRegistry(List.of(new AliasEdgeBasedStrategy("EDGE_BASED_ALIAS"))),
                TransitionPolicy.defaults()
        );
        TraitBundleHasher hasher = new TraitBundleHasher();

        String defaultHash = hasher.hash(
                bundle,
                temporalBinding.getResolvedTemporalContext(),
                defaultTransitionBinding.getResolvedTransitionContext()
        );
        String remappedHash = hasher.hash(
                bundle,
                temporalBinding.getResolvedTemporalContext(),
                remappedTransitionBinding.getResolvedTransitionContext()
        );

        assertNotEquals(defaultHash, remappedHash);
        assertTrue(
                hasher.canonicalConfig(
                        bundle,
                        temporalBinding.getResolvedTemporalContext(),
                        remappedTransitionBinding.getResolvedTransitionContext()
                ).contains("transition_strategy_id=EDGE_BASED_ALIAS")
        );
    }

    private static TraitBundleSpec bundleSpec(String bundleId) {
        return TraitBundleSpec.builder()
                .bundleId(bundleId)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .transitionTraitId(org.Aayush.routing.traits.transition.TransitionTraitCatalog.TRAIT_EDGE_BASED)
                .build();
    }

    private record StaticTransitionTrait(String id, String strategyId) implements TransitionTrait {
    }

    private static final class AliasEdgeBasedStrategy implements TransitionCostStrategy {
        private final String id;
        private final EdgeBasedTransitionCostStrategy delegate = new EdgeBasedTransitionCostStrategy();

        private AliasEdgeBasedStrategy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean appliesFiniteTurnPenalties() {
            return delegate.appliesFiniteTurnPenalties();
        }

        @Override
        public TurnCostDecision evaluate(
                org.Aayush.routing.graph.TurnCostMap turnCostMap,
                int fromEdgeId,
                int toEdgeId,
                boolean hasPredecessor
        ) {
            return delegate.evaluate(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor);
        }

        @Override
        public long evaluatePacked(
                org.Aayush.routing.graph.TurnCostMap turnCostMap,
                int fromEdgeId,
                int toEdgeId,
                boolean hasPredecessor
        ) {
            return delegate.evaluatePacked(turnCostMap, fromEdgeId, toEdgeId, hasPredecessor);
        }
    }
}
