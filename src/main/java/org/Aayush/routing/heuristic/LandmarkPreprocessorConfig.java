package org.Aayush.routing.heuristic;

import lombok.Builder;
import lombok.Value;

/**
 * Stage 12 configuration for landmark preprocessing.
 */
@Value
@Builder
public class LandmarkPreprocessorConfig {
    /**
     * Requested number of landmarks. Effective count is clamped to [1, nodeCount].
     */
    int landmarkCount;

    /**
     * Deterministic seed for landmark node selection.
     */
    long selectionSeed;

    /**
     * Per-landmark Dijkstra settle budget. {@link Integer#MAX_VALUE} means unbounded.
     */
    @Builder.Default
    int maxSettledNodesPerLandmark = Integer.MAX_VALUE;
}

