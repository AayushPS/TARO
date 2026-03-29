package org.Aayush.api;

import java.time.Instant;

/**
 * Stage F2 versioned feedback-ingestion response contract.
 * Satisfies closure criterion: callers receive explicit confirmation that outcome telemetry was joined to a stable served-prediction lineage record.
 */
public record PredictionFeedbackResponse(
        CallerScopedRetainedResultRegistry.ResultKind resultKind,
        String resultSetId,
        PredictionFeedbackRequest.OutcomeStatus outcomeStatus,
        boolean complete,
        Instant feedbackRecordedAt,
        String topologyVersionId,
        String scenarioBundleId
) {
}
