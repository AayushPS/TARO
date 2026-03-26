package org.Aayush.routing.future;

/**
 * Tunable B6 structural-prior calibration policy for incident-like scenarios.
 */
public record StructuralPriorCalibrationConfig(
        String policyId,
        double degreeAdjustmentRange,
        double homophilyLowThreshold,
        double homophilyHighThreshold
) {
    public StructuralPriorCalibrationConfig {
        requireFraction(degreeAdjustmentRange, "degreeAdjustmentRange");
        requireFraction(homophilyLowThreshold, "homophilyLowThreshold");
        requireFraction(homophilyHighThreshold, "homophilyHighThreshold");
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must be non-blank");
        }
        if (homophilyLowThreshold > homophilyHighThreshold) {
            throw new IllegalArgumentException("homophilyLowThreshold must be <= homophilyHighThreshold");
        }
    }

    public static StructuralPriorCalibrationConfig defaults() {
        return new StructuralPriorCalibrationConfig(
                "b6-structural-prior-v1",
                0.06d,
                0.25d,
                0.75d
        );
    }

    private static void requireFraction(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite within [0.0, 1.0]");
        }
    }
}
