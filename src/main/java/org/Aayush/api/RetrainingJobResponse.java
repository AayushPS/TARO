package org.Aayush.api;

import org.Aayush.api.CallerScopedRetainedResultRegistry.ResultKind;

import java.time.Instant;
import java.util.List;

/**
 * Caller-scoped retraining job status view.
 */
public record RetrainingJobResponse(
        String jobId,
        RetrainingJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        Instant publishedAt,
        String trainingWindowLabel,
        List<String> selectedTraits,
        ResultKind resultKind,
        String topologyVersionId,
        String scenarioBundleId,
        String traitHash,
        boolean completeOnly,
        int exportRowCount,
        int completeExportRowCount,
        String basePublishedModelId,
        String releaseArtifactId,
        String validationSummary,
        String failureReason,
        String publishedModelId
) {
}
