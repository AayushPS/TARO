package org.Aayush.routing.profile;

import java.util.Map;
import java.util.Objects;

/**
 * Optional runtime-side recurrence calibration overrides keyed by profile id.
 *
 * <p>This store lets publication-time source metadata carry explicit recurrence posture
 * into the shipped resolver path without changing the compiled flatbuffer profile artifact.</p>
 */
public final class ProfileRecurrenceCalibrationStore {

    public enum SignalFlavor {
        NONE,
        ROUTINE_PERIODIC,
        RECURRING_INCIDENT
    }

    public enum CalibrationSource {
        DERIVED_PROFILE_SHAPE,
        EXPLICIT_SOURCE
    }

    public record ProfileRecurrenceCalibration(
            int observationCount,
            float confidence,
            SignalFlavor signalFlavor,
            CalibrationSource calibrationSource
    ) {
        public ProfileRecurrenceCalibration {
            if (observationCount < 0) {
                throw new IllegalArgumentException("observationCount must be >= 0");
            }
            if (!Float.isFinite(confidence) || confidence < 0.0f || confidence > 1.0f) {
                throw new IllegalArgumentException("confidence must be finite within [0.0, 1.0]");
            }
            Objects.requireNonNull(signalFlavor, "signalFlavor");
            Objects.requireNonNull(calibrationSource, "calibrationSource");
        }
    }

    private final Map<Integer, ProfileRecurrenceCalibration> calibrationByProfileId;

    public ProfileRecurrenceCalibrationStore(Map<Integer, ProfileRecurrenceCalibration> calibrationByProfileId) {
        this.calibrationByProfileId = Map.copyOf(Objects.requireNonNull(calibrationByProfileId, "calibrationByProfileId"));
    }

    public boolean hasCalibration(int profileId) {
        return calibrationByProfileId.containsKey(profileId);
    }

    public ProfileRecurrenceCalibration calibration(int profileId) {
        return calibrationByProfileId.get(profileId);
    }

    public static ProfileRecurrenceCalibrationStore empty() {
        return new ProfileRecurrenceCalibrationStore(Map.of());
    }
}
