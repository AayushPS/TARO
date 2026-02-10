package org.Aayush.routing.graph;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.serialization.flatbuffers.ModelContractValidator;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Model;

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
 * - Zero-copy loading for primitive vectors; coordinates are copied from structs.
 * - CSR (Compressed Sparse Row) for O(1) neighbor access.
 * - O(1) Edge-to-Origin lookup (via edgeOrigin buffer).
 * - Optional Coordinate support (Generic: GPS, Euclidean, or Abstract).
 */
public class EdgeGraph {

    // ========================================================================
    // CONSTANTS
    // ========================================================================
    /*
    Why "TARO" becomes 0x4F524154
        This is about ASCII + hexadecimal + endianness.
        ASCII codes:
            T = 0x54
            A = 0x41
            R = 0x52
            O = 0x4F
    Now comes the tricky part.
    FlatBuffers stores identifiers as little-endian 32-bit integers.
    So memory order becomes:
            T  A  R  O
            54 41 52 4F
    But when interpreted as a 32-bit integer, it appears reversed: 0x4F524154
     */
    private static final int FILE_IDENTIFIER = 0x4F524154; // "TARO"
    private static final int COORDINATE_STRUCT_SIZE = 16; // double x + double y

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

    @Getter
    @Accessors(fluent = true)
    private final int nodeCount;
    @Getter
    @Accessors(fluent = true)
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
    @AllArgsConstructor
    public static class Coordinate {
        public final double x;  // lat for GPS, x for Euclidean, etc.
        public final double y;  // lon for GPS, y for Euclidean, etc.

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

    public record ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {}

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
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        ByteBuffer bb = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);

        // 1. Enforce Identifier
        if (bb.remaining() < 8) {
            throw new IllegalArgumentException("Buffer too small for .taro file header");
        }
        if (!Model.ModelBufferHasIdentifier(bb)) {
            int ident = bb.getInt(4);
            throw new IllegalArgumentException(String.format(
                    "Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X",
                    FILE_IDENTIFIER, ident));
        }

        Model model = Model.getRootAsModel(bb);
        ModelContractValidator.validateMetadataContract(model, "EdgeGraph");
        GraphTopology topology = model.topology();
        if (topology == null) {
            throw new IllegalArgumentException("GraphTopology missing");
        }

        int nodeCount = topology.nodeCount();
        int edgeCount = topology.edgeCount();
        if (nodeCount < 0 || edgeCount < 0) {
            throw new IllegalArgumentException(
                    "node_count and edge_count must be non-negative, got node_count="
                            + nodeCount + ", edge_count=" + edgeCount);
        }

        IntBuffer firstEdge = asIntBuffer(topology.firstEdgeAsByteBuffer());
        IntBuffer edgeTarget = asIntBuffer(topology.edgeTargetAsByteBuffer());
        if (firstEdge == null || edgeTarget == null) {
            throw new IllegalArgumentException("Missing required graph vectors (firstEdge or edgeTarget)");
        }
        validateVectorLength("first_edge", firstEdge.remaining(), nodeCount + 1);
        validateVectorLength("edge_target", edgeTarget.remaining(), edgeCount);
        validateCsrStructure(firstEdge, nodeCount, edgeCount);
        validateNodeIndexVector("edge_target", edgeTarget, edgeCount, nodeCount);

        FloatBuffer baseWeights = asFloatBuffer(topology.baseWeightsAsByteBuffer());
        if (baseWeights == null) {
            baseWeights = FloatBuffer.wrap(new float[edgeCount]);
        } else {
            validateVectorLength("base_weights", baseWeights.remaining(), edgeCount);
        }

        ShortBuffer edgeProfileIds = asShortBuffer(topology.edgeProfileIdAsByteBuffer());
        if (edgeProfileIds == null) {
            edgeProfileIds = ShortBuffer.wrap(new short[edgeCount]);
        } else {
            validateVectorLength("edge_profile_id", edgeProfileIds.remaining(), edgeCount);
        }

        IntBuffer edgeOrigin = asIntBuffer(topology.edgeOriginAsByteBuffer());
        // Fallback: Compute Edge Origin if missing from file (Backward Compatibility)
        if (edgeOrigin == null) {
            edgeOrigin = computeEdgeOrigins(nodeCount, edgeCount, firstEdge);
        } else {
            validateVectorLength("edge_origin", edgeOrigin.remaining(), edgeCount);
            validateNodeIndexVector("edge_origin", edgeOrigin, edgeCount, nodeCount);
        }

        ByteBuffer coordinates = copyCoordinates(topology, nodeCount);

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

            if (start < 0 || end < start || end > edgeCount) {
                throw new IllegalArgumentException(
                        "Malformed CSR span at node " + n + ": [" + start + ", " + end + ") for edge_count " + edgeCount);
            }

            for (int i = start; i < end; i++) {
                origins[i] = n;
            }
        }
        return IntBuffer.wrap(origins);
    }

    private static IntBuffer asIntBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
    }

    private static FloatBuffer asFloatBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    private static ShortBuffer asShortBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
    }

    private static ByteBuffer copyCoordinates(GraphTopology topology, int nodeCount) {
        int coordinateCount = topology.coordinatesLength();
        if (coordinateCount == 0) {
            return null;
        }
        if (coordinateCount != nodeCount) {
            throw new IllegalArgumentException(
                    "coordinates length mismatch: expected " + nodeCount + ", got " + coordinateCount);
        }

        ByteBuffer coordinates = ByteBuffer.allocateDirect(coordinateCount * COORDINATE_STRUCT_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        org.Aayush.serialization.flatbuffers.taro.model.Coordinate cursor =
                new org.Aayush.serialization.flatbuffers.taro.model.Coordinate();
        for (int i = 0; i < coordinateCount; i++) {
            topology.coordinates(cursor, i);
            coordinates.putDouble(cursor.lat());
            coordinates.putDouble(cursor.lon());
        }
        coordinates.flip();
        return coordinates;
    }

    private static void validateVectorLength(String fieldName, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    fieldName + " length mismatch: expected " + expected + ", got " + actual);
        }
    }

    private static void validateCsrStructure(IntBuffer firstEdge, int nodeCount, int edgeCount) {
        int start = firstEdge.get(0);
        if (start != 0) {
            throw new IllegalArgumentException("Malformed first_edge: first_edge[0] must be 0, got " + start);
        }

        int previous = start;
        for (int i = 1; i <= nodeCount; i++) {
            int value = firstEdge.get(i);
            if (value < 0 || value > edgeCount) {
                throw new IllegalArgumentException(
                        "Malformed first_edge: first_edge[" + i + "] out of range [0, " + edgeCount + "], got " + value);
            }
            if (value < previous) {
                throw new IllegalArgumentException(
                        "Malformed first_edge: non-monotonic at index " + i + " (" + previous + " -> " + value + ")");
            }
            previous = value;
        }

        if (firstEdge.get(nodeCount) != edgeCount) {
            throw new IllegalArgumentException(
                    "Malformed first_edge: first_edge[node_count] must equal edge_count (" + edgeCount + "), got "
                            + firstEdge.get(nodeCount));
        }
    }

    private static void validateNodeIndexVector(String fieldName, IntBuffer values, int length, int nodeCount) {
        for (int i = 0; i < length; i++) {
            int nodeId = values.get(i);
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] out of bounds: " + nodeId + " [0, " + nodeCount + ")");
            }
        }
    }
}
