package org.Aayush.routing.traits.addressing;

import org.Aayush.routing.geometry.GeometryDistance;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable coordinate-strategy registry.
 */
public final class CoordinateStrategyRegistry {
    public static final String STRATEGY_XY = "XY";
    public static final String STRATEGY_LAT_LON = "LAT_LON";

    static final String REASON_NON_FINITE_COORDINATES = "H15_NON_FINITE_COORDINATES";
    static final String REASON_LAT_LON_RANGE = "H15_LAT_LON_RANGE";

    private static final CoordinateDistanceStrategy XY_STRATEGY = new XYDistanceStrategy();
    private static final CoordinateDistanceStrategy LAT_LON_STRATEGY = new LatLonDistanceStrategy();

    private final Map<String, CoordinateDistanceStrategy> strategyById;

    /**
     * Creates a registry with built-in XY and LAT_LON strategies.
     */
    public CoordinateStrategyRegistry() {
        this(defaultStrategies());
    }

    /**
     * Creates a registry by merging built-ins with custom strategies.
     *
     * <p>Custom strategy ids override built-ins when ids collide.</p>
     */
    public CoordinateStrategyRegistry(Collection<? extends CoordinateDistanceStrategy> customStrategies) {
        LinkedHashMap<String, CoordinateDistanceStrategy> merged = new LinkedHashMap<>();
        for (CoordinateDistanceStrategy strategy : defaultStrategies()) {
            merged.put(strategy.id(), strategy);
        }
        if (customStrategies != null) {
            for (CoordinateDistanceStrategy strategy : customStrategies) {
                CoordinateDistanceStrategy nonNull = Objects.requireNonNull(strategy, "strategy");
                String id = normalizeId(nonNull.id());
                merged.put(id, nonNull);
            }
        }
        this.strategyById = Map.copyOf(merged);
    }

    /**
     * Resolves strategy by id (case-sensitive), or null when missing.
     */
    public CoordinateDistanceStrategy strategy(String strategyId) {
        if (strategyId == null) {
            return null;
        }
        return strategyById.get(strategyId);
    }

    /**
     * Returns immutable set of registered strategy ids.
     */
    public Set<String> strategyIds() {
        return strategyById.keySet();
    }

    /**
     * Returns default registry singleton shape.
     */
    public static CoordinateStrategyRegistry defaultRegistry() {
        return new CoordinateStrategyRegistry();
    }

    private static Collection<? extends CoordinateDistanceStrategy> defaultStrategies() {
        return java.util.List.of(XY_STRATEGY, LAT_LON_STRATEGY);
    }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("strategy id must be non-blank");
        }
        return normalized;
    }

    private static final class XYDistanceStrategy implements CoordinateDistanceStrategy {
        @Override
        public String id() {
            return STRATEGY_XY;
        }

        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException(
                        REASON_NON_FINITE_COORDINATES,
                        "XY coordinates must be finite"
                );
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            return GeometryDistance.euclideanDistance(requestFirst, requestSecond, nodeFirst, nodeSecond);
        }

        @Override
        public Double defaultMaxSnapDistance() {
            return 1_000.0d;
        }
    }

    private static final class LatLonDistanceStrategy implements CoordinateDistanceStrategy {
        private static final double MIN_LAT = -90.0d;
        private static final double MAX_LAT = 90.0d;
        private static final double MIN_LON = -180.0d;
        private static final double MAX_LON = 180.0d;

        @Override
        public String id() {
            return STRATEGY_LAT_LON;
        }

        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException(
                        REASON_NON_FINITE_COORDINATES,
                        "LAT_LON coordinates must be finite"
                );
            }
            if (first < MIN_LAT || first > MAX_LAT || second < MIN_LON || second > MAX_LON) {
                throw new CoordinateValidationException(
                        REASON_LAT_LON_RANGE,
                        "LAT_LON coordinates must be in [-90,90] and [-180,180]"
                );
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            return GeometryDistance.greatCircleDistanceMeters(requestFirst, requestSecond, nodeFirst, nodeSecond);
        }

        @Override
        public Double defaultMaxSnapDistance() {
            return 250.0d;
        }
    }
}
