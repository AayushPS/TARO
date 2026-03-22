package org.Aayush.routing.profile;

import java.util.Map;
import java.util.Objects;

/**
 * Optional runtime-side recency calibration overrides keyed by profile id.
 *
 * <p>This mirrors the recurrence calibration posture: publication-time source
 * metadata can carry explicit last-observed timestamps into serving without
 * changing the compiled flatbuffer profile artifact.</p>
 */
public final class ProfileRecencyCalibrationStore {

    public enum CalibrationSource {
        EXPLICIT_SOURCE
    }

    public record ProfileRecencyCalibration(
            long lastObservedAtTicks,
            CalibrationSource calibrationSource
    ) {
        public ProfileRecencyCalibration {
            Objects.requireNonNull(calibrationSource, "calibrationSource");
        }
    }

    private final Map<Integer, ProfileRecencyCalibration> calibrationByProfileId;

    public ProfileRecencyCalibrationStore(Map<Integer, ProfileRecencyCalibration> calibrationByProfileId) {
        this.calibrationByProfileId = Map.copyOf(Objects.requireNonNull(calibrationByProfileId, "calibrationByProfileId"));
    }

    public boolean hasCalibration(int profileId) {
        return calibrationByProfileId.containsKey(profileId);
    }

    public ProfileRecencyCalibration calibration(int profileId) {
        return calibrationByProfileId.get(profileId);
    }

    public static ProfileRecencyCalibrationStore empty() {
        return new ProfileRecencyCalibrationStore(Map.of());
    }
}
