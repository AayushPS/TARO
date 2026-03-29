package org.Aayush.api;

import org.Aayush.routing.future.RetainedMatrixResultView;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;

/**
 * Stage F1 matrix evaluation response envelope.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware matrix results without recomputing them.
 */
public record MatrixApiResponse(
        String resultSetId,
        boolean retained,
        Instant expiresAt,
        TopologyVersion topologyVersion,
        RetainedMatrixResultView.Summary summary
) {
}
