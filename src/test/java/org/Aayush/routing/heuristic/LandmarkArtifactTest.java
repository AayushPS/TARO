package org.Aayush.routing.heuristic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LandmarkArtifact Contract Tests")
class LandmarkArtifactTest {

    @Test
    @DisplayName("Constructor rejects invalid nodeCount")
    void testInvalidNodeCountRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(0, new int[]{0}, new float[][]{{0.0f}}, new float[][]{{0.0f}})
        );
    }

    @Test
    @DisplayName("Constructor rejects duplicate and out-of-range landmark node ids")
    void testLandmarkNodeValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{1, 1}, new float[][]{{0, 1, 2}, {0, 1, 2}}, new float[][]{{0, 1, 2}, {0, 1, 2}})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{3}, new float[][]{{0, 1, 2}}, new float[][]{{0, 1, 2}})
        );
    }

    @Test
    @DisplayName("Constructor rejects malformed distance rows")
    void testDistanceRowValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{0}, new float[][]{{0, 1}}, new float[][]{{0, 1, 2}})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{0}, new float[][]{{0, Float.NaN, 2}}, new float[][]{{0, 1, 2}})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{0}, new float[][]{{0, -1, 2}}, new float[][]{{0, 1, 2}})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkArtifact(3, new int[]{0}, new float[][]{{0, Float.NEGATIVE_INFINITY, 2}}, new float[][]{{0, 1, 2}})
        );
    }

    @Test
    @DisplayName("Distance copy accessors are defensive and bounds-checked")
    void testDistanceCopyAccessors() {
        LandmarkArtifact artifact = new LandmarkArtifact(
                3,
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{2.0f, 1.0f, 0.0f}},
                42L
        );

        float[] forward = artifact.forwardDistancesCopy(0);
        float[] backward = artifact.backwardDistancesCopy(0);
        assertArrayEquals(new float[]{0.0f, 1.0f, 2.0f}, forward);
        assertArrayEquals(new float[]{2.0f, 1.0f, 0.0f}, backward);

        forward[1] = 999.0f;
        backward[1] = 999.0f;
        assertArrayEquals(new float[]{0.0f, 1.0f, 2.0f}, artifact.forwardDistancesCopy(0));
        assertArrayEquals(new float[]{2.0f, 1.0f, 0.0f}, artifact.backwardDistancesCopy(0));
        assertEquals(42L, artifact.getCompatibilitySignature());

        assertThrows(IllegalArgumentException.class, () -> artifact.forwardDistancesCopy(-1));
        assertThrows(IllegalArgumentException.class, () -> artifact.forwardDistancesCopy(1));
        assertThrows(IllegalArgumentException.class, () -> artifact.backwardDistancesCopy(-1));
        assertThrows(IllegalArgumentException.class, () -> artifact.backwardDistancesCopy(1));
    }
}
