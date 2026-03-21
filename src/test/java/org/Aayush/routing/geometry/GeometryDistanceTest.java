package org.Aayush.routing.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Geometry Distance Tests")
class GeometryDistanceTest {

    @Test
    @DisplayName("Euclidean distance matches simple projected geometry")
    void testEuclideanDistance() {
        assertEquals(5.0d, GeometryDistance.euclideanDistance(0.0d, 0.0d, 3.0d, 4.0d), 1.0e-9d);
    }

    @Test
    @DisplayName("Longitude normalization keeps values in principal range and preserves the -180 special case")
    void testLongitudeNormalization() {
        assertEquals(180.0d, GeometryDistance.normalizeDeltaLongitudeDegrees(-180.0d), 1.0e-9d);
        assertEquals(-179.0d, GeometryDistance.normalizeDeltaLongitudeDegrees(181.0d), 1.0e-9d);
        assertEquals(179.0d, GeometryDistance.normalizeDeltaLongitudeDegrees(-181.0d), 1.0e-9d);
    }

    @Test
    @DisplayName("Great-circle distance handles antimeridian crossings symmetrically")
    void testGreatCircleDistanceAcrossAntimeridian() {
        double eastbound = GeometryDistance.greatCircleDistanceMeters(0.0d, 179.0d, 0.0d, -179.0d);
        double westbound = GeometryDistance.greatCircleDistanceMeters(0.0d, -179.0d, 0.0d, 179.0d);

        assertTrue(eastbound > 200_000.0d);
        assertEquals(eastbound, westbound, 1.0e-6d);
    }
}
