package org.Aayush.routing.traits.addressing;

import lombok.Builder;
import lombok.Value;

/**
 * Stage 15 addressing policy for snap-threshold defaults and bounds.
 */
@Value
@Builder
public class AddressingPolicy {
    public static final String REASON_INVALID_MAX_SNAP_DISTANCE = "H15_INVALID_MAX_SNAP_DISTANCE";

    @Builder.Default
    double defaultXyMaxSnapDistance = 1_000.0d;

    @Builder.Default
    double defaultLatLonMaxSnapDistanceMeters = 250.0d;

    @Builder.Default
    double minSnapDistance = 0.0d;

    @Builder.Default
    double maxSnapDistance = 1_000_000.0d;

    /**
     * Resolves effective max snap distance for one request.
     */
    public double resolveMaxSnapDistance(String coordinateStrategyId, Double requestOverride) {
        return resolveMaxSnapDistance(coordinateStrategyId, requestOverride, null);
    }

    /**
     * Resolves effective max snap distance for one request with optional strategy-level default.
     */
    public double resolveMaxSnapDistance(
            String coordinateStrategyId,
            Double requestOverride,
            CoordinateDistanceStrategy coordinateStrategy
    ) {
        double resolved = requestOverride == null
                ? defaultForStrategy(coordinateStrategyId, coordinateStrategy)
                : requestOverride;
        validateOverride(resolved);
        return resolved;
    }

    /**
     * Default policy instance.
     */
    public static AddressingPolicy defaults() {
        return AddressingPolicy.builder().build();
    }

    private double defaultForStrategy(String coordinateStrategyId, CoordinateDistanceStrategy coordinateStrategy) {
        if (coordinateStrategy != null) {
            Double strategyDefault = coordinateStrategy.defaultMaxSnapDistance();
            if (strategyDefault != null) {
                return strategyDefault;
            }
        }
        if (CoordinateStrategyRegistry.STRATEGY_LAT_LON.equals(coordinateStrategyId)) {
            return defaultLatLonMaxSnapDistanceMeters;
        }
        return defaultXyMaxSnapDistance;
    }

    private void validateOverride(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(REASON_INVALID_MAX_SNAP_DISTANCE + ": maxSnapDistance must be finite");
        }
        if (value < minSnapDistance) {
            throw new IllegalArgumentException(
                    REASON_INVALID_MAX_SNAP_DISTANCE + ": maxSnapDistance must be >= " + minSnapDistance
            );
        }
        if (value > maxSnapDistance) {
            throw new IllegalArgumentException(
                    REASON_INVALID_MAX_SNAP_DISTANCE + ": maxSnapDistance must be <= " + maxSnapDistance
            );
        }
    }
}
