package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.List;

/**
 * Client-facing many-to-many routing response.
 *
 * <p>Array-valued fields are exposed through defensive-copy getters to keep the
 * value object immutable.</p>
 */
@Value
@Builder
public class MatrixResponse {
    /** External source ids that map to matrix rows. */
    List<String> sourceExternalIds;
    /** External target ids that map to matrix columns. */
    List<String> targetExternalIds;
    /** Reachability matrix per source/target pair. */
    boolean[][] reachable;
    /** Total cost matrix per source/target pair. */
    float[][] totalCosts;
    /** Arrival tick matrix per source/target pair. */
    long[][] arrivalTicks;
    /** Search algorithm used to compute the matrix. */
    RoutingAlgorithm algorithm;
    /** Heuristic mode used during matrix computation. */
    HeuristicType heuristicType;
    /** Planner implementation note for observability and execution-mode tracing. */
    String implementationNote;

    /**
     * Returns a defensive copy of the reachability matrix.
     */
    public boolean[][] getReachable() {
        return deepCopy(reachable);
    }

    /**
     * Returns a defensive copy of the total-cost matrix.
     */
    public float[][] getTotalCosts() {
        return deepCopy(totalCosts);
    }

    /**
     * Returns a defensive copy of the arrival-ticks matrix.
     */
    public long[][] getArrivalTicks() {
        return deepCopy(arrivalTicks);
    }

    /**
     * Deep-copies a boolean matrix.
     */
    private static boolean[][] deepCopy(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /**
     * Deep-copies a float matrix.
     */
    private static float[][] deepCopy(float[][] source) {
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /**
     * Deep-copies a long matrix.
     */
    private static long[][] deepCopy(long[][] source) {
        long[][] copy = new long[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
