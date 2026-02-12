package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.List;

/**
 * Client-facing matrix response.
 */
@Value
@Builder
public class MatrixResponse {
    List<String> sourceExternalIds;
    List<String> targetExternalIds;
    boolean[][] reachable;
    float[][] totalCosts;
    long[][] arrivalTicks;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
    String implementationNote;

    public boolean[][] getReachable() {
        return deepCopy(reachable);
    }

    public float[][] getTotalCosts() {
        return deepCopy(totalCosts);
    }

    public long[][] getArrivalTicks() {
        return deepCopy(arrivalTicks);
    }

    private static boolean[][] deepCopy(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private static float[][] deepCopy(float[][] source) {
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private static long[][] deepCopy(long[][] source) {
        long[][] copy = new long[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
