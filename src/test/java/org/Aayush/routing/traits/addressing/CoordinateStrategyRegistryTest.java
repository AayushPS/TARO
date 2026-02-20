package org.Aayush.routing.traits.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CoordinateStrategyRegistry Tests")
class CoordinateStrategyRegistryTest {

    @Test
    @DisplayName("Default registry exposes built-ins and null lookup returns null")
    void testDefaultRegistryShape() {
        CoordinateStrategyRegistry registry = CoordinateStrategyRegistry.defaultRegistry();

        assertNotNull(registry.strategy(CoordinateStrategyRegistry.STRATEGY_XY));
        assertNotNull(registry.strategy(CoordinateStrategyRegistry.STRATEGY_LAT_LON));
        assertEquals(2, registry.strategyIds().size());
        assertNull(registry.strategy(null));
    }

    @Test
    @DisplayName("Null custom strategy collection keeps default built-ins")
    void testNullCustomStrategyCollectionFallsBackToDefaults() {
        CoordinateStrategyRegistry registry =
                new CoordinateStrategyRegistry((Collection<? extends CoordinateDistanceStrategy>) null);

        assertNotNull(registry.strategy(CoordinateStrategyRegistry.STRATEGY_XY));
        assertNotNull(registry.strategy(CoordinateStrategyRegistry.STRATEGY_LAT_LON));
    }

    @Test
    @DisplayName("Custom strategy can override built-in id")
    void testCustomStrategyOverrideBuiltIn() {
        CoordinateDistanceStrategy override = new ConstantDistanceStrategy(CoordinateStrategyRegistry.STRATEGY_XY);
        CoordinateStrategyRegistry registry = new CoordinateStrategyRegistry(List.of(override));

        assertSame(override, registry.strategy(CoordinateStrategyRegistry.STRATEGY_XY));
        assertEquals(42.0d, registry.strategy(CoordinateStrategyRegistry.STRATEGY_XY)
                .distance(0.0d, 0.0d, 1.0d, 1.0d), 1e-9);
    }

    @Test
    @DisplayName("Constructor rejects null and blank custom strategy entries")
    void testConstructorRejectsInvalidEntries() {
        assertThrows(
                NullPointerException.class,
                () -> new CoordinateStrategyRegistry(Arrays.asList((CoordinateDistanceStrategy) null))
        );

        CoordinateDistanceStrategy blankId = new ConstantDistanceStrategy("   ");
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoordinateStrategyRegistry(List.of(blankId))
        );
    }

    @Test
    @DisplayName("XY and LAT_LON validators enforce finite and range contracts")
    void testBuiltInValidationContracts() {
        CoordinateStrategyRegistry registry = CoordinateStrategyRegistry.defaultRegistry();
        CoordinateDistanceStrategy xy = registry.strategy(CoordinateStrategyRegistry.STRATEGY_XY);
        CoordinateDistanceStrategy latLon = registry.strategy(CoordinateStrategyRegistry.STRATEGY_LAT_LON);

        assertDoesNotThrow(() -> xy.validate(1.0d, -1.0d));
        assertThrows(CoordinateDistanceStrategy.CoordinateValidationException.class, () -> xy.validate(Double.NaN, 0.0d));

        assertDoesNotThrow(() -> latLon.validate(-90.0d, -180.0d));
        assertDoesNotThrow(() -> latLon.validate(90.0d, 180.0d));
        assertThrows(CoordinateDistanceStrategy.CoordinateValidationException.class, () -> latLon.validate(90.1d, 0.0d));
        assertThrows(CoordinateDistanceStrategy.CoordinateValidationException.class, () -> latLon.validate(0.0d, 180.1d));
    }

    private record ConstantDistanceStrategy(String id) implements CoordinateDistanceStrategy {
        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException("CUSTOM_NON_FINITE", "coordinates must be finite");
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            return 42.0d;
        }
    }
}
