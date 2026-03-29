package org.Aayush.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Stage F2 versioned feedback-ingestion request contract.
 * Satisfies closure criterion: eventual outcome telemetry can be joined back to served predictions through an explicit caller-facing payload.
 */
public record PredictionFeedbackRequest(
        @NotNull OutcomeStatus outcomeStatus,
        @PositiveOrZero Long observedAtTicks,
        @PositiveOrZero Long observedArrivalTicks,
        @PositiveOrZero Double observedCostSeconds,
        @Positive Integer observationCount
) {
    /**
     * Stage F2 outcome-status surface for complete vs incomplete feedback updates.
     * Satisfies closure criterion: feedback ingestion distinguishes partial telemetry from complete outcome records explicitly.
     */
    public enum OutcomeStatus {
        PARTIAL,
        COMPLETE
    }
}
