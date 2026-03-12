package org.Aayush.routing.traits.registry;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;

/**
 * Immutable Stage 18 trait-bundle selection spec.
 */
@Value
@Builder
public class TraitBundleSpec {
    String bundleId;
    String addressingTraitId;
    String coordinateDistanceStrategyId;
    String temporalTraitId;
    String timezonePolicyId;
    String modelProfileTimezone;
    String transitionTraitId;

    /**
     * Creates one inline trait-bundle spec from legacy per-axis runtime configs.
     */
    public static TraitBundleSpec fromLegacyConfigs(
            AddressingRuntimeConfig addressingRuntimeConfig,
            TemporalRuntimeConfig temporalRuntimeConfig,
            TransitionRuntimeConfig transitionRuntimeConfig
    ) {
        return TraitBundleSpec.builder()
                .addressingTraitId(addressingRuntimeConfig.getAddressingTraitId())
                .coordinateDistanceStrategyId(addressingRuntimeConfig.getCoordinateDistanceStrategyId())
                .temporalTraitId(temporalRuntimeConfig.getTemporalTraitId())
                .timezonePolicyId(temporalRuntimeConfig.getTimezonePolicyId())
                .modelProfileTimezone(temporalRuntimeConfig.getModelProfileTimezone())
                .transitionTraitId(transitionRuntimeConfig.getTransitionTraitId())
                .build();
    }

    /**
     * Returns this bundle selection as one addressing runtime config.
     */
    public AddressingRuntimeConfig toAddressingRuntimeConfig() {
        return AddressingRuntimeConfig.builder()
                .addressingTraitId(addressingTraitId)
                .coordinateDistanceStrategyId(coordinateDistanceStrategyId)
                .build();
    }

    /**
     * Returns this bundle selection as one temporal runtime config.
     */
    public TemporalRuntimeConfig toTemporalRuntimeConfig() {
        return TemporalRuntimeConfig.builder()
                .temporalTraitId(temporalTraitId)
                .timezonePolicyId(timezonePolicyId)
                .modelProfileTimezone(modelProfileTimezone)
                .build();
    }

    /**
     * Returns this bundle selection as one transition runtime config.
     */
    public TransitionRuntimeConfig toTransitionRuntimeConfig() {
        return TransitionRuntimeConfig.builder()
                .transitionTraitId(transitionTraitId)
                .build();
    }
}
