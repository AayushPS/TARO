package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.traits.addressing.AddressingPolicy;
import org.Aayush.routing.traits.addressing.AddressingRuntimeBinder;
import org.Aayush.routing.traits.addressing.AddressingTelemetry;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.AddressingTraitEngine;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.addressing.ResolvedAddress;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;

import java.util.List;

/**
 * Extension seam for translating client requests into immutable internal requests.
 */
public interface RequestNormalizer {
    /**
     * Normalizes one route request.
     */
    NormalizedRouteRequest normalizeRoute(RouteRequest request, Context context);

    /**
     * Normalizes one matrix request.
     */
    NormalizedMatrixRequest normalizeMatrix(MatrixRequest request, Context context);

    /**
     * Full request-normalization context.
     */
    @Value
    @Builder
    class Context {
        EdgeGraph edgeGraph;
        IDMapper nodeIdMapper;
        SpatialRuntime spatialRuntime;
        AddressingTraitEngine addressingTraitEngine;
        AddressingTraitCatalog addressingTraitCatalog;
        CoordinateStrategyRegistry coordinateStrategyRegistry;
        AddressingPolicy addressingPolicy;
        AddressingRuntimeBinder.Binding addressingRuntimeBinding;
        ResolvedTemporalContext resolvedTemporalContext;
        ResolvedTransitionContext resolvedTransitionContext;
        ResolvedExecutionProfileContext resolvedExecutionProfileContext;
    }

    /**
     * Normalized route request plus endpoint metadata.
     */
    @Value
    class NormalizedRouteRequest {
        InternalRouteRequest internalRequest;
        ResolvedAddress sourceResolvedAddress;
        ResolvedAddress targetResolvedAddress;
        AddressingTelemetry addressingTelemetry;
    }

    /**
     * Normalized matrix request plus source/target id mapping and telemetry.
     */
    @Value
    class NormalizedMatrixRequest {
        InternalMatrixRequest internalRequest;
        List<String> sourceExternalIds;
        List<String> targetExternalIds;
        AddressingTelemetry addressingTelemetry;

        public NormalizedMatrixRequest(
                InternalMatrixRequest internalRequest,
                List<String> sourceExternalIds,
                List<String> targetExternalIds,
                AddressingTelemetry addressingTelemetry
        ) {
            this.internalRequest = internalRequest;
            this.sourceExternalIds = List.copyOf(sourceExternalIds);
            this.targetExternalIds = List.copyOf(targetExternalIds);
            this.addressingTelemetry = addressingTelemetry;
        }
    }
}
