package org.Aayush.routing.topology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic runtime index layout derived from a source-level topology snapshot.
 */
final class TopologyIndexLayout {
    private final List<TopologyModelSource.NodeDefinition> nodes;
    private final List<OrderedEdge> orderedEdges;
    private final Map<String, Integer> nodeIndexById;
    private final Map<String, Integer> edgeIndexById;

    private TopologyIndexLayout(
            List<TopologyModelSource.NodeDefinition> nodes,
            List<OrderedEdge> orderedEdges,
            Map<String, Integer> nodeIndexById,
            Map<String, Integer> edgeIndexById
    ) {
        this.nodes = List.copyOf(nodes);
        this.orderedEdges = List.copyOf(orderedEdges);
        this.nodeIndexById = Map.copyOf(nodeIndexById);
        this.edgeIndexById = Map.copyOf(edgeIndexById);
    }

    static TopologyIndexLayout fromSource(TopologyModelSource source) {
        TopologyModelSource nonNullSource = Objects.requireNonNull(source, "source");
        List<TopologyModelSource.NodeDefinition> nodes = nonNullSource.getNodes();
        LinkedHashMap<String, Integer> nodeIndexById = new LinkedHashMap<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            nodeIndexById.put(nodes.get(i).getNodeId(), i);
        }

        ArrayList<OrderedEdge> orderedEdges = new ArrayList<>(nonNullSource.getEdges().size());
        for (int i = 0; i < nonNullSource.getEdges().size(); i++) {
            TopologyModelSource.EdgeDefinition edge = nonNullSource.getEdges().get(i);
            int originNodeIndex = requireIndex(nodeIndexById, edge.getOriginNodeId(), "origin");
            int destinationNodeIndex = requireIndex(nodeIndexById, edge.getDestinationNodeId(), "destination");
            orderedEdges.add(new OrderedEdge(edge, i, originNodeIndex, destinationNodeIndex));
        }
        orderedEdges.sort(Comparator
                .comparingInt(OrderedEdge::originNodeIndex)
                .thenComparingInt(OrderedEdge::originalIndex));

        LinkedHashMap<String, Integer> edgeIndexById = new LinkedHashMap<>(orderedEdges.size());
        for (int edgeIndex = 0; edgeIndex < orderedEdges.size(); edgeIndex++) {
            edgeIndexById.put(orderedEdges.get(edgeIndex).edge().getEdgeId(), edgeIndex);
        }

        return new TopologyIndexLayout(nodes, orderedEdges, nodeIndexById, edgeIndexById);
    }

    List<TopologyModelSource.NodeDefinition> nodes() {
        return nodes;
    }

    List<OrderedEdge> orderedEdges() {
        return orderedEdges;
    }

    Map<String, Integer> nodeIndexById() {
        return nodeIndexById;
    }

    Map<String, Integer> edgeIndexById() {
        return edgeIndexById;
    }

    int nodeCount() {
        return nodes.size();
    }

    int edgeCount() {
        return orderedEdges.size();
    }

    String nodeId(int nodeIndex) {
        return nodes.get(nodeIndex).getNodeId();
    }

    String edgeId(int edgeIndex) {
        return orderedEdges.get(edgeIndex).edge().getEdgeId();
    }

    Integer findNodeIndex(String nodeId) {
        return nodeIndexById.get(nodeId);
    }

    Integer findEdgeIndex(String edgeId) {
        return edgeIndexById.get(edgeId);
    }

    private static int requireIndex(Map<String, Integer> indexById, String id, String kind) {
        Integer index = indexById.get(id);
        if (index == null) {
            throw new IllegalArgumentException(kind + " node missing from topology layout: " + id);
        }
        return index;
    }

    record OrderedEdge(
            TopologyModelSource.EdgeDefinition edge,
            int originalIndex,
            int originNodeIndex,
            int destinationNodeIndex
    ) {
    }
}
