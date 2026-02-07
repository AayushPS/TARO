package org.Aayush.routing.spatial;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Immutable nearest-neighbor match result for spatial queries.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public final class SpatialMatch {
    private final int nodeId;
    private final double nodeX;
    private final double nodeY;
    private final double distanceSquared;
}
