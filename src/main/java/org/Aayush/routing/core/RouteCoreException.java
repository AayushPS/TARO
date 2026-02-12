package org.Aayush.routing.core;

import lombok.Getter;

import java.util.Objects;

/**
 * Stage 12 route-core contract exception with deterministic reason codes.
 */
@Getter
public final class RouteCoreException extends RuntimeException {
    private final String reasonCode;

    /**
     * Creates a reason-coded route-core contract failure.
     *
     * @param reasonCode deterministic reason code.
     * @param message descriptive error message.
     */
    public RouteCoreException(String reasonCode, String message) {
        super(formatMessage(reasonCode, message));
        this.reasonCode = requireReasonCode(reasonCode);
    }

    /**
     * Creates a reason-coded route-core contract failure with a cause.
     *
     * @param reasonCode deterministic reason code.
     * @param message descriptive error message.
     * @param cause underlying cause.
     */
    public RouteCoreException(String reasonCode, String message, Throwable cause) {
        super(formatMessage(reasonCode, message), cause);
        this.reasonCode = requireReasonCode(reasonCode);
    }

    /**
     * Formats exception message with deterministic reason-code prefix.
     */
    private static String formatMessage(String reasonCode, String message) {
        return "[" + requireReasonCode(reasonCode) + "] " + Objects.requireNonNull(message, "message");
    }

    /**
     * Validates reason-code contract.
     */
    private static String requireReasonCode(String reasonCode) {
        String code = Objects.requireNonNull(reasonCode, "reasonCode");
        if (code.isBlank()) {
            throw new IllegalArgumentException("reasonCode must be non-blank");
        }
        return code;
    }
}
