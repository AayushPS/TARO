package org.Aayush.routing.traits.addressing;

import lombok.Getter;

/**
 * Coordinate distance strategy used by Stage 15 coordinate snapping.
 */
public interface CoordinateDistanceStrategy {
    /**
     * Stable strategy id used in request payloads.
     */
    String id();

    /**
     * Validates one coordinate pair for strategy-specific constraints.
     *
     * @throws CoordinateValidationException when the pair violates strategy constraints.
     */
    void validate(double first, double second);

    /**
     * Computes distance between request coordinates and snapped node coordinates.
     */
    double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond);

    /**
     * Optional strategy-specific default max-snap distance.
     *
     * <p>When null, the global addressing policy default for the strategy id is used.</p>
     */
    default Double defaultMaxSnapDistance() {
        return null;
    }

    /**
     * Deterministic validation exception carrying a Stage 15 reason code.
     */
    @Getter
    final class CoordinateValidationException extends RuntimeException {
        private final String reasonCode;

        public CoordinateValidationException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
