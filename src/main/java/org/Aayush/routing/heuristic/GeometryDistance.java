package org.Aayush.routing.heuristic;

import lombok.experimental.UtilityClass;

/**
 * Backward-compatible package-local bridge to shared geometry helpers.
 */
@UtilityClass
final class GeometryDistance {
    /**
     * Computes Euclidean distance in projected/cartesian coordinate space.
     */
    static double euclideanDistance(double x1, double y1, double x2, double y2) {
        return org.Aayush.routing.geometry.GeometryDistance.euclideanDistance(x1, y1, x2, y2);
    }

    /**
     * Computes great-circle distance in meters using haversine formulation.
     */
    static double greatCircleDistanceMeters(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        return org.Aayush.routing.geometry.GeometryDistance.greatCircleDistanceMeters(
                lat1Deg,
                lon1Deg,
                lat2Deg,
                lon2Deg
        );
    }

    /**
     * Normalizes delta-longitude into the principal range {@code (-180, 180]}.
     */
    static double normalizeDeltaLongitudeDegrees(double deltaLonDeg) {
        return org.Aayush.routing.geometry.GeometryDistance.normalizeDeltaLongitudeDegrees(deltaLonDeg);
    }
}
