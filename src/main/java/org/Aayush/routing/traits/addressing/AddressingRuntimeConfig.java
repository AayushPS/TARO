package org.Aayush.routing.traits.addressing;

import lombok.Builder;
import lombok.Value;

/**
 * Startup runtime configuration for Stage 15 addressing trait selection.
 *
 * <p>Addressing trait selection is a system-level decision and is bound once
 * when {@code RouteCore} starts. Request payloads cannot switch the trait.</p>
 */
@Value
@Builder
public class AddressingRuntimeConfig {

    /**
     * Selected addressing trait id (for example {@code DEFAULT} or {@code EXTERNAL_ID_ONLY}).
     */
    String addressingTraitId;

    /**
     * Returns convenience config for the default addressing trait.
     */
    public static AddressingRuntimeConfig defaultRuntime() {
        return AddressingRuntimeConfig.builder()
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .build();
    }

    /**
     * Returns convenience config for the external-id-only trait.
     */
    public static AddressingRuntimeConfig externalIdOnlyRuntime() {
        return AddressingRuntimeConfig.builder()
                .addressingTraitId(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY)
                .build();
    }
}
