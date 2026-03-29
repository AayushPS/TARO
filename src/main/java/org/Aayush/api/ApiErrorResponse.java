package org.Aayush.api;

import java.time.Instant;

/**
 * Stage F1 JSON error envelope for the TARO HTTP/API surface.
 * Satisfies closure criterion: request validation and retained-result error contracts are explicit.
 */
public record ApiErrorResponse(
        String code,
        int status,
        String message,
        String path,
        Instant timestamp
) {
}
