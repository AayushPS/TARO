package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.AddressingTraitEngine;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;

import java.util.Objects;

/**
 * Default request normalizer that binds request payloads to the startup-selected
 * execution profile.
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
        AddressingTraitEngine.RouteResolution addressing = nonNullContext.getAddressingTraitEngine().resolveRoute(
                request,
                addressingContext(nonNullContext)
        );
        ResolvedExecutionProfileContext executionProfileContext =
                requireBoundExecutionProfile(nonNullContext.getResolvedExecutionProfileContext());
        RoutingAlgorithm algorithm = executionProfileContext.getAlgorithm();
        HeuristicType heuristicType = resolveHeuristicType(algorithm, executionProfileContext);
        InternalRouteRequest internalRequest = new InternalRouteRequest(
                addressing.sourceNodeId(),
                addressing.targetNodeId(),
                request.getDepartureTicks(),
                algorithm,
                heuristicType,
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
        AddressingTraitEngine.MatrixResolution addressing = nonNullContext.getAddressingTraitEngine().resolveMatrix(
                request,
                addressingContext(nonNullContext)
        );
        ResolvedExecutionProfileContext executionProfileContext =
                requireBoundExecutionProfile(nonNullContext.getResolvedExecutionProfileContext());
        RoutingAlgorithm algorithm = executionProfileContext.getAlgorithm();
        HeuristicType heuristicType = resolveHeuristicType(algorithm, executionProfileContext);
        InternalMatrixRequest internalRequest = new InternalMatrixRequest(
                addressing.sourceNodeIds(),
                addressing.targetNodeIds(),
                request.getDepartureTicks(),
                algorithm,
                heuristicType,
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

    private ResolvedExecutionProfileContext requireBoundExecutionProfile(
            ResolvedExecutionProfileContext executionProfileContext
    ) {
        ResolvedExecutionProfileContext nonNullContext =
                Objects.requireNonNull(executionProfileContext, "executionProfileContext");
        if (nonNullContext.getAlgorithm() == null || nonNullContext.getHeuristicType() == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_CONFIG_REQUIRED,
                    "request normalization requires a startup execution profile"
            );
        }
        return nonNullContext;
    }

    private HeuristicType resolveHeuristicType(
            RoutingAlgorithm algorithm,
            ResolvedExecutionProfileContext executionProfileContext
    ) {
        HeuristicType heuristicType = executionProfileContext.getHeuristicType();
        if (algorithm == RoutingAlgorithm.DIJKSTRA && heuristicType != HeuristicType.NONE) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_PROFILE_INCOMPATIBLE,
                    "DIJKSTRA execution profile requires heuristicType NONE"
            );
        }
        return heuristicType;
    }
}
