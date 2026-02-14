package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Deduplicated target index used by Stage 14 matrix planner.
 */
final class MatrixTargetIndex {
    static final int NOT_FOUND = -1;

    private final int[] uniqueTargetNodeIds;
    private final int[] columnToUniqueIndex;
    private final Int2IntOpenHashMap uniqueIndexByNode;

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

    int uniqueTargetCount() {
        return uniqueTargetNodeIds.length;
    }

    int columnCount() {
        return columnToUniqueIndex.length;
    }

    int uniqueIndexOfNode(int nodeId) {
        return uniqueIndexByNode.get(nodeId);
    }

    int uniqueIndexForColumn(int columnIndex) {
        return columnToUniqueIndex[columnIndex];
    }
}
