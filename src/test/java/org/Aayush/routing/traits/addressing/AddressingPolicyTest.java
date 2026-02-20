package org.Aayush.routing.traits.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AddressingPolicy Tests")
class AddressingPolicyTest {

    @Test
    @DisplayName("Default policy resolves strategy-specific snap-distance defaults")
    void testDefaultsResolveByStrategy() {
        AddressingPolicy policy = AddressingPolicy.defaults();

        assertEquals(1_000.0d, policy.resolveMaxSnapDistance(CoordinateStrategyRegistry.STRATEGY_XY, null), 1e-9);
        assertEquals(250.0d, policy.resolveMaxSnapDistance(CoordinateStrategyRegistry.STRATEGY_LAT_LON, null), 1e-9);
        assertEquals(1_000.0d, policy.resolveMaxSnapDistance("UNKNOWN_STRATEGY", null), 1e-9);
    }

    @Test
    @DisplayName("Override validation rejects non-finite, negative, and over-max values")
    void testOverrideValidationRejectsInvalidValues() {
        AddressingPolicy policy = AddressingPolicy.builder()
                .minSnapDistance(0.0d)
                .maxSnapDistance(10.0d)
                .build();

        assertThrows(IllegalArgumentException.class, () -> policy.resolveMaxSnapDistance("XY", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> policy.resolveMaxSnapDistance("XY", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> policy.resolveMaxSnapDistance("XY", -0.1d));
        assertThrows(IllegalArgumentException.class, () -> policy.resolveMaxSnapDistance("XY", 10.1d));
    }

    @Test
    @DisplayName("Override validation accepts min and max boundary values")
    void testOverrideValidationAcceptsBoundaries() {
        AddressingPolicy policy = AddressingPolicy.builder()
                .minSnapDistance(0.0d)
                .maxSnapDistance(10.0d)
                .build();

        assertEquals(0.0d, policy.resolveMaxSnapDistance("XY", 0.0d), 1e-9);
        assertEquals(10.0d, policy.resolveMaxSnapDistance("XY", 10.0d), 1e-9);
    }

    @Test
    @DisplayName("Strategy-specific defaults override global fallback for custom strategies")
    void testStrategySpecificDefaultOverride() {
        AddressingPolicy policy = AddressingPolicy.defaults();
        CoordinateDistanceStrategy customStrategy = new FixedDefaultStrategy("CUSTOM", 12.5d);

        assertEquals(12.5d, policy.resolveMaxSnapDistance("CUSTOM", null, customStrategy), 1e-9);
    }

    private record FixedDefaultStrategy(String id, double maxSnapDefault) implements CoordinateDistanceStrategy {
        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException("CUSTOM_NON_FINITE", "coordinates must be finite");
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            return 0.0d;
        }

        @Override
        public Double defaultMaxSnapDistance() {
            return maxSnapDefault;
        }
    }
}
