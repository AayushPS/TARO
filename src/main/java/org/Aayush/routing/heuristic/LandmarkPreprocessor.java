package org.Aayush.routing.heuristic;

import lombok.experimental.UtilityClass;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

import java.util.Arrays;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/**
 * Stage 12 deterministic ALT landmark preprocessing.
 *
 * <p>Builds landmark node selection and forward/backward lower-bound distance
 * tables that back {@link LandmarkHeuristicProvider}.</p>
 */
@UtilityClass
public final class LandmarkPreprocessor {
    private static final float INF = Float.POSITIVE_INFINITY;
    private static final int DAYS_PER_WEEK = 7;

    /**
     * Builds landmark artifact arrays from graph/profile runtime contracts.
     *
     * @param edgeGraph graph runtime.
     * @param profileStore profile runtime.
     * @param config preprocessing parameters.
     * @return immutable landmark artifact.
     */
    public static LandmarkArtifact preprocess(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            LandmarkPreprocessorConfig config
    ) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(profileStore, "profileStore");
        Objects.requireNonNull(config, "config");
        validateConfig(config);

        int nodeCount = edgeGraph.nodeCount();
        int edgeCount = edgeGraph.edgeCount();
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("edgeGraph must contain at least one node");
        }

        int effectiveLandmarkCount = Math.min(config.getLandmarkCount(), nodeCount);
        int[] landmarkNodeIds = selectLandmarkNodes(edgeGraph, effectiveLandmarkCount, config.getSelectionSeed());

        float[] lowerBoundEdgeWeights = computeLowerBoundEdgeWeights(edgeGraph, profileStore);
        long compatibilitySignature = LandmarkCompatibility.computeSignature(edgeGraph, profileStore);
        IncomingIndex incomingIndex = buildIncomingIndex(edgeGraph);

        float[][] forward = new float[landmarkNodeIds.length][nodeCount];
        float[][] backward = new float[landmarkNodeIds.length][nodeCount];
        for (int i = 0; i < landmarkNodeIds.length; i++) {
            int landmarkNode = landmarkNodeIds[i];
            forward[i] = dijkstraForward(edgeGraph, lowerBoundEdgeWeights, landmarkNode, config.getMaxSettledNodesPerLandmark());
            backward[i] = dijkstraBackward(edgeGraph, lowerBoundEdgeWeights, incomingIndex, landmarkNode, config.getMaxSettledNodesPerLandmark());
        }
        return new LandmarkArtifact(nodeCount, landmarkNodeIds, forward, backward, compatibilitySignature);
    }

    /**
     * Validates preprocessor configuration ranges.
     */
    private static void validateConfig(LandmarkPreprocessorConfig config) {
        if (config.getLandmarkCount() <= 0) {
            throw new IllegalArgumentException("landmarkCount must be > 0");
        }
        if (config.getMaxSettledNodesPerLandmark() <= 0) {
            throw new IllegalArgumentException("maxSettledNodesPerLandmark must be > 0");
        }
    }

    /**
     * Selects deterministic landmark seed nodes with preference for non-terminal nodes.
     */
    private static int[] selectLandmarkNodes(EdgeGraph edgeGraph, int count, long seed) {
        int nodeCount = edgeGraph.nodeCount();
        int[] order = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            order[i] = i;
        }
        shuffle(order, seed);

        int[] selected = new int[count];
        boolean[] used = new boolean[nodeCount];
        int selectedCount = 0;

        for (int nodeId : order) {
            if (selectedCount >= count) {
                break;
            }
            if (edgeGraph.getNodeDegree(nodeId) <= 0) {
                continue;
            }
            selected[selectedCount++] = nodeId;
            used[nodeId] = true;
        }
        for (int nodeId : order) {
            if (selectedCount >= count) {
                break;
            }
            if (used[nodeId]) {
                continue;
            }
            selected[selectedCount++] = nodeId;
            used[nodeId] = true;
        }
        return selected;
    }

    /**
     * In-place deterministic Fisher-Yates shuffle.
     */
    private static void shuffle(int[] array, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    /**
     * Computes per-edge lower-bound traversal weights for preprocessing Dijkstra runs.
     */
    private static float[] computeLowerBoundEdgeWeights(EdgeGraph edgeGraph, ProfileStore profileStore) {
        int edgeCount = edgeGraph.edgeCount();
        float[] lowerBounds = new float[edgeCount];
        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            float baseWeight = edgeGraph.getBaseWeight(edgeId);
            if (!Float.isFinite(baseWeight) || baseWeight < 0.0f) {
                throw new IllegalArgumentException("baseWeight must be finite and >= 0 for edge " + edgeId);
            }

            int profileId = edgeGraph.getProfileId(edgeId);
            float minMultiplier = minimumTemporalMultiplier(profileStore, profileId);
            if (!Float.isFinite(minMultiplier) || minMultiplier <= 0.0f) {
                throw new IllegalArgumentException(
                        "profile minMultiplier must be finite and > 0 for profile " + profileId
                );
            }

            double lower = (double) baseWeight * minMultiplier;
            if (!Double.isFinite(lower) || lower < 0.0d) {
                throw new IllegalArgumentException("invalid lower-bound edge weight for edge " + edgeId);
            }
            lowerBounds[edgeId] = lower >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) lower;
        }
        return lowerBounds;
    }

    /**
     * Returns minimum temporal multiplier over all days for a profile id.
     */
    private static float minimumTemporalMultiplier(ProfileStore profileStore, int profileId) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            int selectedProfileId = profileStore.selectProfileForDay(profileId, day);
            float dayMinimum = selectedProfileId == ProfileStore.DEFAULT_PROFILE_ID
                    ? ProfileStore.DEFAULT_MULTIPLIER
                    : profileStore.getMetadata(selectedProfileId).minMultiplier();
            if (dayMinimum < minimum) {
                minimum = dayMinimum;
            }
        }
        return minimum;
    }

    private static float[] dijkstraForward(
            EdgeGraph edgeGraph,
            float[] lowerBoundEdgeWeights,
            int sourceNode,
            int maxSettledNodes
    ) {
        float[] distances = new float[edgeGraph.nodeCount()];
        Arrays.fill(distances, INF);
        distances[sourceNode] = 0.0f;

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>();
        queue.add(new NodeDistance(sourceNode, 0.0f));

        EdgeGraph.EdgeIterator iterator = edgeGraph.iterator();
        int settled = 0;
        while (!queue.isEmpty()) {
            NodeDistance state = queue.poll();
            int nodeId = state.nodeId;
            if (state.distance > distances[nodeId]) {
                continue;
            }
            settled++;
            if (settled >= maxSettledNodes) {
                break;
            }

            iterator.resetForNode(nodeId);
            while (iterator.hasNext()) {
                int edgeId = iterator.next();
                int nextNode = edgeGraph.getEdgeDestination(edgeId);
                float weight = lowerBoundEdgeWeights[edgeId];
                float nextDistance = state.distance + weight;
                if (nextDistance < distances[nextNode]) {
                    distances[nextNode] = nextDistance;
                    queue.add(new NodeDistance(nextNode, nextDistance));
                }
            }
        }
        return distances;
    }

    private static float[] dijkstraBackward(
            EdgeGraph edgeGraph,
            float[] lowerBoundEdgeWeights,
            IncomingIndex incomingIndex,
            int landmarkNode,
            int maxSettledNodes
    ) {
        float[] distances = new float[edgeGraph.nodeCount()];
        Arrays.fill(distances, INF);
        distances[landmarkNode] = 0.0f;

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>();
        queue.add(new NodeDistance(landmarkNode, 0.0f));

        int settled = 0;
        while (!queue.isEmpty()) {
            NodeDistance state = queue.poll();
            int nodeId = state.nodeId;
            if (state.distance > distances[nodeId]) {
                continue;
            }
            settled++;
            if (settled >= maxSettledNodes) {
                break;
            }

            int start = incomingIndex.offsets[nodeId];
            int end = incomingIndex.offsets[nodeId + 1];
            for (int pos = start; pos < end; pos++) {
                int edgeId = incomingIndex.incomingEdgeIds[pos];
                int predecessorNode = edgeGraph.getEdgeOrigin(edgeId);
                float weight = lowerBoundEdgeWeights[edgeId];
                float nextDistance = state.distance + weight;
                if (nextDistance < distances[predecessorNode]) {
                    distances[predecessorNode] = nextDistance;
                    queue.add(new NodeDistance(predecessorNode, nextDistance));
                }
            }
        }
        return distances;
    }

    /**
     * Builds reverse adjacency index for backward Dijkstra execution.
     */
    private static IncomingIndex buildIncomingIndex(EdgeGraph edgeGraph) {
        int nodeCount = edgeGraph.nodeCount();
        int edgeCount = edgeGraph.edgeCount();
        int[] counts = new int[nodeCount];
        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            int destination = edgeGraph.getEdgeDestination(edgeId);
            counts[destination]++;
        }

        int[] offsets = new int[nodeCount + 1];
        for (int i = 0; i < nodeCount; i++) {
            offsets[i + 1] = offsets[i] + counts[i];
        }

        int[] writeCursor = Arrays.copyOf(offsets, offsets.length);
        int[] incomingEdgeIds = new int[edgeCount];
        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            int destination = edgeGraph.getEdgeDestination(edgeId);
            int insertAt = writeCursor[destination]++;
            incomingEdgeIds[insertAt] = edgeId;
        }
        return new IncomingIndex(offsets, incomingEdgeIds);
    }

    private record IncomingIndex(int[] offsets, int[] incomingEdgeIds) {
    }

    private static final class NodeDistance implements Comparable<NodeDistance> {
        private final int nodeId;
        private final float distance;

        /**
         * Creates a queue state for Dijkstra frontier ordering.
         */
        private NodeDistance(int nodeId, float distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        /**
         * Orders states by distance first, then node id for deterministic ties.
         */
        @Override
        public int compareTo(NodeDistance other) {
            int byDistance = Float.compare(this.distance, other.distance);
            if (byDistance != 0) {
                return byDistance;
            }
            return Integer.compare(this.nodeId, other.nodeId);
        }
    }
}
