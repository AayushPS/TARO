package org.Aayush.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntConsumer;

/**
 * Stage 4: Memory-Efficient Edge-Based Graph (Physical Layer).
 * <p>
 * ARCHITECTURAL NOTE:
 * This class acts as the "Dumb Physical Layer". It provides raw, efficient access
 * to the underlying memory-mapped data without enforcing semantic interpretations.
 * <p>
 * - It exposes coordinates as generic X/Y doubles (matching the Schema).
 * - It DOES NOT implement spatial logic (distance, adjacency resolution).
 * - Higher-level traits (Stage 15+ Addressing) should consume this class.
 * <p>
 * Features:
 * - SoA (Structure of Arrays) layout for cache locality.
 * - Zero-Copy FlatBuffers loading.
 * - CSR (Compressed Sparse Row) for O(1) neighbor access.
 * - O(1) Edge-to-Origin lookup (via edgeOrigin buffer).
 * - Optional Coordinate support (Generic: GPS, Euclidean, or Abstract).
 */
public class EdgeGraph {

    // ========================================================================
    // CONSTANTS
    // ========================================================================
    private static final int FILE_IDENTIFIER = 0x4F524154; // "TARO"
    private static final int COORDINATE_STRUCT_SIZE = 16; // double x + double y

    // Field Indices in GraphTopology table
    private static final int FIELD_NODE_COUNT = 0;
    private static final int FIELD_EDGE_COUNT = 1;
    private static final int FIELD_FIRST_EDGE = 2;
    private static final int FIELD_EDGE_TARGET = 3;
    private static final int FIELD_COORDINATES = 4;
    private static final int FIELD_BASE_WEIGHTS = 5;
    private static final int FIELD_EDGE_PROFILE_ID = 6;
    private static final int FIELD_EDGE_ORIGIN = 8;

    // ========================================================================
    // DATA BUFFERS (SoA Layout)
    // ========================================================================

    // CSR Index: first_edge[node_idx] -> start index in edge arrays
    private final IntBuffer firstEdge;

    // Edge Properties
    private final IntBuffer edgeTarget;
    private final IntBuffer edgeOrigin;
    private final FloatBuffer baseWeights;
    private final ShortBuffer edgeProfileIds;

    // Node Properties (Raw bytes for 16-byte structs). Can be null.
    private final ByteBuffer coordinates;

    private final int nodeCount;
    private final int edgeCount;

    EdgeGraph(int nodeCount, int edgeCount,
              IntBuffer firstEdge, IntBuffer edgeTarget, IntBuffer edgeOrigin,
              FloatBuffer baseWeights, ShortBuffer edgeProfileIds,
              ByteBuffer coordinates) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.firstEdge = firstEdge;
        this.edgeTarget = edgeTarget;
        this.edgeOrigin = edgeOrigin;
        this.baseWeights = baseWeights;
        this.edgeProfileIds = edgeProfileIds;
        this.coordinates = coordinates;
    }

    // ========================================================================
    // CORE ACCESSORS (O(1))
    // ========================================================================

    public int nodeCount() { return nodeCount; }
    public int edgeCount() { return edgeCount; }

    /**
     * UNCHECKED - caller must ensure edgeId is valid for performance.
     * Use asserts for debugging.
     */
    public int getEdgeDestination(int edgeId) {
        assert edgeId >= 0 && edgeId < edgeCount : "Edge " + edgeId + " out of bounds";
        return edgeTarget.get(edgeId);
    }

    public float getBaseWeight(int edgeId) {
        assert edgeId >= 0 && edgeId < edgeCount;
        return baseWeights.get(edgeId);
    }

    public int getProfileId(int edgeId) {
        assert edgeId >= 0 && edgeId < edgeCount;
        return edgeProfileIds.get(edgeId) & 0xFFFF;
    }

    // ========================================================================
    // COORDINATE ACCESS (Generic - no GPS assumptions)
    // ========================================================================

    /**
     * Check if this graph has coordinate data.
     */
    public boolean hasCoordinates() {
        return coordinates != null && coordinates.remaining() > 0;
    }

    /**
     * Gets the first coordinate component (lat for GPS, x for Euclidean, etc).
     * Throws if graph has no coordinates.
     */
    public double getNodeX(int nodeId) {
        if (!hasCoordinates()) {
            throw new UnsupportedOperationException(
                "Graph has no coordinate data. " +
                "This is likely an abstract/network graph. " +
                "Use Spatial addressing trait for coordinate-based routing."
            );
        }
        assert nodeId >= 0 && nodeId < nodeCount;
        return coordinates.getDouble(nodeId << 4);
    }

    /**
     * Gets the second coordinate component (lon for GPS, y for Euclidean, etc).
     */
    public double getNodeY(int nodeId) {
        if (!hasCoordinates()) {
            throw new UnsupportedOperationException(
                "Graph has no coordinate data."
            );
        }
        assert nodeId >= 0 && nodeId < nodeCount;
        return coordinates.getDouble((nodeId << 4) + 8);
    }

    /**
     * @deprecated Use {@link #getNodeX(int)} instead. Assumes GPS semantics.
     */
    @Deprecated
    public double getNodeLat(int nodeId) {
        return getNodeX(nodeId);
    }

    /**
     * @deprecated Use {@link #getNodeY(int)} instead. Assumes GPS semantics.
     */
    @Deprecated
    public double getNodeLon(int nodeId) {
        return getNodeY(nodeId);
    }

    public int getNodeDegree(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IndexOutOfBoundsException("Node " + nodeId + " out of bounds");
        }
        return firstEdge.get(nodeId + 1) - firstEdge.get(nodeId);
    }

    public boolean isTerminalNode(int nodeId) {
        return getNodeDegree(nodeId) == 0;
    }

    /**
     * Pure Data Transfer Object (DTO) for Coordinates.
     * Logic (distance, bearing, etc.) is intentionally omitted to allow
     * Trait Adapters (Spatial vs Abstract) to define behavior.
     */
    public static class Coordinate {
        public final double x;  // lat for GPS, x for Euclidean, etc.
        public final double y;  // lon for GPS, y for Euclidean, etc.

        public Coordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("(%.6f, %.6f)", x, y);
        }
    }

    public Coordinate getNodeCoordinate(int nodeId) {
        return new Coordinate(getNodeX(nodeId), getNodeY(nodeId));
    }

    // ========================================================================
    // TRAVERSAL & SEARCH
    // ========================================================================

    /**
     * Finds the origin node of an edge using the O(1) edgeOrigin buffer.
     * Time Complexity: O(1)
     */
    public int getEdgeOrigin(int edgeId) {
        if (edgeId < 0 || edgeId >= edgeCount) {
            throw new IndexOutOfBoundsException("Edge " + edgeId + " out of bounds [0, " + edgeCount + ")");
        }
        return edgeOrigin.get(edgeId);
    }

    /**
     * Returns a zero-allocation iterator.
     */
    public EdgeIterator iterator() {
        return new EdgeIterator(this);
    }

    /**
     * Static nested class for zero-allocation iteration.
     * Does not hold implicit reference to parent.
     */
    public static class EdgeIterator {
        private final EdgeGraph graph;
        private int current;
        private int end;

        EdgeIterator(EdgeGraph graph) {
            this.graph = graph;
        }

        /**
         * Resets iterator to traverse edges outgoing from the target of the given edgeId.
         */
        public EdgeIterator reset(int edgeId) {
            int targetNode = graph.edgeTarget.get(edgeId);
            this.current = graph.firstEdge.get(targetNode);
            this.end = graph.firstEdge.get(targetNode + 1);
            return this;
        }

        /**
         * Resets iterator to traverse edges outgoing from a specific node.
         */
        public EdgeIterator resetForNode(int nodeId) {
            this.current = graph.firstEdge.get(nodeId);
            this.end = graph.firstEdge.get(nodeId + 1);
            return this;
        }

        public boolean hasNext() {
            return current < end;
        }

        public int next() {
            if (current >= end) throw new NoSuchElementException();
            return current++;
        }
    }

    /**
     * Functional-style iteration helper.
     */
    public void forEachOutgoingEdge(int edgeId, IntConsumer action) {
        long range = getOutgoingEdgeRange(edgeId);
        int start = (int)(range >>> 32);
        int end = (int)range;
        for (int i = start; i < end; i++) {
            action.accept(i);
        }
    }

    public long getOutgoingEdgeRange(int edgeId) {
        int targetNode = edgeTarget.get(edgeId);
        int start = firstEdge.get(targetNode);
        int end = firstEdge.get(targetNode + 1);
        return ((long) start << 32) | (end & 0xFFFFFFFFL);
    }

    @Deprecated
    public int[] getOutgoingEdges(int edgeId) {
        long range = getOutgoingEdgeRange(edgeId);
        int start = (int)(range >>> 32);
        int end = (int)range;
        int len = end - start;
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = start + i;
        }
        return result;
    }

    // ========================================================================
    // DEBUG & VALIDATION
    // ========================================================================

    @Override
    public String toString() {
        return String.format("EdgeGraph[nodes=%d, edges=%d, avgDegree=%.2f, hasCoordinates=%b]",
            nodeCount, edgeCount, nodeCount > 0 ? (double) edgeCount / nodeCount : 0, hasCoordinates());
    }

    public String toDetailedString() {
        if (nodeCount > 50) return toString() + " (too large to detail)";
        StringBuilder sb = new StringBuilder(toString()).append("\n");
        EdgeIterator iter = iterator();
        for (int n = 0; n < nodeCount; n++) {
            if (hasCoordinates()) {
                sb.append(String.format("Node %d %s: [", n, getNodeCoordinate(n)));
            } else {
                sb.append(String.format("Node %d: [", n));
            }
            iter.resetForNode(n);
            while (iter.hasNext()) {
                int e = iter.next();
                sb.append(String.format("%d->%d(%.1f)", e, getEdgeDestination(e), getBaseWeight(e)));
                if (iter.hasNext()) sb.append(", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    public static class ValidationResult {
        public final boolean isValid;
        public final List<String> errors;
        public final List<String> warnings;

        public ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = errors;
            this.warnings = warnings;
        }
    }

    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. CSR Monotonicity and Consistency
        if (firstEdge.get(0) != 0) {
            errors.add("CSR must start at 0, got " + firstEdge.get(0));
        }
        if (firstEdge.get(nodeCount) != edgeCount) {
            errors.add("CSR end mismatch: expected " + edgeCount + ", got " + firstEdge.get(nodeCount));
        }
        
        for (int i = 0; i < nodeCount; i++) {
            int start = firstEdge.get(i);
            int end = firstEdge.get(i + 1);
            if (start > end) {
                errors.add("CSR violation at node " + i + ": start=" + start + " > end=" + end);
            }
            if (end > edgeCount) {
                errors.add("CSR overflow at node " + i + ": end=" + end + " > edgeCount=" + edgeCount);
            }
        }

        // 2. Edge Targets
        for (int e = 0; e < edgeCount; e++) {
            int target = edgeTarget.get(e);
            if (target < 0 || target >= nodeCount) {
                errors.add("Edge " + e + " invalid target: " + target);
                if (errors.size() > 10) { errors.add("..."); break; }
            }
        }

        // 3. Generic Coordinate Validation (if present)
        if (hasCoordinates()) {
            for (int n = 0; n < nodeCount; n++) {
                double x = getNodeX(n);
                double y = getNodeY(n);
                if (Double.isNaN(x) || Double.isInfinite(x)) {
                    errors.add("Node " + n + " invalid x: " + x);
                }
                if (Double.isNaN(y) || Double.isInfinite(y)) {
                    errors.add("Node " + n + " invalid y: " + y);
                }
                if (errors.size() > 20) break;
            }
        } else {
            warnings.add("Graph has no coordinate data (Abstract/Network graph mode)");
        }

        // 4. Isolated Nodes Check
        int isolatedNodes = 0;
        for (int n = 0; n < nodeCount; n++) {
            if (getNodeDegree(n) == 0) isolatedNodes++;
        }
        if (isolatedNodes > 0) {
            warnings.add("Graph contains " + isolatedNodes + " isolated nodes (degree 0)");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ========================================================================
    // FLATBUFFERS LOADING
    // ========================================================================

    public static EdgeGraph fromFlatBuffer(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 1. Enforce Identifier
        if (buffer.remaining() >= 8) {
            int ident = buffer.getInt(4);
            if (ident != FILE_IDENTIFIER) {
                 throw new IllegalArgumentException(
                    String.format("Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X", 
                    FILE_IDENTIFIER, ident));
            }
        }

        int rootOffset = buffer.getInt(buffer.position()) + buffer.position();
        Table model = new Table(buffer, rootOffset);
        
        int topologyOffset = model.getUnionOrTableOffset(1); 
        if (topologyOffset == 0) throw new IllegalArgumentException("GraphTopology missing");
        
        Table topology = new Table(buffer, topologyOffset);

        int nodeCount = topology.getIntField(FIELD_NODE_COUNT, 0);
        int edgeCount = topology.getIntField(FIELD_EDGE_COUNT, 0);

        IntBuffer firstEdge = topology.getVectorAsIntBuffer(FIELD_FIRST_EDGE);
        IntBuffer edgeTarget = topology.getVectorAsIntBuffer(FIELD_EDGE_TARGET);
        FloatBuffer baseWeights = topology.getVectorAsFloatBuffer(FIELD_BASE_WEIGHTS);
        ShortBuffer edgeProfileIds = topology.getVectorAsShortBuffer(FIELD_EDGE_PROFILE_ID);
        
        // Try to get Edge Origin (Field 7)
        IntBuffer edgeOrigin = topology.getVectorAsIntBuffer(FIELD_EDGE_ORIGIN);
        
        // Coordinates are optional (nullable)
        ByteBuffer coordinates = topology.getVectorAsByteBuffer(FIELD_COORDINATES, COORDINATE_STRUCT_SIZE);

        if (firstEdge == null || edgeTarget == null) {
            throw new IllegalArgumentException("Missing required graph vectors (firstEdge or edgeTarget)");
        }
        
        // Fallback: Compute Edge Origin if missing from file (Backward Compatibility)
        if (edgeOrigin == null) {
            edgeOrigin = computeEdgeOrigins(nodeCount, edgeCount, firstEdge);
        }
        
        return new EdgeGraph(nodeCount, edgeCount, firstEdge, edgeTarget, edgeOrigin, 
                             baseWeights, edgeProfileIds, coordinates);
    }
    
    /**
     * Helper to compute edge origins in memory if missing from file.
     * Ensures O(1) access contract even for older files.
     */
    private static IntBuffer computeEdgeOrigins(int nodeCount, int edgeCount, IntBuffer firstEdge) {
        int[] origins = new int[edgeCount];
        // Note: Using int[] for simple heap allocation. 
        // For 25M edges, this is ~100MB RAM, acceptable per spec.
        
        for (int n = 0; n < nodeCount; n++) {
            int start = firstEdge.get(n);
            int end = firstEdge.get(n + 1);
            
            // Safety check against malformed CSR (though validate() catches this separately)
            if (start < 0 || end > edgeCount || start > end) continue;
            
            for (int i = start; i < end; i++) {
                origins[i] = n;
            }
        }
        return IntBuffer.wrap(origins);
    }

    private static class Table {
        private final ByteBuffer bb;
        private final int pos;
        private final int vtablePos;
        private final int vtableLen;

        Table(ByteBuffer bb, int pos) {
            this.bb = bb;
            this.pos = pos;
            this.vtablePos = pos - bb.getInt(pos);
            this.vtableLen = bb.getShort(vtablePos);
        }

        int getOffset(int fieldIndex) {
            int vtableOffset = 4 + (fieldIndex * 2);
            return (vtableOffset < vtableLen) ? bb.getShort(vtablePos + vtableOffset) : 0;
        }

        int getIntField(int fieldIndex, int defaultValue) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? bb.getInt(pos + offset) : defaultValue;
        }

        int getUnionOrTableOffset(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? pos + offset + bb.getInt(pos + offset) : 0;
        }

        IntBuffer getVectorAsIntBuffer(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            if (offset == 0) return null;
            int vectorStart = pos + offset + bb.getInt(pos + offset);
            int vectorLen = bb.getInt(vectorStart);
            ByteBuffer dup = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            dup.position(vectorStart + 4);
            dup.limit(vectorStart + 4 + vectorLen * 4);
            return dup.asIntBuffer();
        }
        
        FloatBuffer getVectorAsFloatBuffer(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            if (offset == 0) return null;
            int vectorStart = pos + offset + bb.getInt(pos + offset);
            int vectorLen = bb.getInt(vectorStart);
            ByteBuffer dup = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            dup.position(vectorStart + 4);
            dup.limit(vectorStart + 4 + vectorLen * 4);
            return dup.asFloatBuffer();
        }

        ShortBuffer getVectorAsShortBuffer(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            if (offset == 0) return null;
            int vectorStart = pos + offset + bb.getInt(pos + offset);
            int vectorLen = bb.getInt(vectorStart);
            ByteBuffer dup = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            dup.position(vectorStart + 4);
            dup.limit(vectorStart + 4 + vectorLen * 2);
            return dup.asShortBuffer();
        }

        ByteBuffer getVectorAsByteBuffer(int fieldIndex, int structSizeBytes) {
            int offset = getOffset(fieldIndex);
            if (offset == 0) return null;
            int vectorStart = pos + offset + bb.getInt(pos + offset);
            int vectorLen = bb.getInt(vectorStart);
            
            ByteBuffer dup = bb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            dup.position(vectorStart + 4);
            // Limit calculated based on struct size
            dup.limit(vectorStart + 4 + vectorLen * structSizeBytes);
            // Slice so absolute reads start at 0 for the vector payload
            return dup.slice().order(ByteOrder.LITTLE_ENDIAN);
        }
    }
}
