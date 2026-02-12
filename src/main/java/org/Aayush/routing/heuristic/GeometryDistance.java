package org.Aayush.routing.heuristic;

import lombok.experimental.UtilityClass;

/**
 * Numeric helpers for Stage 11 geometry distance computations.
 */
@UtilityClass
final class GeometryDistance {
    private static final double EARTH_MEAN_RADIUS_METERS = 6_371_008.8d;

    /**
     * Computes Euclidean distance in projected/cartesian coordinate space.
     */
    static double euclideanDistance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    /**
     * Computes great-circle distance in meters using haversine formulation.
     */
    static double greatCircleDistanceMeters(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        double lat1Rad = Math.toRadians(lat1Deg);
        double lat2Rad = Math.toRadians(lat2Deg);
        double deltaLatRad = Math.toRadians(lat2Deg - lat1Deg);
        double deltaLonRad = Math.toRadians(normalizeDeltaLongitudeDegrees(lon2Deg - lon1Deg));

        double sinHalfLat = Math.sin(deltaLatRad * 0.5d);
        double sinHalfLon = Math.sin(deltaLonRad * 0.5d);

        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfLon * sinHalfLon;
        double clampedA = clamp(a, 0.0d, 1.0d);
        double c = 2.0d * Math.asin(Math.sqrt(clampedA));
        return EARTH_MEAN_RADIUS_METERS * c;
    }

    /**
     * Normalizes delta-longitude into the principal range {@code (-180, 180]}.
     */
    static double normalizeDeltaLongitudeDegrees(double deltaLonDeg) {
        double normalized = ((deltaLonDeg + 540.0d) % 360.0d) - 180.0d;
        if (normalized == -180.0d) {
            return 180.0d;
        }
        return normalized;
    }

    /**
     * Clamps a value into inclusive {@code [min, max]} bounds.
     */
    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
