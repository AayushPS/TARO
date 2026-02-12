package org.Aayush.routing.heuristic;

import java.util.Objects;

/**
 * Thrown when Stage 11 heuristic contracts cannot be satisfied.
 *
 * <p>Messages are prefixed with deterministic reason-code text for observability.</p>
 */
public final class HeuristicConfigurationException extends RuntimeException {
    private final String reasonCode;

    public HeuristicConfigurationException(String reasonCode, String message) {
        super(formatMessage(reasonCode, message));
        this.reasonCode = requireReasonCode(reasonCode);
    }

    public HeuristicConfigurationException(String reasonCode, String message, Throwable cause) {
        super(formatMessage(reasonCode, message), cause);
        this.reasonCode = requireReasonCode(reasonCode);
    }

    public String reasonCode() {
        return reasonCode;
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
