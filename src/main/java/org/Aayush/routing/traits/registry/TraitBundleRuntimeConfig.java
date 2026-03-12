package org.Aayush.routing.traits.registry;

import lombok.Builder;
import lombok.Value;

/**
 * Startup configuration for Stage 18 bundle selection.
 */
@Value
@Builder
public class TraitBundleRuntimeConfig {
    String traitBundleId;
    TraitBundleSpec inlineTraitBundleSpec;

    /**
     * Selects one named trait bundle from the registry.
     */
    public static TraitBundleRuntimeConfig ofBundleId(String traitBundleId) {
        return TraitBundleRuntimeConfig.builder()
                .traitBundleId(traitBundleId)
                .build();
    }

    /**
     * Selects one explicit inline trait bundle.
     */
    public static TraitBundleRuntimeConfig inline(TraitBundleSpec inlineTraitBundleSpec) {
        return TraitBundleRuntimeConfig.builder()
                .inlineTraitBundleSpec(inlineTraitBundleSpec)
                .build();
    }
}
