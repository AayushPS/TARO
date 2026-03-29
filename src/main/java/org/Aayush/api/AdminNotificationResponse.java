package org.Aayush.api;

import java.time.Instant;

/**
 * Caller-scoped admin notification emitted by dataset upload, training completion, and publish events.
 */
public record AdminNotificationResponse(
        String notificationId,
        String type,
        String title,
        String detail,
        Instant createdAt,
        String relatedDatasetId,
        String relatedJobId
) {
}
