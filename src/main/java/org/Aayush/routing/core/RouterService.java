package org.Aayush.routing.core;

/**
 * Public route and matrix service contract.
 *
 * <p>Implementations are expected to perform deterministic input validation and
 * throw reason-coded runtime exceptions for contract failures.</p>
 */
public interface RouterService {
    /**
     * Executes one point-to-point route request.
     *
     * @param request client route request.
     * @return route response for the requested source/target pair.
     */
    RouteResponse route(RouteRequest request);

    /**
     * Executes a many-to-many matrix request.
     *
     * @param request client matrix request.
     * @return matrix response for all source/target pairs.
     */
    MatrixResponse matrix(MatrixRequest request);
}
