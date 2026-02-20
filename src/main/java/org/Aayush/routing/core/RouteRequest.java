package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.AddressInput;

/**
 * Client-facing point-to-point route request.
 *
 * <p>Node identifiers are expressed in external id space. Mapping into internal
 * graph node ids is handled by {@link RouteCore}.</p>
 */
@Value
@Builder
public class RouteRequest {
    /** External identifier for route origin node. */
    String sourceExternalId;
    /** External identifier for route destination node. */
    String targetExternalId;
    /** Typed route origin address (Stage 15 path). */
    AddressInput sourceAddress;
    /** Typed route destination address (Stage 15 path). */
    AddressInput targetAddress;
    /** Selected addressing trait id for typed addressing mode. */
    String addressingTraitId;
    /** Selected coordinate strategy id for coordinate-based addressing. */
    String coordinateDistanceStrategyId;
    /** Explicit enable for mixed address-type requests. */
    Boolean allowMixedAddressing;
    /** Max coordinate snap distance (strategy-relative units). */
    Double maxSnapDistance;
    /** Departure time in engine ticks. */
    long departureTicks;
    /** Search algorithm to execute. */
    RoutingAlgorithm algorithm;
    /** Heuristic mode to use (must match algorithm constraints). */
    HeuristicType heuristicType;
}
