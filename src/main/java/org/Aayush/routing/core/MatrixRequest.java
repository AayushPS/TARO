package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.addressing.AddressInput;

import java.util.List;

/**
 * Client-facing many-to-many routing request.
 */
@Value
@Builder
public class MatrixRequest {
    /** External source node ids (matrix rows). */
    @Singular("sourceExternalId")
    List<String> sourceExternalIds;
    /** External target node ids (matrix columns). */
    @Singular("targetExternalId")
    List<String> targetExternalIds;
    /** Typed source addresses (matrix rows). */
    @Singular("sourceAddress")
    List<AddressInput> sourceAddresses;
    /** Typed target addresses (matrix columns). */
    @Singular("targetAddress")
    List<AddressInput> targetAddresses;
    /** Selected addressing trait id for typed addressing mode. */
    String addressingTraitId;
    /** Selected coordinate strategy id for coordinate-based addressing. */
    String coordinateDistanceStrategyId;
    /** Explicit enable for mixed address-type requests. */
    Boolean allowMixedAddressing;
    /** Max coordinate snap distance (strategy-relative units). */
    Double maxSnapDistance;
    /** Shared departure tick applied to each source/target pair. */
    long departureTicks;
    /** Search algorithm to execute for each pair. */
    RoutingAlgorithm algorithm;
    /** Heuristic mode to use (must match algorithm constraints). */
    HeuristicType heuristicType;
}
