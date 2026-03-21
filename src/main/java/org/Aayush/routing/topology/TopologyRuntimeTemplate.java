package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.heuristic.LandmarkPreprocessorConfig;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;

/**
 * Runtime template applied whenever a rebuilt topology snapshot is materialized.
 */
@Value
@Builder(toBuilder = true)
public class TopologyRuntimeTemplate {
    ExecutionRuntimeConfig executionRuntimeConfig;
    TraitBundleRuntimeConfig traitBundleRuntimeConfig;
    AddressingRuntimeConfig addressingRuntimeConfig;
    TemporalRuntimeConfig temporalRuntimeConfig;
    TransitionRuntimeConfig transitionRuntimeConfig;
    LandmarkPreprocessorConfig landmarkPreprocessorConfig;
    @Builder.Default
    int bucketSizeSeconds = 3_600;
    @Builder.Default
    CostEngine.TemporalSamplingPolicy temporalSamplingPolicy = CostEngine.TemporalSamplingPolicy.INTERPOLATED;
    @Builder.Default
    int liveOverlayCapacity = 256;
    @Builder.Default
    LiveOverlay.CapacityPolicy liveOverlayCapacityPolicy = LiveOverlay.CapacityPolicy.EVICT_EXPIRED_THEN_REJECT;
    @Builder.Default
    int liveOverlayWriteCleanupBudget = 32;
    @Builder.Default
    boolean liveOverlayReadCleanupEnabled = true;
    @Builder.Default
    boolean spatialEnabled = true;
    @Builder.Default
    boolean preserveActiveLiveOverlay = true;
}
