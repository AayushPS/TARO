package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Mutable per-query forward label storage for dominance-filtered edge labels.
 *
 * <p>Label ids are append-only indexes and remain stable for predecessor chains.
 * Dominance filters deactivate labels in-place via {@link #deactivate(int)}.</p>
 */
final class DominanceLabelStore {
    private final IntArrayList edgeIdByLabel = new IntArrayList();
    private final FloatArrayList gScoreByLabel = new FloatArrayList();
    private final LongArrayList arrivalByLabel = new LongArrayList();
    private final IntArrayList predecessorLabelByLabel = new IntArrayList();
    private final BooleanArrayList activeByLabel = new BooleanArrayList();

    /**
     * Clears all stored labels for query reuse.
     */
    void clear() {
        edgeIdByLabel.clear();
        gScoreByLabel.clear();
        arrivalByLabel.clear();
        predecessorLabelByLabel.clear();
        activeByLabel.clear();
    }

    /**
     * Appends one label and returns its stable label id.
     */
    int add(int edgeId, float gScore, long arrivalTicks, int predecessorLabelId) {
        int labelId = edgeIdByLabel.size();
        edgeIdByLabel.add(edgeId);
        gScoreByLabel.add(gScore);
        arrivalByLabel.add(arrivalTicks);
        predecessorLabelByLabel.add(predecessorLabelId);
        activeByLabel.add(true);
        return labelId;
    }

    /**
     * Returns total number of labels allocated in current query.
     */
    int size() {
        return edgeIdByLabel.size();
    }

    /**
     * Returns edge id for given label id.
     */
    int edgeId(int labelId) {
        return edgeIdByLabel.getInt(labelId);
    }

    /**
     * Returns cumulative route cost for given label id.
     */
    float gScore(int labelId) {
        return gScoreByLabel.getFloat(labelId);
    }

    /**
     * Returns arrival tick for given label id.
     */
    long arrivalTicks(int labelId) {
        return arrivalByLabel.getLong(labelId);
    }

    /**
     * Returns predecessor label id for path reconstruction.
     */
    int predecessorLabelId(int labelId) {
        return predecessorLabelByLabel.getInt(labelId);
    }

    /**
     * Returns whether label is still active in dominance set.
     */
    boolean isActive(int labelId) {
        return activeByLabel.getBoolean(labelId);
    }

    /**
     * Marks label as inactive without removing it from arrays.
     */
    void deactivate(int labelId) {
        activeByLabel.set(labelId, false);
    }
}
