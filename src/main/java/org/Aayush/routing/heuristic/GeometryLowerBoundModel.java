package org.Aayush.routing.heuristic;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

import java.util.Objects;

/**
 * Admissibility calibration model for geometry heuristics.
 *
 * <p>Computes a deterministic lower-bound scale:
 * cost_per_distance = min over edges of
 * (base_weight * min_temporal_multiplier * live_penalty_lower_bound + turn_lower_bound) / geometry_distance.</p>
 */
public final class GeometryLowerBoundModel {
    public static final String REASON_GRAPH_REQUIRED = "H11_LB_GRAPH_REQUIRED";
    public static final String REASON_PROFILE_REQUIRED = "H11_LB_PROFILE_STORE_REQUIRED";
    public static final String REASON_COST_REQUIRED = "H11_LB_COST_ENGINE_REQUIRED";
    public static final String REASON_COST_GRAPH_MISMATCH = "H11_LB_COST_GRAPH_MISMATCH";
    public static final String REASON_COST_PROFILE_MISMATCH = "H11_LB_COST_PROFILE_MISMATCH";
    public static final String REASON_COORDINATES_REQUIRED = "H11_LB_COORDINATES_REQUIRED";
    public static final String REASON_EMPTY_GRAPH = "H11_LB_EMPTY_GRAPH";
    public static final String REASON_INVALID_BASE_WEIGHT = "H11_LB_INVALID_BASE_WEIGHT";
    public static final String REASON_INVALID_TEMPORAL_MIN = "H11_LB_INVALID_TEMPORAL_MIN";
    public static final String REASON_INVALID_EDGE_DISTANCE = "H11_LB_INVALID_EDGE_DISTANCE";
    public static final String REASON_INVALID_EDGE_RATIO = "H11_LB_INVALID_EDGE_RATIO";
    public static final String REASON_NO_POSITIVE_DISTANCE_EDGES = "H11_LB_NO_POSITIVE_DISTANCE_EDGES";
    public static final String REASON_INVALID_RESULT = "H11_LB_INVALID_RESULT";

    private static final double LIVE_PENALTY_LOWER_BOUND = 1.0d;
    private static final double TURN_COST_LOWER_BOUND = 0.0d;
    private static final int DAYS_PER_WEEK = 7;

    private final double lowerBoundCostPerDistance;

    private GeometryLowerBoundModel(double lowerBoundCostPerDistance) {
        this.lowerBoundCostPerDistance = lowerBoundCostPerDistance;
    }

    public double lowerBoundCostPerDistance() {
        return lowerBoundCostPerDistance;
    }

    public static GeometryLowerBoundModel calibrateEuclidean(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine
    ) {
        return calibrate(
                "EUCLIDEAN",
                edgeGraph,
                profileStore,
                costEngine,
                GeometryDistance::euclideanDistance
        );
    }

    public static GeometryLowerBoundModel calibrateSpherical(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine
    ) {
        return calibrate(
                "SPHERICAL",
                edgeGraph,
                profileStore,
                costEngine,
                GeometryDistance::greatCircleDistanceMeters
        );
    }

    private static GeometryLowerBoundModel calibrate(
            String metricName,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            DistanceFunction distanceFunction
    ) {
        Objects.requireNonNull(metricName, "metricName");
        Objects.requireNonNull(distanceFunction, "distanceFunction");

        if (edgeGraph == null) {
            throw new HeuristicConfigurationException(
                    REASON_GRAPH_REQUIRED,
                    "edgeGraph must be provided for lower-bound calibration"
            );
        }
        if (profileStore == null) {
            throw new HeuristicConfigurationException(
                    REASON_PROFILE_REQUIRED,
                    "profileStore must be provided for lower-bound calibration"
            );
        }
        if (costEngine == null) {
            throw new HeuristicConfigurationException(
                    REASON_COST_REQUIRED,
                    "costEngine must be provided for lower-bound calibration"
            );
        }
        if (costEngine.edgeGraph() != edgeGraph) {
            throw new HeuristicConfigurationException(
                    REASON_COST_GRAPH_MISMATCH,
                    "costEngine graph contract does not match heuristic edgeGraph"
            );
        }
        if (costEngine.profileStore() != profileStore) {
            throw new HeuristicConfigurationException(
                    REASON_COST_PROFILE_MISMATCH,
                    "costEngine profile contract does not match heuristic profileStore"
            );
        }
        if (!edgeGraph.hasCoordinates()) {
            throw new HeuristicConfigurationException(
                    REASON_COORDINATES_REQUIRED,
                    "coordinates are required for " + metricName + " lower-bound calibration"
            );
        }
        if (edgeGraph.edgeCount() == 0) {
            throw new HeuristicConfigurationException(
                    REASON_EMPTY_GRAPH,
                    "graph must contain at least one edge for lower-bound calibration"
            );
        }

        double bestRatio = Double.POSITIVE_INFINITY;
        boolean foundPositiveDistance = false;

        for (int edgeId = 0; edgeId < edgeGraph.edgeCount(); edgeId++) {
            float baseWeight = edgeGraph.getBaseWeight(edgeId);
            if (!Float.isFinite(baseWeight) || baseWeight < 0.0f) {
                throw new HeuristicConfigurationException(
                        REASON_INVALID_BASE_WEIGHT,
                        "edge " + edgeId + " has invalid base weight: " + baseWeight
                );
            }

            int profileId = edgeGraph.getProfileId(edgeId);
            double minTemporalMultiplier = minimumTemporalMultiplier(profileStore, profileId);
            if (!Double.isFinite(minTemporalMultiplier) || minTemporalMultiplier < 0.0d) {
                throw new HeuristicConfigurationException(
                        REASON_INVALID_TEMPORAL_MIN,
                        "edge " + edgeId + " profile " + profileId
                                + " produced invalid temporal lower bound: " + minTemporalMultiplier
                );
            }

            double lowerBoundCost = baseWeight * minTemporalMultiplier * LIVE_PENALTY_LOWER_BOUND + TURN_COST_LOWER_BOUND;
            if (!Double.isFinite(lowerBoundCost) || lowerBoundCost < 0.0d) {
                throw new HeuristicConfigurationException(
                        REASON_INVALID_TEMPORAL_MIN,
                        "edge " + edgeId + " produced invalid lower-bound cost: " + lowerBoundCost
                );
            }

            int fromNode = edgeGraph.getEdgeOrigin(edgeId);
            int toNode = edgeGraph.getEdgeDestination(edgeId);
            double fromX = edgeGraph.getNodeX(fromNode);
            double fromY = edgeGraph.getNodeY(fromNode);
            double toX = edgeGraph.getNodeX(toNode);
            double toY = edgeGraph.getNodeY(toNode);

            double edgeDistance = distanceFunction.distance(fromX, fromY, toX, toY);
            if (!Double.isFinite(edgeDistance) || edgeDistance < 0.0d) {
                throw new HeuristicConfigurationException(
                        REASON_INVALID_EDGE_DISTANCE,
                        "edge " + edgeId + " has invalid " + metricName + " distance: " + edgeDistance
                );
            }
            if (edgeDistance == 0.0d) {
                continue;
            }

            foundPositiveDistance = true;
            double ratio = lowerBoundCost / edgeDistance;
            if (!Double.isFinite(ratio) || ratio < 0.0d) {
                throw new HeuristicConfigurationException(
                        REASON_INVALID_EDGE_RATIO,
                        "edge " + edgeId + " has invalid lower-bound ratio: " + ratio
                );
            }
            if (ratio < bestRatio) {
                bestRatio = ratio;
            }
        }

        if (!foundPositiveDistance) {
            throw new HeuristicConfigurationException(
                    REASON_NO_POSITIVE_DISTANCE_EDGES,
                    "no edge has positive " + metricName + " geometry distance"
            );
        }
        if (!Double.isFinite(bestRatio) || bestRatio < 0.0d) {
            throw new HeuristicConfigurationException(
                    REASON_INVALID_RESULT,
                    "calibrated lowerBoundCostPerDistance is invalid: " + bestRatio
            );
        }
        return new GeometryLowerBoundModel(bestRatio);
    }

    private static double minimumTemporalMultiplier(ProfileStore profileStore, int profileId) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            int selected = profileStore.selectProfileForDay(profileId, day);
            double dayMinimum = selected == ProfileStore.DEFAULT_PROFILE_ID
                    ? ProfileStore.DEFAULT_MULTIPLIER
                    : profileStore.getMetadata(selected).minMultiplier();
            if (dayMinimum < minimum) {
                minimum = dayMinimum;
            }
        }
        return minimum;
    }

    @FunctionalInterface
    private interface DistanceFunction {
        double distance(double x1, double y1, double x2, double y2);
    }
}
