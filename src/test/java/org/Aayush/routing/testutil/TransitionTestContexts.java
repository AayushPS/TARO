package org.Aayush.routing.testutil;

import org.Aayush.routing.traits.transition.ResolvedTransitionContext;
import org.Aayush.routing.traits.transition.TransitionPolicy;
import org.Aayush.routing.traits.transition.TransitionRuntimeBinder;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionStrategyRegistry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;

/**
 * Shared transition contexts for tests that call low-level planner/cost APIs directly.
 */
public final class TransitionTestContexts {
    private static final ResolvedTransitionContext EDGE_BASED = bind(TransitionRuntimeConfig.edgeBased());
    private static final ResolvedTransitionContext NODE_BASED = bind(TransitionRuntimeConfig.nodeBased());

    private TransitionTestContexts() {
    }

    public static ResolvedTransitionContext edgeBased() {
        return EDGE_BASED;
    }

    public static ResolvedTransitionContext nodeBased() {
        return NODE_BASED;
    }

    private static ResolvedTransitionContext bind(TransitionRuntimeConfig runtimeConfig) {
        return new TransitionRuntimeBinder().bind(
                runtimeConfig,
                TransitionTraitCatalog.defaultCatalog(),
                TransitionStrategyRegistry.defaultRegistry(),
                TransitionPolicy.defaults()
        ).getResolvedTransitionContext();
    }
}
