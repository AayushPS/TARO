package org.Aayush.routing.core;

/**
 * Stage 12 route-core facade contract.
 */
public interface RouterService {
    RouteResponse route(RouteRequest request);
    MatrixResponse matrix(MatrixRequest request);
}

