package org.Aayush.api;

/**
 * Runner callback contract for finishing one retraining job.
 */
public record RetrainingJobCompletionRequest(
        boolean succeeded,
        String releaseArtifactId,
        String validationSummary,
        String failureReason
) {
}
