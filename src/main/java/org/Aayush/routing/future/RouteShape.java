package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.ResolvedAddress;

import java.util.List;

/**
 * Geometry and endpoint metadata for one selected route.
 */
@Value
@Builder(toBuilder = true)
public class RouteShape {
    boolean reachable;
    long departureTicks;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
    ResolvedAddress sourceResolvedAddress;
    ResolvedAddress targetResolvedAddress;
    @Singular("pathNode")
    List<String> pathExternalNodeIds;
    List<PathPoint> pathPoints;

    public static RouteShape fromRouteResponse(RouteResponse response) {
        return fromRouteResponse(response, null);
    }

    public static RouteShape fromRouteResponse(RouteResponse response, RouteCore routeCore) {
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
        if (routeCore != null) {
            builder.pathPoints(toPathPoints(routeCore.pathCoordinates(response.getPathExternalNodeIds())));
        }
        return builder.build();
    }

    private static List<PathPoint> toPathPoints(List<EdgeGraph.Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return List.of();
        }
        return coordinates.stream()
                .map(coordinate -> new PathPoint(coordinate.x, coordinate.y))
                .toList();
    }

    public record PathPoint(double x, double y) {
    }
}
