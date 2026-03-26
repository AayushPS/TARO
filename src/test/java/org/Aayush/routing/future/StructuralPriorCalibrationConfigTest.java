package org.Aayush.routing.future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Structural Prior Calibration Config Tests")
class StructuralPriorCalibrationConfigTest {

    @Test
    @DisplayName("Defaults expose the canonical B6 policy")
    void testDefaults() {
        StructuralPriorCalibrationConfig defaults = StructuralPriorCalibrationConfig.defaults();

        assertEquals("b6-structural-prior-v1", defaults.policyId());
        assertEquals(0.06d, defaults.degreeAdjustmentRange(), 1.0e-9d);
        assertEquals(0.25d, defaults.homophilyLowThreshold(), 1.0e-9d);
        assertEquals(0.75d, defaults.homophilyHighThreshold(), 1.0e-9d);
    }

    @Test
    @DisplayName("Validation rejects impossible threshold and range combinations")
    void testValidationRejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralPriorCalibrationConfig(" ", 0.06d, 0.25d, 0.75d));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralPriorCalibrationConfig("b6", -0.01d, 0.25d, 0.75d));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralPriorCalibrationConfig("b6", 0.06d, -0.01d, 0.75d));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralPriorCalibrationConfig("b6", 0.06d, 0.25d, 1.01d));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralPriorCalibrationConfig("b6", 0.06d, 0.80d, 0.20d));
    }
}
