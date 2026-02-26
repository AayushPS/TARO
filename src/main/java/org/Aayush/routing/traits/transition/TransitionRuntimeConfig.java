package org.Aayush.routing.traits.transition;

import lombok.Builder;
import lombok.Value;

/**
 * Runtime configuration used to bind Stage 17 transition behavior once at startup.
 */
@Value
@Builder
public class TransitionRuntimeConfig {

    /**
     * Selected transition trait id (for example NODE_BASED or EDGE_BASED).
     */
    String transitionTraitId;

    /**
     * Returns convenience runtime config for NODE_BASED.
     */
    public static TransitionRuntimeConfig nodeBased() {
        return TransitionRuntimeConfig.builder()
                .transitionTraitId(TransitionTraitCatalog.TRAIT_NODE_BASED)
                .build();
    }

    /**
     * Returns convenience runtime config for EDGE_BASED.
     */
    public static TransitionRuntimeConfig edgeBased() {
        return TransitionRuntimeConfig.builder()
                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                .build();
    }

    /**
     * Returns default startup transition mode used for migration parity.
     */
    public static TransitionRuntimeConfig defaultRuntime() {
        return edgeBased();
    }
}
