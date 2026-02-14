package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Mutable per-query forward label storage for dominance-filtered edge labels.
 */
final class DominanceLabelStore {
    private final IntArrayList edgeIdByLabel = new IntArrayList();
    private final FloatArrayList gScoreByLabel = new FloatArrayList();
    private final LongArrayList arrivalByLabel = new LongArrayList();
    private final IntArrayList predecessorLabelByLabel = new IntArrayList();
    private final BooleanArrayList activeByLabel = new BooleanArrayList();

    void clear() {
        edgeIdByLabel.clear();
        gScoreByLabel.clear();
        arrivalByLabel.clear();
        predecessorLabelByLabel.clear();
        activeByLabel.clear();
    }

    int add(int edgeId, float gScore, long arrivalTicks, int predecessorLabelId) {
        int labelId = edgeIdByLabel.size();
        edgeIdByLabel.add(edgeId);
        gScoreByLabel.add(gScore);
        arrivalByLabel.add(arrivalTicks);
        predecessorLabelByLabel.add(predecessorLabelId);
        activeByLabel.add(true);
        return labelId;
    }

    int size() {
        return edgeIdByLabel.size();
    }

    int edgeId(int labelId) {
        return edgeIdByLabel.getInt(labelId);
    }

    float gScore(int labelId) {
        return gScoreByLabel.getFloat(labelId);
    }

    long arrivalTicks(int labelId) {
        return arrivalByLabel.getLong(labelId);
    }

    int predecessorLabelId(int labelId) {
        return predecessorLabelByLabel.getInt(labelId);
    }

    boolean isActive(int labelId) {
        return activeByLabel.getBoolean(labelId);
    }

    void deactivate(int labelId) {
        activeByLabel.set(labelId, false);
    }
}
