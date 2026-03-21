package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.ResolvedAddress;

import java.util.List;

/**
 * Geometry and endpoint metadata for one selected route.
 */
@Value
@Builder
public class RouteShape {
    boolean reachable;
    long departureTicks;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
    ResolvedAddress sourceResolvedAddress;
    ResolvedAddress targetResolvedAddress;
    @Singular("pathNode")
    List<String> pathExternalNodeIds;

    public static RouteShape fromRouteResponse(RouteResponse response) {
        RouteShape.RouteShapeBuilder builder = RouteShape.builder()
                .reachable(response.isReachable())
                .departureTicks(response.getDepartureTicks())
                .algorithm(response.getAlgorithm())
                .heuristicType(response.getHeuristicType())
                .sourceResolvedAddress(response.getSourceResolvedAddress())
                .targetResolvedAddress(response.getTargetResolvedAddress());
        for (String nodeId : response.getPathExternalNodeIds()) {
            builder.pathNode(nodeId);
        }
        return builder.build();
    }
}
