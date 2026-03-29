package org.Aayush.api;

import java.time.Instant;

/**
 * Stage F1 health response for the TARO HTTP/API surface.
 * Satisfies closure criterion: health behavior for future-aware serving is explicit.
 */
public record HealthApiResponse(
        String status,
        Instant observedAt,
        boolean routeServiceConfigured,
        boolean matrixServiceConfigured,
        String activeTopologyVersion,
        String quarantineSnapshotId,
        int callerScopedResultCount,
        String reloadHealth,
        String alertStatus
) {
}
