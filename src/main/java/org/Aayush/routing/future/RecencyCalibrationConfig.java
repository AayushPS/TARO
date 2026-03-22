package org.Aayush.routing.future;

import java.time.Duration;
import java.util.Objects;

/**
 * Tunable recency weighting policy for B4 scenario probability calibration.
 */
public record RecencyCalibrationConfig(
        String policyId,
        Duration freshnessHalfLife,
        double freshnessFloor,
        Duration horizonHalfLife,
        double horizonFloor,
        double recurringScaleFloor,
        double recurringScaleRange,
        double incidentPersistsBaseProbability,
        double incidentPersistsRange,
        double minIncidentPersistsProbability,
        double maxIncidentPersistsProbability
) {
    public RecencyCalibrationConfig {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must be non-blank");
        }
        requirePositive(freshnessHalfLife, "freshnessHalfLife");
        requirePositive(horizonHalfLife, "horizonHalfLife");
        requireFraction(freshnessFloor, "freshnessFloor");
        requireFraction(horizonFloor, "horizonFloor");
        requireFraction(recurringScaleFloor, "recurringScaleFloor");
        requireFraction(recurringScaleRange, "recurringScaleRange");
        requireFraction(incidentPersistsBaseProbability, "incidentPersistsBaseProbability");
        requireFraction(incidentPersistsRange, "incidentPersistsRange");
        requireFraction(minIncidentPersistsProbability, "minIncidentPersistsProbability");
        requireFraction(maxIncidentPersistsProbability, "maxIncidentPersistsProbability");
        if (recurringScaleFloor + recurringScaleRange > 1.0d + 1.0e-9d) {
            throw new IllegalArgumentException("recurring scale floor + range must be <= 1.0");
        }
        if (minIncidentPersistsProbability > maxIncidentPersistsProbability) {
            throw new IllegalArgumentException("minIncidentPersistsProbability must be <= maxIncidentPersistsProbability");
        }
    }

    public static RecencyCalibrationConfig defaults() {
        return new RecencyCalibrationConfig(
                "b4-recency-v1",
                Duration.ofMinutes(45),
                0.20d,
                Duration.ofHours(6),
                0.35d,
                0.40d,
                0.60d,
                0.35d,
                0.50d,
                0.35d,
                0.85d
        );
    }

    private static void requirePositive(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    private static void requireFraction(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite within [0.0, 1.0]");
        }
    }
}
