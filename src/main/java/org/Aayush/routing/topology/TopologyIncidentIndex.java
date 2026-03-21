package org.Aayush.routing.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable node-to-incident-edge index for quarantine expansion.
 */
final class TopologyIncidentIndex {
    private final int[][] incidentEdgesByNode;

    private TopologyIncidentIndex(int[][] incidentEdgesByNode) {
        this.incidentEdgesByNode = incidentEdgesByNode;
    }

    static TopologyIncidentIndex fromLayout(TopologyIndexLayout layout) {
        TopologyIndexLayout nonNullLayout = Objects.requireNonNull(layout, "layout");
        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] working = new ArrayList[nonNullLayout.nodeCount()];
        for (int nodeIndex = 0; nodeIndex < working.length; nodeIndex++) {
            working[nodeIndex] = new ArrayList<>();
        }

        List<TopologyIndexLayout.OrderedEdge> orderedEdges = nonNullLayout.orderedEdges();
        for (int edgeIndex = 0; edgeIndex < orderedEdges.size(); edgeIndex++) {
            TopologyIndexLayout.OrderedEdge orderedEdge = orderedEdges.get(edgeIndex);
            working[orderedEdge.originNodeIndex()].add(edgeIndex);
            if (orderedEdge.destinationNodeIndex() != orderedEdge.originNodeIndex()) {
                working[orderedEdge.destinationNodeIndex()].add(edgeIndex);
            }
        }

        int[][] incidentEdges = new int[working.length][];
        for (int nodeIndex = 0; nodeIndex < working.length; nodeIndex++) {
            ArrayList<Integer> source = working[nodeIndex];
            int[] nodeIncidentEdges = new int[source.size()];
            for (int i = 0; i < source.size(); i++) {
                nodeIncidentEdges[i] = source.get(i);
            }
            incidentEdges[nodeIndex] = nodeIncidentEdges;
        }
        return new TopologyIncidentIndex(incidentEdges);
    }

    int[] incidentEdges(int nodeId) {
        return incidentEdgesByNode[nodeId];
    }
}
