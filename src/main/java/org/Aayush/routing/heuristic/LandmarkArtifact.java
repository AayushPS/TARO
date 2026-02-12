package org.Aayush.routing.heuristic;

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable Stage 12 landmark preprocessing output.
 */
@Getter
public final class LandmarkArtifact {
    private final int nodeCount;
    private final int[] landmarkNodeIds;
    private final float[][] forwardDistances;
    private final float[][] backwardDistances;
    private final long compatibilitySignature;

    public LandmarkArtifact(
            int nodeCount,
            int[] landmarkNodeIds,
            float[][] forwardDistances,
            float[][] backwardDistances
    ) {
        this(
                nodeCount,
                landmarkNodeIds,
                forwardDistances,
                backwardDistances,
                LandmarkStore.UNKNOWN_COMPATIBILITY_SIGNATURE
        );
    }

    public LandmarkArtifact(
            int nodeCount,
            int[] landmarkNodeIds,
            float[][] forwardDistances,
            float[][] backwardDistances,
            long compatibilitySignature
    ) {
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("nodeCount must be > 0");
        }
        this.nodeCount = nodeCount;
        this.landmarkNodeIds = copyAndValidateLandmarkNodes(landmarkNodeIds, nodeCount);
        this.forwardDistances = copyAndValidateDistances(forwardDistances, nodeCount, "forwardDistances");
        this.backwardDistances = copyAndValidateDistances(backwardDistances, nodeCount, "backwardDistances");
        this.compatibilitySignature = compatibilitySignature;
        if (this.landmarkNodeIds.length != this.forwardDistances.length
                || this.landmarkNodeIds.length != this.backwardDistances.length) {
            throw new IllegalArgumentException(
                    "landmark count mismatch: nodeIds=" + this.landmarkNodeIds.length
                            + ", forward=" + this.forwardDistances.length
                            + ", backward=" + this.backwardDistances.length
            );
        }
    }

    /**
     * Returns number of landmark rows in this artifact.
     */
    public int landmarkCount() {
        return landmarkNodeIds.length;
    }

    /**
     * Returns a defensive copy of landmark node ids.
     */
    public int[] landmarkNodeIdsCopy() {
        return Arrays.copyOf(landmarkNodeIds, landmarkNodeIds.length);
    }

    /**
     * Returns a defensive copy of one forward-distance row.
     */
    public float[] forwardDistancesCopy(int landmarkIndex) {
        if (landmarkIndex < 0 || landmarkIndex >= forwardDistances.length) {
            throw new IllegalArgumentException("landmarkIndex out of bounds: " + landmarkIndex);
        }
        return Arrays.copyOf(forwardDistances[landmarkIndex], nodeCount);
    }

    /**
     * Returns a defensive copy of one backward-distance row.
     */
    public float[] backwardDistancesCopy(int landmarkIndex) {
        if (landmarkIndex < 0 || landmarkIndex >= backwardDistances.length) {
            throw new IllegalArgumentException("landmarkIndex out of bounds: " + landmarkIndex);
        }
        return Arrays.copyOf(backwardDistances[landmarkIndex], nodeCount);
    }

    /**
     * Copies and validates landmark node ids for uniqueness and bounds.
     */
    static int[] copyAndValidateLandmarkNodes(int[] nodeIds, int nodeCount) {
        Objects.requireNonNull(nodeIds, "landmarkNodeIds");
        if (nodeIds.length == 0) {
            throw new IllegalArgumentException("at least one landmark is required");
        }
        int[] copy = Arrays.copyOf(nodeIds, nodeIds.length);
        boolean[] seen = new boolean[nodeCount];
        for (int i = 0; i < copy.length; i++) {
            int nodeId = copy[i];
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "landmarkNodeIds[" + i + "] out of bounds: " + nodeId + " [0," + nodeCount + ")"
                );
            }
            if (seen[nodeId]) {
                throw new IllegalArgumentException("duplicate landmark node id: " + nodeId);
            }
            seen[nodeId] = true;
        }
        return copy;
    }

    /**
     * Copies and validates a distance matrix for row width and value constraints.
     */
    static float[][] copyAndValidateDistances(float[][] distances, int nodeCount, String fieldName) {
        Objects.requireNonNull(distances, fieldName);
        float[][] copy = new float[distances.length][];
        for (int i = 0; i < distances.length; i++) {
            float[] row = Objects.requireNonNull(distances[i], fieldName + "[" + i + "]");
            if (row.length != nodeCount) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] length mismatch: " + row.length + " != " + nodeCount
                );
            }
            copy[i] = Arrays.copyOf(row, row.length);
            validateDistanceRow(copy[i], fieldName, i);
        }
        return copy;
    }

    /**
     * Validates one distance row for non-negative, non-NaN values.
     */
    private static void validateDistanceRow(float[] row, String fieldName, int rowIndex) {
        for (int node = 0; node < row.length; node++) {
            float value = row[node];
            if (Float.isNaN(value) || value < 0.0f || value == Float.NEGATIVE_INFINITY) {
                throw new IllegalArgumentException(
                        fieldName + "[" + rowIndex + "][" + node + "] must be >= 0, +INF, and non-NaN"
                );
            }
        }
    }
}
