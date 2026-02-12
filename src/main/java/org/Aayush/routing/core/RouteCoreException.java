package org.Aayush.routing.core;

import lombok.Getter;

import java.util.Objects;

/**
 * Stage 12 route-core contract exception with deterministic reason codes.
 */
@Getter
public final class RouteCoreException extends RuntimeException {
    private final String reasonCode;

    public RouteCoreException(String reasonCode, String message) {
        super(formatMessage(reasonCode, message));
        this.reasonCode = requireReasonCode(reasonCode);
    }

    public RouteCoreException(String reasonCode, String message, Throwable cause) {
        super(formatMessage(reasonCode, message), cause);
        this.reasonCode = requireReasonCode(reasonCode);
    }

    private static String formatMessage(String reasonCode, String message) {
        return "[" + requireReasonCode(reasonCode) + "] " + Objects.requireNonNull(message, "message");
    }

    private static String requireReasonCode(String reasonCode) {
        String code = Objects.requireNonNull(reasonCode, "reasonCode");
        if (code.isBlank()) {
            throw new IllegalArgumentException("reasonCode must be non-blank");
        }
        return code;
    }
}

