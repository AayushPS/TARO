package org.Aayush.routing.core;

/**
 * Internal deterministic telemetry for one matrix execution.
 *
 * <p>This type is intentionally internal and not part of public API contracts.</p>
 */
final class MatrixExecutionStats {
    private final long requestWorkStates;
    private final long requestSettledStates;
    private final int requestLabelPeak;
    private final int requestFrontierPeak;
    private final int[] rowWorkStates;
    private final int[] rowSettledStates;
    private final int[] rowLabelPeaks;
    private final int[] rowFrontierPeaks;

    private MatrixExecutionStats(
            long requestWorkStates,
            long requestSettledStates,
            int requestLabelPeak,
            int requestFrontierPeak,
            int[] rowWorkStates,
            int[] rowSettledStates,
            int[] rowLabelPeaks,
            int[] rowFrontierPeaks
    ) {
        this.requestWorkStates = requestWorkStates;
        this.requestSettledStates = requestSettledStates;
        this.requestLabelPeak = requestLabelPeak;
        this.requestFrontierPeak = requestFrontierPeak;
        this.rowWorkStates = rowWorkStates.clone();
        this.rowSettledStates = rowSettledStates.clone();
        this.rowLabelPeaks = rowLabelPeaks.clone();
        this.rowFrontierPeaks = rowFrontierPeaks.clone();
    }

    static MatrixExecutionStats of(
            long requestWorkStates,
            long requestSettledStates,
            int requestLabelPeak,
            int requestFrontierPeak,
            int[] rowWorkStates,
            int[] rowSettledStates,
            int[] rowLabelPeaks,
            int[] rowFrontierPeaks
    ) {
        return new MatrixExecutionStats(
                requestWorkStates,
                requestSettledStates,
                requestLabelPeak,
                requestFrontierPeak,
                rowWorkStates,
                rowSettledStates,
                rowLabelPeaks,
                rowFrontierPeaks
        );
    }

    static MatrixExecutionStats empty(int sourceRows) {
        int size = Math.max(0, sourceRows);
        int[] zeros = new int[size];
        return new MatrixExecutionStats(0L, 0L, 0, 0, zeros, zeros, zeros, zeros);
    }

    long requestWorkStates() {
        return requestWorkStates;
    }

    long requestSettledStates() {
        return requestSettledStates;
    }

    int requestLabelPeak() {
        return requestLabelPeak;
    }

    int requestFrontierPeak() {
        return requestFrontierPeak;
    }

    int[] rowWorkStates() {
        return rowWorkStates.clone();
    }

    int[] rowSettledStates() {
        return rowSettledStates.clone();
    }

    int[] rowLabelPeaks() {
        return rowLabelPeaks.clone();
    }

    int[] rowFrontierPeaks() {
        return rowFrontierPeaks.clone();
    }
}
