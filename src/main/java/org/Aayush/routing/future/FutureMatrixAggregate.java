package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Aggregated future-aware matrix summary across one scenario bundle.
 */
@Value
public class FutureMatrixAggregate {
    List<String> sourceExternalIds;
    List<String> targetExternalIds;
    double[][] reachabilityProbabilities;
    float[][] expectedCosts;
    float[][] p50Costs;
    float[][] p90Costs;
    float[][] minCosts;
    float[][] maxCosts;
    long[][] minArrivalTicks;
    long[][] maxArrivalTicks;
    String aggregationNote;

    @Builder
    public FutureMatrixAggregate(
            List<String> sourceExternalIds,
            List<String> targetExternalIds,
            double[][] reachabilityProbabilities,
            float[][] expectedCosts,
            float[][] p50Costs,
            float[][] p90Costs,
            float[][] minCosts,
            float[][] maxCosts,
            long[][] minArrivalTicks,
            long[][] maxArrivalTicks,
            String aggregationNote
    ) {
        this.sourceExternalIds = List.copyOf(sourceExternalIds);
        this.targetExternalIds = List.copyOf(targetExternalIds);
        this.reachabilityProbabilities = deepCopy(reachabilityProbabilities);
        this.expectedCosts = deepCopy(expectedCosts);
        this.p50Costs = deepCopy(p50Costs);
        this.p90Costs = deepCopy(p90Costs);
        this.minCosts = deepCopy(minCosts);
        this.maxCosts = deepCopy(maxCosts);
        this.minArrivalTicks = deepCopy(minArrivalTicks);
        this.maxArrivalTicks = deepCopy(maxArrivalTicks);
        this.aggregationNote = aggregationNote;
    }

    public double[][] getReachabilityProbabilities() {
        return deepCopy(reachabilityProbabilities);
    }

    public float[][] getExpectedCosts() {
        return deepCopy(expectedCosts);
    }

    public float[][] getP50Costs() {
        return deepCopy(p50Costs);
    }

    public float[][] getP90Costs() {
        return deepCopy(p90Costs);
    }

    public float[][] getMinCosts() {
        return deepCopy(minCosts);
    }

    public float[][] getMaxCosts() {
        return deepCopy(maxCosts);
    }

    public long[][] getMinArrivalTicks() {
        return deepCopy(minArrivalTicks);
    }

    public long[][] getMaxArrivalTicks() {
        return deepCopy(maxArrivalTicks);
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
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
