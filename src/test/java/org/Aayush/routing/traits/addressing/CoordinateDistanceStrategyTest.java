package org.Aayush.routing.traits.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Coordinate Distance Strategy Tests")
class CoordinateDistanceStrategyTest {

    @Test
    @DisplayName("Default max snap distance is null unless overridden")
    void testDefaultMaxSnapDistance() {
        CoordinateDistanceStrategy strategy = new CoordinateDistanceStrategy() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public void validate(double first, double second) {
            }

            @Override
            public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
                return 0.0d;
            }
        };

        assertNull(strategy.defaultMaxSnapDistance());
    }

    @Test
    @DisplayName("Coordinate validation exceptions retain reason codes and messages")
    void testCoordinateValidationExceptionCarriesReasonCode() {
        CoordinateDistanceStrategy.CoordinateValidationException exception =
                new CoordinateDistanceStrategy.CoordinateValidationException("BAD_COORDS", "coordinates invalid");

        assertEquals("BAD_COORDS", exception.getReasonCode());
        assertEquals("coordinates invalid", exception.getMessage());
    }
}
