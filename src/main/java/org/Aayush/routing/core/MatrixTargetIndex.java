package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Deduplicated target index used by the matrix planner.
 *
 * <p>Maps original matrix columns to a compact unique-target domain so one row
 * expansion can satisfy duplicate targets without extra search work.</p>
 */
final class MatrixTargetIndex {
    static final int NOT_FOUND = -1;

    private final int[] uniqueTargetNodeIds;
    private final int[] columnToUniqueIndex;
    private final Int2IntOpenHashMap uniqueIndexByNode;

    /**
     * Builds deduplicated target mapping for one request.
     */
    MatrixTargetIndex(int[] targetNodeIds) {
        this.uniqueIndexByNode = new Int2IntOpenHashMap();
        uniqueIndexByNode.defaultReturnValue(NOT_FOUND);
        this.columnToUniqueIndex = new int[targetNodeIds.length];

        IntArrayList uniqueTargets = new IntArrayList();
        for (int column = 0; column < targetNodeIds.length; column++) {
            int nodeId = targetNodeIds[column];
            int uniqueIndex = uniqueIndexByNode.get(nodeId);
            if (uniqueIndex == NOT_FOUND) {
                uniqueIndex = uniqueTargets.size();
                uniqueTargets.add(nodeId);
                uniqueIndexByNode.put(nodeId, uniqueIndex);
            }
            columnToUniqueIndex[column] = uniqueIndex;
        }
        this.uniqueTargetNodeIds = uniqueTargets.toIntArray();
    }

    /**
     * Number of unique targets after deduplication.
     */
    int uniqueTargetCount() {
        return uniqueTargetNodeIds.length;
    }

    /**
     * Number of columns in original request target list.
     */
    int columnCount() {
        return columnToUniqueIndex.length;
    }

    /**
     * Returns deduplicated-target index for a node id, or {@link #NOT_FOUND}.
     */
    int uniqueIndexOfNode(int nodeId) {
        return uniqueIndexByNode.get(nodeId);
    }

    /**
     * Returns deduplicated-target index for a column in original target list.
     */
    int uniqueIndexForColumn(int columnIndex) {
        return columnToUniqueIndex[columnIndex];
    }
}
