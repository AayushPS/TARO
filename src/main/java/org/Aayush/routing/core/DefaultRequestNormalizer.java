package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.AddressingTraitEngine;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;

import java.util.Objects;

/**
 * Default request normalizer that binds request payloads to startup-selected execution mode.
 */
public final class DefaultRequestNormalizer implements RequestNormalizer {
    /**
     * Normalizes one route request against the locked execution profile.
     */
    @Override
    public NormalizedRouteRequest normalizeRoute(RouteRequest request, Context context) {
        if (request == null) {
            throw new RouteCoreException(RouteCore.REASON_ROUTE_REQUEST_REQUIRED, "route request must be provided");
        }

        Context nonNullContext = Objects.requireNonNull(context, "context");
        validateExecutionHints(
                request.getAlgorithm(),
                request.getHeuristicType(),
                nonNullContext.getResolvedExecutionProfileContext()
        );

        AddressingTraitEngine.RouteResolution addressing = nonNullContext.getAddressingTraitEngine().resolveRoute(
                request,
                addressingContext(nonNullContext)
        );
        ResolvedExecutionProfileContext executionProfileContext = nonNullContext.getResolvedExecutionProfileContext();
        InternalRouteRequest internalRequest = new InternalRouteRequest(
                addressing.sourceNodeId(),
                addressing.targetNodeId(),
                request.getDepartureTicks(),
                executionProfileContext.getAlgorithm(),
                executionProfileContext.getHeuristicType(),
                nonNullContext.getResolvedTemporalContext(),
                nonNullContext.getResolvedTransitionContext()
        );

        return new NormalizedRouteRequest(
                internalRequest,
                addressing.sourceResolvedAddress(),
                addressing.targetResolvedAddress(),
                addressing.telemetry()
        );
    }

    /**
     * Normalizes one matrix request against the locked execution profile.
     */
    @Override
    public NormalizedMatrixRequest normalizeMatrix(MatrixRequest request, Context context) {
        if (request == null) {
            throw new RouteCoreException(RouteCore.REASON_MATRIX_REQUEST_REQUIRED, "matrix request must be provided");
        }

        Context nonNullContext = Objects.requireNonNull(context, "context");
        validateExecutionHints(
                request.getAlgorithm(),
                request.getHeuristicType(),
                nonNullContext.getResolvedExecutionProfileContext()
        );

        AddressingTraitEngine.MatrixResolution addressing = nonNullContext.getAddressingTraitEngine().resolveMatrix(
                request,
                addressingContext(nonNullContext)
        );
        ResolvedExecutionProfileContext executionProfileContext = nonNullContext.getResolvedExecutionProfileContext();
        InternalMatrixRequest internalRequest = new InternalMatrixRequest(
                addressing.sourceNodeIds(),
                addressing.targetNodeIds(),
                request.getDepartureTicks(),
                executionProfileContext.getAlgorithm(),
                executionProfileContext.getHeuristicType(),
                nonNullContext.getResolvedTemporalContext(),
                nonNullContext.getResolvedTransitionContext()
        );

        return new NormalizedMatrixRequest(
                internalRequest,
                addressing.sourceExternalIds(),
                addressing.targetExternalIds(),
                addressing.telemetry()
        );
    }

    private AddressingTraitEngine.ResolveContext addressingContext(Context context) {
        return new AddressingTraitEngine.ResolveContext(
                context.getEdgeGraph(),
                context.getNodeIdMapper(),
                context.getSpatialRuntime(),
                context.getAddressingTraitCatalog(),
                context.getCoordinateStrategyRegistry(),
                context.getAddressingPolicy(),
                context.getAddressingRuntimeBinding()
        );
    }

    private void validateExecutionHints(
            RoutingAlgorithm requestedAlgorithm,
            HeuristicType requestedHeuristicType,
            ResolvedExecutionProfileContext executionProfileContext
    ) {
        if (requestedAlgorithm != null && requestedAlgorithm != executionProfileContext.getAlgorithm()) {
            throw new RouteCoreException(
                    RouteCore.REASON_REQUEST_EXECUTION_SELECTOR_MISMATCH,
                    "request algorithm " + requestedAlgorithm
                            + " does not match startup execution profile " + executionProfileContext.getAlgorithm()
            );
        }
        if (requestedHeuristicType != null && requestedHeuristicType != executionProfileContext.getHeuristicType()) {
            throw new RouteCoreException(
                    RouteCore.REASON_REQUEST_EXECUTION_SELECTOR_MISMATCH,
                    "request heuristicType " + requestedHeuristicType
                            + " does not match startup execution profile "
                            + executionProfileContext.getHeuristicType()
            );
        }
    }
}
