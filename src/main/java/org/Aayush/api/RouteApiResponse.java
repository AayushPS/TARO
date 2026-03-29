package org.Aayush.api;

import org.Aayush.routing.future.RetainedRouteResultView;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;

/**
 * Stage F1 route evaluation response envelope.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware route results without recomputing them.
 */
public record RouteApiResponse(
        String resultSetId,
        boolean retained,
        Instant expiresAt,
        TopologyVersion topologyVersion,
        RetainedRouteResultView.Summary summary
) {
}
