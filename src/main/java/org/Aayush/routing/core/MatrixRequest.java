package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.List;

/**
 * Client-facing matrix request.
 */
@Value
@Builder
public class MatrixRequest {
    @Singular("sourceExternalId")
    List<String> sourceExternalIds;
    @Singular("targetExternalId")
    List<String> targetExternalIds;
    long departureTicks;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
}

