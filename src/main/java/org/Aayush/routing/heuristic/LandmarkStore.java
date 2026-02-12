package org.Aayush.routing.heuristic;

import lombok.Getter;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.serialization.flatbuffers.taro.model.Landmark;
import org.Aayush.serialization.flatbuffers.taro.model.Model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable runtime container for Stage 12 landmark arrays.
 */
@Getter
public final class LandmarkStore {
    public static final long UNKNOWN_COMPATIBILITY_SIGNATURE = Long.MIN_VALUE;

    private final int nodeCount;
    private final int[] landmarkNodeIds;
    private final float[][] forwardDistances;
    private final float[][] backwardDistances;
    private final long compatibilitySignature;

    /**
     * Creates an immutable landmark store without compatibility signature metadata.
     */
    public LandmarkStore(
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
                UNKNOWN_COMPATIBILITY_SIGNATURE
        );
    }

    private LandmarkStore(
            int nodeCount,
            int[] landmarkNodeIds,
            float[][] forwardDistances,
            float[][] backwardDistances,
            long compatibilitySignature
    ) {
        LandmarkArtifact artifact = new LandmarkArtifact(
                nodeCount,
                landmarkNodeIds,
                forwardDistances,
                backwardDistances,
                compatibilitySignature
        );
        this.nodeCount = artifact.getNodeCount();
        this.landmarkNodeIds = artifact.landmarkNodeIdsCopy();
        this.forwardDistances = copyRows(artifact, true);
        this.backwardDistances = copyRows(artifact, false);
        this.compatibilitySignature = artifact.getCompatibilitySignature();
    }

    /**
     * Creates a store from a validated landmark artifact.
     */
    public static LandmarkStore fromArtifact(LandmarkArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return new LandmarkStore(
                artifact.getNodeCount(),
                artifact.landmarkNodeIdsCopy(),
                copyRows(artifact, true),
                copyRows(artifact, false),
                artifact.getCompatibilitySignature()
        );
    }

    /**
     * Loads landmarks from a TARO FlatBuffer and computes compatibility signature.
     */
    public static LandmarkStore fromFlatBuffer(ByteBuffer buffer, int expectedNodeCount) {
        Objects.requireNonNull(buffer, "buffer");
        ByteBuffer bb = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (!Model.ModelBufferHasIdentifier(bb)) {
            throw new IllegalArgumentException("invalid TARO identifier while loading landmarks");
        }
        Model model = Model.getRootAsModel(bb);
        long compatibilitySignature = computeCompatibilitySignature(bb);
        return fromModel(model, expectedNodeCount, compatibilitySignature);
    }

    /**
     * Loads landmarks from a parsed model without compatibility signature metadata.
     */
    public static LandmarkStore fromModel(Model model, int expectedNodeCount) {
        return fromModel(model, expectedNodeCount, UNKNOWN_COMPATIBILITY_SIGNATURE);
    }

    /**
     * Internal model loader with explicit compatibility signature.
     */
    private static LandmarkStore fromModel(Model model, int expectedNodeCount, long compatibilitySignature) {
        Objects.requireNonNull(model, "model");
        if (expectedNodeCount <= 0) {
            throw new IllegalArgumentException("expectedNodeCount must be > 0");
        }
        int landmarkCount = model.landmarksLength();
        if (landmarkCount <= 0) {
            throw new IllegalArgumentException("model landmarks vector is empty");
        }

        int[] nodeIds = new int[landmarkCount];
        float[][] forward = new float[landmarkCount][expectedNodeCount];
        float[][] backward = new float[landmarkCount][expectedNodeCount];

        for (int i = 0; i < landmarkCount; i++) {
            Landmark landmark = model.landmarks(i);
            if (landmark == null) {
                throw new IllegalArgumentException("landmarks[" + i + "] is null");
            }
            int nodeId = landmark.nodeIdx();
            nodeIds[i] = nodeId;

            if (landmark.forwardDistancesLength() != expectedNodeCount) {
                throw new IllegalArgumentException(
                        "landmarks[" + i + "].forwardDistances length mismatch: "
                                + landmark.forwardDistancesLength() + " != " + expectedNodeCount
                );
            }
            if (landmark.backwardDistancesLength() != expectedNodeCount) {
                throw new IllegalArgumentException(
                        "landmarks[" + i + "].backwardDistances length mismatch: "
                                + landmark.backwardDistancesLength() + " != " + expectedNodeCount
                );
            }

            for (int node = 0; node < expectedNodeCount; node++) {
                float f = landmark.forwardDistances(node);
                float b = landmark.backwardDistances(node);
                validateDistanceValue(f, "landmarks[" + i + "].forwardDistances[" + node + "]");
                validateDistanceValue(b, "landmarks[" + i + "].backwardDistances[" + node + "]");
                forward[i][node] = f;
                backward[i][node] = b;
            }
        }

        return new LandmarkStore(expectedNodeCount, nodeIds, forward, backward, compatibilitySignature);
    }

    /**
     * Returns the number of configured landmarks.
     */
    public int landmarkCount() {
        return landmarkNodeIds.length;
    }

    /**
     * Returns forward landmark distance for one landmark/node pair.
     */
    public float forwardDistance(int landmarkIndex, int nodeId) {
        validateIndices(landmarkIndex, nodeId);
        return forwardDistances[landmarkIndex][nodeId];
    }

    /**
     * Returns backward landmark distance for one landmark/node pair.
     */
    public float backwardDistance(int landmarkIndex, int nodeId) {
        validateIndices(landmarkIndex, nodeId);
        return backwardDistances[landmarkIndex][nodeId];
    }

    /**
     * Returns ALT lower bound from {@code fromNodeId} to {@code goalNodeId}.
     */
    public double lowerBound(int fromNodeId, int goalNodeId) {
        validateNodeId(fromNodeId);
        validateNodeId(goalNodeId);
        double best = 0.0d;
        for (int i = 0; i < landmarkNodeIds.length; i++) {
            float fGoal = forwardDistances[i][goalNodeId];
            float fFrom = forwardDistances[i][fromNodeId];
            float bFrom = backwardDistances[i][fromNodeId];
            float bGoal = backwardDistances[i][goalNodeId];

            double c1 = differenceIfFinite(fGoal, fFrom);
            double c2 = differenceIfFinite(bFrom, bGoal);
            double candidate = Math.max(Math.max(c1, c2), 0.0d);
            if (candidate > best) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Returns a defensive copy of landmark node ids.
     */
    public int[] landmarkNodeIdsCopy() {
        return Arrays.copyOf(landmarkNodeIds, landmarkNodeIds.length);
    }

    /**
     * Returns whether this store carries a deterministic compatibility signature.
     */
    public boolean hasCompatibilitySignature() {
        return compatibilitySignature != UNKNOWN_COMPATIBILITY_SIGNATURE;
    }

    /**
     * Computes signature from graph/profile contracts embedded in a model buffer.
     */
    private static long computeCompatibilitySignature(ByteBuffer buffer) {
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        return LandmarkCompatibility.computeSignature(edgeGraph, profileStore);
    }

    /**
     * Deep-copies all landmark rows from an artifact.
     */
    private static float[][] copyRows(LandmarkArtifact artifact, boolean forward) {
        int count = artifact.landmarkCount();
        float[][] rows = new float[count][];
        for (int i = 0; i < count; i++) {
            rows[i] = forward ? artifact.forwardDistancesCopy(i) : artifact.backwardDistancesCopy(i);
        }
        return rows;
    }

    /**
     * Deep-copies a 2D distance matrix.
     */
    private static float[][] copyRows(float[][] source) {
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    /**
     * Validates one encoded distance value against landmark store constraints.
     */
    private static void validateDistanceValue(float value, String field) {
        if (Float.isNaN(value) || value < 0.0f || value == Float.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException(field + " must be >= 0, +INF, and non-NaN");
        }
    }

    /**
     * Returns {@code max(0, a-b)} when both values are finite, otherwise {@code 0}.
     */
    private double differenceIfFinite(float a, float b) {
        if (!Float.isFinite(a) || !Float.isFinite(b)) {
            return 0.0d;
        }
        return Math.max(0.0d, (double) a - b);
    }

    /**
     * Validates landmark and node indexes before matrix access.
     */
    private void validateIndices(int landmarkIndex, int nodeId) {
        if (landmarkIndex < 0 || landmarkIndex >= landmarkNodeIds.length) {
            throw new IllegalArgumentException("landmarkIndex out of bounds: " + landmarkIndex);
        }
        validateNodeId(nodeId);
    }

    /**
     * Validates node id against this store's node-count contract.
     */
    private void validateNodeId(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IllegalArgumentException("nodeId out of bounds: " + nodeId + " [0," + nodeCount + ")");
        }
    }
}
