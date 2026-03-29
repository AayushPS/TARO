package org.Aayush.api;

import java.time.Instant;
import java.util.List;

/**
 * Caller-scoped uploaded CSV dataset metadata used by training configuration.
 */
public record TrainingDatasetResponse(
        String datasetId,
        String fileName,
        Instant uploadedAt,
        int rowCount,
        int columnCount,
        List<String> headers,
        List<List<String>> sampleRows,
        String sha256
) {
}
