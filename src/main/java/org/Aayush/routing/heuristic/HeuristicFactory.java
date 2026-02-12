package org.Aayush.routing.heuristic;

import lombok.experimental.UtilityClass;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

/**
 * Strict Stage 11 heuristic factory.
 *
 * <p>Centralizes validation so all heuristic providers are created with the same
 * graph/profile/cost contracts and deterministic failure reason codes.</p>
 */
@UtilityClass
public final class HeuristicFactory {
    public static final String REASON_TYPE_REQUIRED = "H11_TYPE_REQUIRED";
    public static final String REASON_GRAPH_REQUIRED = "H11_EDGE_GRAPH_REQUIRED";
    public static final String REASON_PROFILE_REQUIRED = "H11_PROFILE_STORE_REQUIRED";
    public static final String REASON_COST_REQUIRED = "H11_COST_ENGINE_REQUIRED";
    public static final String REASON_LANDMARK_STORE_REQUIRED = "H12_LANDMARK_STORE_REQUIRED";
    public static final String REASON_LANDMARK_NODE_COUNT_MISMATCH = "H12_LANDMARK_NODE_COUNT_MISMATCH";
    public static final String REASON_LANDMARK_EMPTY = "H12_LANDMARK_EMPTY";
    public static final String REASON_LANDMARK_SIGNATURE_REQUIRED = "H12_LANDMARK_SIGNATURE_REQUIRED";
    public static final String REASON_LANDMARK_SIGNATURE_MISMATCH = "H12_LANDMARK_SIGNATURE_MISMATCH";
    public static final String REASON_COORDINATES_REQUIRED = "H11_COORDINATES_REQUIRED";
    public static final String REASON_EUCLIDEAN_X_NON_FINITE = "H11_EUCLIDEAN_X_NON_FINITE";
    public static final String REASON_EUCLIDEAN_Y_NON_FINITE = "H11_EUCLIDEAN_Y_NON_FINITE";
    public static final String REASON_SPHERICAL_LAT_RANGE = "H11_SPHERICAL_LAT_RANGE";
    public static final String REASON_SPHERICAL_LON_RANGE = "H11_SPHERICAL_LON_RANGE";

    private static final double MIN_LAT = -90.0d;
    private static final double MAX_LAT = 90.0d;
    private static final double MIN_LON = -180.0d;
    private static final double MAX_LON = 180.0d;

    /**
     * Creates a heuristic provider without landmark backing store.
     *
     * @param type requested heuristic type.
     * @param edgeGraph graph runtime.
     * @param profileStore profile runtime.
     * @param costEngine cost runtime.
     * @return initialized heuristic provider.
     */
    public static HeuristicProvider create(
            HeuristicType type,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine
    ) {
        return create(type, edgeGraph, profileStore, costEngine, null);
    }

    /**
     * Creates a heuristic provider with optional landmark backing store.
     *
     * @param type requested heuristic type.
     * @param edgeGraph graph runtime.
     * @param profileStore profile runtime.
     * @param costEngine cost runtime.
     * @param landmarkStore landmark runtime (required for {@link HeuristicType#LANDMARK}).
     * @return initialized heuristic provider.
     */
    public static HeuristicProvider create(
            HeuristicType type,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            LandmarkStore landmarkStore
    ) {
        if (type == null) {
            throw new HeuristicConfigurationException(
                    REASON_TYPE_REQUIRED,
                    "heuristic type must be explicitly specified (NONE, EUCLIDEAN, SPHERICAL, LANDMARK)"
            );
        }
        if (edgeGraph == null) {
            throw new HeuristicConfigurationException(
                    REASON_GRAPH_REQUIRED,
                    "edgeGraph must be provided"
            );
        }
        if (profileStore == null) {
            throw new HeuristicConfigurationException(
                    REASON_PROFILE_REQUIRED,
                    "profileStore must be provided"
            );
        }
        if (costEngine == null) {
            throw new HeuristicConfigurationException(
                    REASON_COST_REQUIRED,
                    "costEngine must be provided"
            );
        }

        return switch (type) {
            case NONE -> new NullHeuristicProvider(edgeGraph);
            case EUCLIDEAN -> createEuclidean(edgeGraph, profileStore, costEngine);
            case SPHERICAL -> createSpherical(edgeGraph, profileStore, costEngine);
            case LANDMARK -> createLandmark(edgeGraph, profileStore, landmarkStore);
        };
    }

    private static HeuristicProvider createEuclidean(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine
    ) {
        ensureCoordinatesAvailable(edgeGraph, HeuristicType.EUCLIDEAN);
        ensureEuclideanCoordinatesFinite(edgeGraph);
        GeometryLowerBoundModel lowerBoundModel = GeometryLowerBoundModel.calibrateEuclidean(
                edgeGraph,
                profileStore,
                costEngine
        );
        return new EuclideanHeuristicProvider(edgeGraph, lowerBoundModel);
    }

    private static HeuristicProvider createSpherical(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine
    ) {
        ensureCoordinatesAvailable(edgeGraph, HeuristicType.SPHERICAL);
        ensureGeodeticCoordinateRanges(edgeGraph);
        GeometryLowerBoundModel lowerBoundModel = GeometryLowerBoundModel.calibrateSpherical(
                edgeGraph,
                profileStore,
                costEngine
        );
        return new SphericalHeuristicProvider(edgeGraph, lowerBoundModel);
    }

    /**
     * Ensures coordinate vectors are available for geometry-based heuristics.
     */
    private static void ensureCoordinatesAvailable(EdgeGraph edgeGraph, HeuristicType type) {
        if (!edgeGraph.hasCoordinates()) {
            throw new HeuristicConfigurationException(
                    REASON_COORDINATES_REQUIRED,
                    type + " heuristic requires graph coordinates"
            );
        }
    }

    /**
     * Ensures coordinates are valid latitude/longitude values for spherical metric.
     */
    private static void ensureGeodeticCoordinateRanges(EdgeGraph edgeGraph) {
        for (int nodeId = 0; nodeId < edgeGraph.nodeCount(); nodeId++) {
            double lat = edgeGraph.getNodeX(nodeId);
            double lon = edgeGraph.getNodeY(nodeId);

            if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
                throw new HeuristicConfigurationException(
                        REASON_SPHERICAL_LAT_RANGE,
                        "node " + nodeId + " latitude must be finite and in [-90,90], got " + lat
                );
            }
            if (!Double.isFinite(lon) || lon < MIN_LON || lon > MAX_LON) {
                throw new HeuristicConfigurationException(
                        REASON_SPHERICAL_LON_RANGE,
                        "node " + nodeId + " longitude must be finite and in [-180,180], got " + lon
                );
            }
        }
    }

    /**
     * Ensures coordinates are finite for Euclidean metric.
     */
    private static void ensureEuclideanCoordinatesFinite(EdgeGraph edgeGraph) {
        for (int nodeId = 0; nodeId < edgeGraph.nodeCount(); nodeId++) {
            double x = edgeGraph.getNodeX(nodeId);
            double y = edgeGraph.getNodeY(nodeId);

            if (!Double.isFinite(x)) {
                throw new HeuristicConfigurationException(
                        REASON_EUCLIDEAN_X_NON_FINITE,
                        "node " + nodeId + " x must be finite for EUCLIDEAN heuristic, got " + x
                );
            }
            if (!Double.isFinite(y)) {
                throw new HeuristicConfigurationException(
                        REASON_EUCLIDEAN_Y_NON_FINITE,
                        "node " + nodeId + " y must be finite for EUCLIDEAN heuristic, got " + y
                );
            }
        }
    }

    private static HeuristicProvider createLandmark(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            LandmarkStore landmarkStore
    ) {
        if (landmarkStore == null) {
            throw new HeuristicConfigurationException(
                    REASON_LANDMARK_STORE_REQUIRED,
                    "LANDMARK heuristic requires precomputed LandmarkStore"
            );
        }
        if (landmarkStore.getNodeCount() != edgeGraph.nodeCount()) {
            throw new HeuristicConfigurationException(
                    REASON_LANDMARK_NODE_COUNT_MISMATCH,
                    "landmark nodeCount " + landmarkStore.getNodeCount()
                            + " does not match graph nodeCount " + edgeGraph.nodeCount()
            );
        }
        if (landmarkStore.landmarkCount() <= 0) {
            throw new HeuristicConfigurationException(
                    REASON_LANDMARK_EMPTY,
                    "landmark store must contain at least one landmark"
            );
        }
        if (!landmarkStore.hasCompatibilitySignature()) {
            throw new HeuristicConfigurationException(
                    REASON_LANDMARK_SIGNATURE_REQUIRED,
                    "landmark store is missing compatibility signature; load it from LandmarkPreprocessor/LandmarkStore.fromFlatBuffer"
            );
        }
        long expectedSignature = LandmarkCompatibility.computeSignature(edgeGraph, profileStore);
        long providedSignature = landmarkStore.getCompatibilitySignature();
        if (providedSignature != expectedSignature) {
            throw new HeuristicConfigurationException(
                    REASON_LANDMARK_SIGNATURE_MISMATCH,
                    "landmark store signature mismatch: expected 0x"
                            + Long.toUnsignedString(expectedSignature, 16)
                            + ", got 0x"
                            + Long.toUnsignedString(providedSignature, 16)
            );
        }
        return new LandmarkHeuristicProvider(edgeGraph, landmarkStore);
    }
}
