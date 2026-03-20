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
    /** Explicit enable for mixed address-type requests. */
    Boolean allowMixedAddressing;
    /** Max coordinate snap distance (strategy-relative units). */
    Double maxSnapDistance;
    /** Shared departure tick applied to each source/target pair. */
    long departureTicks;
    /** Deprecated startup-lock hint for the execution algorithm selected at startup. */
    @Deprecated
    RoutingAlgorithm algorithm;
    /** Deprecated startup-lock hint for the heuristic selected at startup. */
    @Deprecated
    HeuristicType heuristicType;
}
