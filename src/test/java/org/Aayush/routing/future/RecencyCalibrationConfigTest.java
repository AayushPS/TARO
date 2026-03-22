package org.Aayush.routing.future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Recency Calibration Config Tests")
class RecencyCalibrationConfigTest {

    @Test
    @DisplayName("Defaults expose the canonical B4 policy values")
    void testDefaultsExposeCanonicalPolicyValues() {
        RecencyCalibrationConfig defaults = RecencyCalibrationConfig.defaults();

        assertEquals("b4-recency-v1", defaults.policyId());
        assertEquals(Duration.ofMinutes(45), defaults.freshnessHalfLife());
        assertEquals(0.20d, defaults.freshnessFloor(), 1.0e-9d);
        assertEquals(Duration.ofHours(6), defaults.horizonHalfLife());
        assertEquals(0.35d, defaults.horizonFloor(), 1.0e-9d);
        assertEquals(0.40d, defaults.recurringScaleFloor(), 1.0e-9d);
        assertEquals(0.60d, defaults.recurringScaleRange(), 1.0e-9d);
        assertEquals(0.35d, defaults.incidentPersistsBaseProbability(), 1.0e-9d);
        assertEquals(0.50d, defaults.incidentPersistsRange(), 1.0e-9d);
        assertEquals(0.35d, defaults.minIncidentPersistsProbability(), 1.0e-9d);
        assertEquals(0.85d, defaults.maxIncidentPersistsProbability(), 1.0e-9d);
    }

    @Test
    @DisplayName("Invalid policy ids and half-life durations are rejected")
    void testRejectsInvalidPolicyIdsAndDurations() {
        assertThrows(IllegalArgumentException.class, () -> config("", Duration.ofMinutes(45), Duration.ofHours(6)));
        assertThrows(IllegalArgumentException.class, () -> config(" ", Duration.ofMinutes(45), Duration.ofHours(6)));
        assertThrows(IllegalArgumentException.class, () -> config("ok", Duration.ZERO, Duration.ofHours(6)));
        assertThrows(IllegalArgumentException.class, () -> config("ok", Duration.ofMinutes(45), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> config("ok", Duration.ofMinutes(-1), Duration.ofHours(6)));
        assertThrows(IllegalArgumentException.class, () -> config("ok", Duration.ofMinutes(45), Duration.ofHours(-1)));
    }

    @Test
    @DisplayName("Out-of-range fractions and inconsistent bounds are rejected")
    void testRejectsOutOfRangeFractionsAndInconsistentBounds() {
        assertThrows(IllegalArgumentException.class, () -> new RecencyCalibrationConfig(
                "ok",
                Duration.ofMinutes(45),
                Double.NaN,
                Duration.ofHours(6),
                0.35d,
                0.40d,
                0.60d,
                0.35d,
                0.50d,
                0.35d,
                0.85d
        ));
        assertThrows(IllegalArgumentException.class, () -> new RecencyCalibrationConfig(
                "ok",
                Duration.ofMinutes(45),
                0.20d,
                Duration.ofHours(6),
                1.10d,
                0.40d,
                0.60d,
                0.35d,
                0.50d,
                0.35d,
                0.85d
        ));
        assertThrows(IllegalArgumentException.class, () -> new RecencyCalibrationConfig(
                "ok",
                Duration.ofMinutes(45),
                0.20d,
                Duration.ofHours(6),
                0.35d,
                0.60d,
                0.50d,
                0.35d,
                0.50d,
                0.35d,
                0.85d
        ));
        assertThrows(IllegalArgumentException.class, () -> new RecencyCalibrationConfig(
                "ok",
                Duration.ofMinutes(45),
                0.20d,
                Duration.ofHours(6),
                0.35d,
                0.40d,
                0.60d,
                0.35d,
                0.50d,
                0.90d,
                0.85d
        ));
    }

    private RecencyCalibrationConfig config(String policyId, Duration freshnessHalfLife, Duration horizonHalfLife) {
        return new RecencyCalibrationConfig(
                policyId,
                freshnessHalfLife,
                0.20d,
                horizonHalfLife,
                0.35d,
                0.40d,
                0.60d,
                0.35d,
                0.50d,
                0.35d,
                0.85d
        );
    }
}
