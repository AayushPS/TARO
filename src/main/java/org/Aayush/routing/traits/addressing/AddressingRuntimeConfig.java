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
     * Selected coordinate strategy id locked at startup for coordinate-capable addressing traits.
     */
    String coordinateDistanceStrategyId;

    /**
     * Returns convenience config for the default addressing trait with XY coordinate strategy.
     */
    public static AddressingRuntimeConfig defaultRuntime() {
        return xyRuntime();
    }

    /**
     * Returns convenience config for the default addressing trait with XY coordinate strategy.
     */
    public static AddressingRuntimeConfig xyRuntime() {
        return AddressingRuntimeConfig.builder()
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .build();
    }

    /**
     * Returns convenience config for the default addressing trait with LAT_LON coordinate strategy.
     */
    public static AddressingRuntimeConfig latLonRuntime() {
        return AddressingRuntimeConfig.builder()
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_LAT_LON)
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
