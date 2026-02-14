package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Thread-confined reusable row buffers for Stage 14 matrix execution.
 */
final class MatrixQueryContext {
    private static final float INF = Float.POSITIVE_INFINITY;

    private final DominanceLabelStore labelStore = new DominanceLabelStore();
    private final IntArrayList touchedEdges = new IntArrayList();
    private final Int2ObjectOpenHashMap<IntArrayList> activeLabelsByEdge = new Int2ObjectOpenHashMap<>();
    private final PriorityQueue<ForwardFrontierState> frontier = new PriorityQueue<>();

    private float[] bestCostByTarget = new float[0];
    private long[] bestArrivalByTarget = new long[0];
    private boolean[] reachedByTarget = new boolean[0];
    private int targetCount;
    private int unresolvedTargets;

    private int rowSettledStates;
    private int rowWorkStates;
    private int rowLabelPeak;
    private int rowFrontierPeak;

    /**
     * Resets all per-row mutable structures and initializes target buffers.
     */
    void resetForRow(int uniqueTargetCount) {
        labelStore.clear();
        for (int i = 0; i < touchedEdges.size(); i++) {
            int edgeId = touchedEdges.getInt(i);
            IntArrayList labels = activeLabelsByEdge.get(edgeId);
            if (labels != null) {
                labels.clear();
            }
        }
        touchedEdges.clear();
        frontier.clear();

        ensureTargetCapacity(uniqueTargetCount);
        Arrays.fill(bestCostByTarget, 0, uniqueTargetCount, INF);
        Arrays.fill(bestArrivalByTarget, 0, uniqueTargetCount, Long.MAX_VALUE);
        Arrays.fill(reachedByTarget, 0, uniqueTargetCount, false);
        this.targetCount = uniqueTargetCount;
        this.unresolvedTargets = uniqueTargetCount;

        rowSettledStates = 0;
        rowWorkStates = 0;
        rowLabelPeak = 0;
        rowFrontierPeak = 0;
    }

    DominanceLabelStore labelStore() {
        return labelStore;
    }

    PriorityQueue<ForwardFrontierState> frontier() {
        return frontier;
    }

    IntArrayList activeLabelsForEdge(int edgeId) {
        IntArrayList labels = activeLabelsByEdge.get(edgeId);
        if (labels == null) {
            labels = new IntArrayList();
            activeLabelsByEdge.put(edgeId, labels);
        }
        if (labels.isEmpty()) {
            touchedEdges.add(edgeId);
        }
        return labels;
    }

    boolean updateTargetBest(int uniqueTargetIndex, float candidateCost, long candidateArrival) {
        float currentCost = bestCostByTarget[uniqueTargetIndex];
        long currentArrival = bestArrivalByTarget[uniqueTargetIndex];
        if (!isBetter(candidateCost, candidateArrival, currentCost, currentArrival)) {
            return false;
        }
        if (!reachedByTarget[uniqueTargetIndex]) {
            reachedByTarget[uniqueTargetIndex] = true;
            unresolvedTargets--;
        }
        bestCostByTarget[uniqueTargetIndex] = candidateCost;
        bestArrivalByTarget[uniqueTargetIndex] = candidateArrival;
        return true;
    }

    boolean isTargetReached(int uniqueTargetIndex) {
        return reachedByTarget[uniqueTargetIndex];
    }

    float targetBestCost(int uniqueTargetIndex) {
        return bestCostByTarget[uniqueTargetIndex];
    }

    long targetBestArrival(int uniqueTargetIndex) {
        return bestArrivalByTarget[uniqueTargetIndex];
    }

    int unresolvedTargets() {
        return unresolvedTargets;
    }

    double maxResolvedTargetCost() {
        double max = 0.0d;
        for (int i = 0; i < targetCount; i++) {
            if (reachedByTarget[i]) {
                max = Math.max(max, bestCostByTarget[i]);
            }
        }
        return max;
    }

    void observeLabelCount() {
        rowLabelPeak = Math.max(rowLabelPeak, labelStore.size());
    }

    void observeFrontierSize() {
        rowFrontierPeak = Math.max(rowFrontierPeak, frontier.size());
    }

    int incrementRowWorkStates() {
        rowWorkStates++;
        return rowWorkStates;
    }

    void incrementRowSettledStates() {
        rowSettledStates++;
    }

    int rowWorkStates() {
        return rowWorkStates;
    }

    int rowSettledStates() {
        return rowSettledStates;
    }

    int rowLabelPeak() {
        return rowLabelPeak;
    }

    int rowFrontierPeak() {
        return rowFrontierPeak;
    }

    private void ensureTargetCapacity(int uniqueTargetCount) {
        if (bestCostByTarget.length < uniqueTargetCount) {
            bestCostByTarget = new float[uniqueTargetCount];
            bestArrivalByTarget = new long[uniqueTargetCount];
            reachedByTarget = new boolean[uniqueTargetCount];
        }
    }

    private static boolean isBetter(float newCost, long newArrival, float currentCost, long currentArrival) {
        if (newCost < currentCost) {
            return true;
        }
        return Float.compare(newCost, currentCost) == 0 && newArrival < currentArrival;
    }
}
