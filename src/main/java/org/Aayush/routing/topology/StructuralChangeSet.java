package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Typed v13 batched structural changes intended for one topology rebuild publication.
 */
@Value
@Builder
public class StructuralChangeSet {
    @Builder.Default
    RolloutPolicy rolloutPolicy = RolloutPolicy.ATOMIC_RELOAD;
    @Singular("addedNode")
    List<NodeAddition> addedNodes;
    @Singular("removedNode")
    List<String> removedNodes;
    @Singular("addedEdge")
    List<EdgeAddition> addedEdges;
    @Singular("removedEdge")
    List<String> removedEdges;
    @Singular("changedCoordinate")
    List<CoordinateChange> changedCoordinates;
    @Singular("changedTurnRelationship")
    List<TurnRelationshipChange> changedTurnRelationships;
    @Singular("changedProfileAssignment")
    List<ProfileAssignmentChange> changedProfileAssignments;

    public enum RolloutPolicy {
        VALIDATE_ONLY,
        ATOMIC_RELOAD
    }

    @Value
    @Builder
    public static class NodeAddition {
        String nodeId;
        Double x;
        Double y;
    }

    @Value
    @Builder
    public static class EdgeAddition {
        String edgeId;
        String originNodeId;
        String destinationNodeId;
        float baseWeight;
        int profileId;
    }

    @Value
    @Builder
    public static class CoordinateChange {
        String nodeId;
        double x;
        double y;
    }

    @Value
    @Builder
    public static class TurnRelationshipChange {
        TurnChangeOperation operation;
        String fromEdgeId;
        String toEdgeId;
        float penaltySeconds;
    }

    @Value
    @Builder
    public static class ProfileAssignmentChange {
        String edgeId;
        int profileId;
    }

    public enum TurnChangeOperation {
        UPSERT,
        REMOVE
    }
}
