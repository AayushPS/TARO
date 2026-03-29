package org.Aayush.api;

import java.util.List;

/**
 * Caller-scoped joined telemetry export preview used by retraining orchestration.
 */
public record RetrainingTelemetryExportResponse(
        int rowCount,
        int completeRowCount,
        List<PredictionTelemetryStore.ExportRow> rows
) {
}
