package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.core.time.TimeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical source-level topology input used by v13 rebuild-and-reload publication.
 */
@Value
@Builder(toBuilder = true)
public class TopologyModelSource {
    @Builder.Default
    TimeUtils.EngineTimeUnit engineTimeUnit = TimeUtils.EngineTimeUnit.SECONDS;
    String modelVersion;
    @Builder.Default
    String profileTimezone = "UTC";
    @Singular("profile")
    List<ProfileDefinition> profiles;
    @Singular("node")
    List<NodeDefinition> nodes;
    @Singular("edge")
    List<EdgeDefinition> edges;
    @Singular("turnCost")
    List<TurnCostDefinition> turnCosts;

    public boolean hasCoordinates() {
        return !nodes.isEmpty() && nodes.getFirst().getX() != null;
    }

    public void validate() {
        Objects.requireNonNull(engineTimeUnit, "engineTimeUnit");
        requireNonBlank(modelVersion, "modelVersion");
        requireNonBlank(profileTimezone, "profileTimezone");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("topology source must contain at least one node");
        }

        boolean sawCoordinates = false;
        boolean sawMissingCoordinates = false;
        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : nodes) {
            requireNonBlank(node.getNodeId(), "node.nodeId");
            if (!nodeIds.add(node.getNodeId())) {
                throw new IllegalArgumentException("duplicate nodeId: " + node.getNodeId());
            }
            boolean hasX = node.getX() != null;
            boolean hasY = node.getY() != null;
            if (hasX != hasY) {
                throw new IllegalArgumentException("node coordinates must provide both x and y: " + node.getNodeId());
            }
            sawCoordinates |= hasX;
            sawMissingCoordinates |= !hasX;
        }
        if (sawCoordinates && sawMissingCoordinates) {
            throw new IllegalArgumentException("topology source coordinates must be enabled for all nodes or none");
        }

        Set<Integer> profileIds = new HashSet<>();
        for (ProfileDefinition profile : profiles) {
            validateProfile(profile, profileIds);
        }

        Set<String> edgeIds = new HashSet<>();
        for (EdgeDefinition edge : edges) {
            requireNonBlank(edge.getEdgeId(), "edge.edgeId");
            requireNonBlank(edge.getOriginNodeId(), "edge.originNodeId");
            requireNonBlank(edge.getDestinationNodeId(), "edge.destinationNodeId");
            if (!edgeIds.add(edge.getEdgeId())) {
                throw new IllegalArgumentException("duplicate edgeId: " + edge.getEdgeId());
            }
            if (!nodeIds.contains(edge.getOriginNodeId())) {
                throw new IllegalArgumentException("edge origin node missing: " + edge.getEdgeId());
            }
            if (!nodeIds.contains(edge.getDestinationNodeId())) {
                throw new IllegalArgumentException("edge destination node missing: " + edge.getEdgeId());
            }
            if (!Float.isFinite(edge.getBaseWeight()) || edge.getBaseWeight() < 0.0f) {
                throw new IllegalArgumentException("edge baseWeight must be finite and >= 0: " + edge.getEdgeId());
            }
            validateUnsignedShort(edge.getProfileId(), "edge.profileId");
        }

        Set<String> turnKeys = new HashSet<>();
        for (TurnCostDefinition turnCost : turnCosts) {
            requireNonBlank(turnCost.getFromEdgeId(), "turnCost.fromEdgeId");
            requireNonBlank(turnCost.getToEdgeId(), "turnCost.toEdgeId");
            if (!edgeIds.contains(turnCost.getFromEdgeId())) {
                throw new IllegalArgumentException("turn-cost fromEdgeId missing: " + turnCost.getFromEdgeId());
            }
            if (!edgeIds.contains(turnCost.getToEdgeId())) {
                throw new IllegalArgumentException("turn-cost toEdgeId missing: " + turnCost.getToEdgeId());
            }
            validateTurnPenalty(turnCost.getPenaltySeconds(), "turnCost.penaltySeconds");
            String turnKey = turnCost.getFromEdgeId() + "->" + turnCost.getToEdgeId();
            if (!turnKeys.add(turnKey)) {
                throw new IllegalArgumentException("duplicate turn-cost relationship: " + turnKey);
            }
        }
    }

    private static void validateProfile(ProfileDefinition profile, Set<Integer> profileIds) {
        validateUnsignedShort(profile.getProfileId(), "profile.profileId");
        if (!profileIds.add(profile.getProfileId())) {
            throw new IllegalArgumentException("duplicate profileId: " + profile.getProfileId());
        }
        int dayMask = profile.getDayMask();
        if (dayMask <= 0 || dayMask > 0x7F) {
            throw new IllegalArgumentException("profile dayMask must be a non-zero 7-bit mask: " + profile.getProfileId());
        }
        if (profile.getBuckets().isEmpty()) {
            throw new IllegalArgumentException("profile buckets must be non-empty: " + profile.getProfileId());
        }
        if (!Float.isFinite(profile.getMultiplier()) || profile.getMultiplier() <= 0.0f) {
            throw new IllegalArgumentException("profile multiplier must be finite and > 0: " + profile.getProfileId());
        }
        for (int i = 0; i < profile.getBuckets().size(); i++) {
            Float bucket = profile.getBuckets().get(i);
            if (bucket == null || !Float.isFinite(bucket) || bucket <= 0.0f) {
                throw new IllegalArgumentException("profile bucket must be finite and > 0: profile="
                        + profile.getProfileId() + ", bucket=" + i);
            }
            float scaled = bucket * profile.getMultiplier();
            if (!Float.isFinite(scaled) || scaled <= 0.0f) {
                throw new IllegalArgumentException("profile bucket * multiplier must be finite and > 0: profile="
                        + profile.getProfileId() + ", bucket=" + i);
            }
        }
    }

    private static void validateTurnPenalty(float penaltySeconds, String fieldName) {
        if (Float.isNaN(penaltySeconds) || penaltySeconds < 0.0f || penaltySeconds == Float.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException(fieldName + " must be >= 0 and not NaN/-INF");
        }
    }

    private static void validateUnsignedShort(int value, String fieldName) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(fieldName + " must be in [0,65535], got " + value);
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class ProfileDefinition {
        int profileId;
        int dayMask;
        @Singular("bucket")
        List<Float> buckets;
        float multiplier;
    }

    @Value
    @Builder(toBuilder = true)
    public static class NodeDefinition {
        String nodeId;
        Double x;
        Double y;
    }

    @Value
    @Builder(toBuilder = true)
    public static class EdgeDefinition {
        String edgeId;
        String originNodeId;
        String destinationNodeId;
        float baseWeight;
        int profileId;
    }

    @Value
    @Builder(toBuilder = true)
    public static class TurnCostDefinition {
        String fromEdgeId;
        String toEdgeId;
        float penaltySeconds;
    }
}
