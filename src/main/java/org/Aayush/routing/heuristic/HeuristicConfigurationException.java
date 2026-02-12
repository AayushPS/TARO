package org.Aayush.routing.heuristic;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Objects;

/**
 * Thrown when Stage 11 heuristic contracts cannot be satisfied.
 *
 * <p>Messages are prefixed with deterministic reason-code text for observability.</p>
 */
@Getter
@Accessors(fluent = true)
public final class HeuristicConfigurationException extends RuntimeException {
    private final String reasonCode;

    /**
     * Creates a reason-coded configuration failure.
     *
     * @param reasonCode deterministic reason code.
     * @param message descriptive message.
     */
    public HeuristicConfigurationException(String reasonCode, String message) {
        super(formatMessage(reasonCode, message));
        this.reasonCode = requireReasonCode(reasonCode);
    }

    /**
     * Creates a reason-coded configuration failure with a cause.
     *
     * @param reasonCode deterministic reason code.
     * @param message descriptive message.
     * @param cause underlying exception.
     */
    public HeuristicConfigurationException(String reasonCode, String message, Throwable cause) {
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
