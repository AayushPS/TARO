package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

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
    /** Shared departure tick applied to each source/target pair. */
    long departureTicks;
    /** Search algorithm to execute for each pair. */
    RoutingAlgorithm algorithm;
    /** Heuristic mode to use (must match algorithm constraints). */
    HeuristicType heuristicType;
}
