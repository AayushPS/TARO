package org.Aayush.api;

import java.time.Instant;

/**
 * Stage F1 admin maintenance response for retained-result purge operations.
 * Satisfies closure criterion: admin behavior around retained-result access is explicit.
 */
public record AdminPurgeResponse(
        Instant purgedAt,
        boolean routeServiceConfigured,
        boolean matrixServiceConfigured,
        String activeTopologyVersion,
        int callerScopedResultCount
) {
}
