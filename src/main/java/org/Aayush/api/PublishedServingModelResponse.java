package org.Aayush.api;

import org.Aayush.api.CallerScopedRetainedResultRegistry.ResultKind;

import java.time.Instant;
import java.util.List;

/**
 * Caller-scoped active published model metadata.
 */
public record PublishedServingModelResponse(
        String activeModelId,
        String sourceTrainingJobId,
        String releaseArtifactId,
        Instant publishedAt,
        String trainingWindowLabel,
        List<String> selectedTraits,
        ResultKind resultKind,
        String datasetId,
        String datasetFileName,
        String targetColumn,
        List<String> featureColumns,
        int exportRowCount,
        int completeExportRowCount
) {
}
