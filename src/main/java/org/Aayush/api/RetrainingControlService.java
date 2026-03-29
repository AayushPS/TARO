package org.Aayush.api;

import org.Aayush.api.CallerScopedRetainedResultRegistry.ResultKind;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory control-plane service for caller-scoped telemetry export, retraining jobs, and active-model publication.
 */
@Component
public final class RetrainingControlService {
    private final PredictionTelemetryStore predictionTelemetryStore;
    private final TrainingDatasetService trainingDatasetService;
    private final AdminNotificationService adminNotificationService;
    private final Clock clock;
    private final LinkedHashMap<String, StoredRetrainingJob> jobsById = new LinkedHashMap<>();
    private final LinkedHashMap<String, PublishedModelRecord> activeModelsByCallerId = new LinkedHashMap<>();

    public RetrainingControlService(
            PredictionTelemetryStore predictionTelemetryStore,
            TrainingDatasetService trainingDatasetService,
            AdminNotificationService adminNotificationService,
            ObjectProvider<Clock> clockProvider
    ) {
        this.predictionTelemetryStore = Objects.requireNonNull(predictionTelemetryStore, "predictionTelemetryStore");
        this.trainingDatasetService = Objects.requireNonNull(trainingDatasetService, "trainingDatasetService");
        this.adminNotificationService = Objects.requireNonNull(adminNotificationService, "adminNotificationService");
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    public synchronized RetrainingTelemetryExportResponse exportTelemetry(
            String callerId,
            ResultKind resultKind,
            String topologyVersionId,
            String scenarioBundleId,
            String traitHash,
            boolean completeOnly
    ) {
        List<PredictionTelemetryStore.ExportRow> rows = exportRows(
                callerId,
                resultKind,
                topologyVersionId,
                scenarioBundleId,
                traitHash,
                completeOnly
        );
        return new RetrainingTelemetryExportResponse(
                rows.size(),
                countCompleteRows(rows),
                rows
        );
    }

    public synchronized RetrainingJobResponse createRetrainingJob(String callerId, RetrainingJobCreateRequest request) {
        Objects.requireNonNull(request, "request");
        String normalizedCallerId = requireText(callerId, "callerId");
        String trainingWindowLabel = requireText(request.trainingWindowLabel(), "trainingWindowLabel");
        List<String> selectedTraits = normalizeSelectedTraits(request.selectedTraits());
        String datasetId = normalizeOptionalText(request.datasetId());
        String targetColumn = normalizeOptionalText(request.targetColumn());
        List<String> featureColumns = normalizeFeatureColumns(request.featureColumns());
        List<PredictionTelemetryStore.ExportRow> rows = exportRows(
                normalizedCallerId,
                request.resultKind(),
                request.topologyVersionId(),
                request.scenarioBundleId(),
                request.traitHash(),
                request.completeOnly()
        );

        TrainingDatasetService.StoredDataset dataset = null;
        if (datasetId != null) {
            dataset = trainingDatasetService.requireDataset(normalizedCallerId, datasetId);
            validateTrainingColumns(dataset, targetColumn, featureColumns);
        } else if (targetColumn != null || !featureColumns.isEmpty()) {
            throw TaroApiException.badRequest("datasetId is required when target or feature columns are provided");
        }

        if (dataset == null && rows.isEmpty()) {
            throw TaroApiException.badRequest("no telemetry rows matched the retraining export filter");
        }

        String jobId = "retrain-" + UUID.randomUUID();
        Instant now = clock.instant();
        PublishedModelRecord activeModel = activeModelsByCallerId.get(normalizedCallerId);
        StoredRetrainingJob job = new StoredRetrainingJob(
                jobId,
                normalizedCallerId,
                now,
                now,
                null,
                null,
                null,
                trainingWindowLabel,
                selectedTraits,
                request.resultKind(),
                dataset == null ? null : dataset.datasetId(),
                dataset == null ? null : dataset.fileName(),
                dataset == null ? null : dataset.rowCount(),
                targetColumn,
                featureColumns,
                request.notifyOnCompletion(),
                normalizeOptionalText(request.topologyVersionId()),
                normalizeOptionalText(request.scenarioBundleId()),
                normalizeOptionalText(request.traitHash()),
                request.completeOnly(),
                rows.size(),
                countCompleteRows(rows),
                activeModel == null ? null : activeModel.activeModelId(),
                RetrainingJobStatus.QUEUED,
                null,
                null,
                null,
                null
        );
        jobsById.put(jobId, job);
        RetrainingJobResponse response = job.toResponse();
        adminNotificationService.recordTrainingJobCreated(normalizedCallerId, response);
        return response;
    }

    public synchronized RetrainingJobResponse jobStatus(String callerId, String jobId) {
        return requireJob(callerId, jobId).toResponse();
    }

    public synchronized RetrainingJobResponse startRetrainingJob(String callerId, String jobId) {
        StoredRetrainingJob job = requireJob(callerId, jobId);
        if (job.status() != RetrainingJobStatus.QUEUED) {
            throw TaroApiException.trainingJobConflict(jobId, "cannot start from state " + job.status());
        }
        Instant now = clock.instant();
        StoredRetrainingJob updated = job.withState(RetrainingJobStatus.RUNNING, now, null, null, null, null, null);
        jobsById.put(jobId, updated);
        return updated.toResponse();
    }

    public synchronized RetrainingJobResponse completeRetrainingJob(
            String callerId,
            String jobId,
            RetrainingJobCompletionRequest request
    ) {
        Objects.requireNonNull(request, "request");
        String normalizedCallerId = requireText(callerId, "callerId");
        StoredRetrainingJob job = requireJob(normalizedCallerId, jobId);
        if (job.status() != RetrainingJobStatus.QUEUED && job.status() != RetrainingJobStatus.RUNNING) {
            throw TaroApiException.trainingJobConflict(jobId, "cannot complete from state " + job.status());
        }
        Instant now = clock.instant();
        StoredRetrainingJob updated;
        if (request.succeeded()) {
            String releaseArtifactId = requireText(request.releaseArtifactId(), "releaseArtifactId");
            updated = job.withState(
                    RetrainingJobStatus.SUCCEEDED,
                    job.startedAt() == null ? now : job.startedAt(),
                    now,
                    null,
                    releaseArtifactId,
                    normalizeOptionalText(request.validationSummary()),
                    null
            );
        } else {
            String failureReason = requireText(request.failureReason(), "failureReason");
            updated = job.withState(
                    RetrainingJobStatus.FAILED,
                    job.startedAt() == null ? now : job.startedAt(),
                    now,
                    null,
                    null,
                    normalizeOptionalText(request.validationSummary()),
                    failureReason
            );
        }
        jobsById.put(jobId, updated);
        RetrainingJobResponse response = updated.toResponse();
        if (updated.notifyOnCompletion()) {
            adminNotificationService.recordTrainingJobCompleted(normalizedCallerId, response);
        }
        return response;
    }

    public synchronized PublishedServingModelResponse publishRetrainingJob(String callerId, String jobId) {
        String normalizedCallerId = requireText(callerId, "callerId");
        StoredRetrainingJob job = requireJob(normalizedCallerId, jobId);
        if (job.status() != RetrainingJobStatus.SUCCEEDED) {
            throw TaroApiException.trainingJobConflict(jobId, "cannot publish from state " + job.status());
        }
        Instant now = clock.instant();
        String activeModelId = "published-" + UUID.randomUUID();
        PublishedModelRecord model = new PublishedModelRecord(
                normalizedCallerId,
                activeModelId,
                job.jobId(),
                job.releaseArtifactId(),
                now,
                job.trainingWindowLabel(),
                job.selectedTraits(),
                job.resultKind(),
                job.datasetId(),
                job.datasetFileName(),
                job.targetColumn(),
                job.featureColumns(),
                job.exportRowCount(),
                job.completeExportRowCount()
        );
        activeModelsByCallerId.put(model.callerId(), model);
        StoredRetrainingJob updated = job.withState(
                RetrainingJobStatus.PUBLISHED,
                job.startedAt(),
                job.completedAt(),
                now,
                job.releaseArtifactId(),
                job.validationSummary(),
                job.failureReason()
        ).withPublishedModelId(activeModelId);
        jobsById.put(jobId, updated);
        PublishedServingModelResponse response = model.toResponse();
        adminNotificationService.recordModelPublished(normalizedCallerId, response);
        return response;
    }

    public synchronized PublishedServingModelResponse activeModel(String callerId) {
        String normalizedCallerId = requireText(callerId, "callerId");
        PublishedModelRecord model = activeModelsByCallerId.get(normalizedCallerId);
        if (model == null) {
            throw TaroApiException.activeModelNotFound(normalizedCallerId);
        }
        return model.toResponse();
    }

    synchronized void clear() {
        jobsById.clear();
        activeModelsByCallerId.clear();
    }

    private List<PredictionTelemetryStore.ExportRow> exportRows(
            String callerId,
            ResultKind resultKind,
            String topologyVersionId,
            String scenarioBundleId,
            String traitHash,
            boolean completeOnly
    ) {
        return predictionTelemetryStore.exportRows(
                new PredictionTelemetryStore.ExportFilter(
                        requireText(callerId, "callerId"),
                        resultKind,
                        normalizeOptionalText(topologyVersionId),
                        normalizeOptionalText(scenarioBundleId),
                        normalizeOptionalText(traitHash),
                        completeOnly
                )
        );
    }

    private StoredRetrainingJob requireJob(String callerId, String jobId) {
        String normalizedCallerId = requireText(callerId, "callerId");
        String normalizedJobId = requireText(jobId, "jobId");
        StoredRetrainingJob job = jobsById.get(normalizedJobId);
        if (job == null || !normalizedCallerId.equals(job.callerId())) {
            throw TaroApiException.trainingJobNotFound(normalizedJobId);
        }
        return job;
    }

    private static int countCompleteRows(List<PredictionTelemetryStore.ExportRow> rows) {
        int count = 0;
        for (PredictionTelemetryStore.ExportRow row : rows) {
            if (row.complete()) {
                count++;
            }
        }
        return count;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw TaroApiException.badRequest(fieldName + " must be non-blank");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> normalizeSelectedTraits(List<String> selectedTraits) {
        if (selectedTraits == null || selectedTraits.isEmpty()) {
            throw TaroApiException.badRequest("selectedTraits must contain at least one trait");
        }
        ArrayList<String> normalized = new ArrayList<>();
        for (String selectedTrait : selectedTraits) {
            String value = requireText(selectedTrait, "selectedTraits");
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeFeatureColumns(List<String> featureColumns) {
        if (featureColumns == null || featureColumns.isEmpty()) {
            return List.of();
        }
        ArrayList<String> normalized = new ArrayList<>();
        for (String featureColumn : featureColumns) {
            String value = requireText(featureColumn, "featureColumns");
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static void validateTrainingColumns(
            TrainingDatasetService.StoredDataset dataset,
            String targetColumn,
            List<String> featureColumns
    ) {
        if (targetColumn == null) {
            throw TaroApiException.badRequest("targetColumn is required when datasetId is provided");
        }
        if (!dataset.headers().contains(targetColumn)) {
            throw TaroApiException.badRequest("targetColumn is not present in dataset headers: " + targetColumn);
        }
        if (featureColumns.isEmpty()) {
            throw TaroApiException.badRequest("featureColumns must contain at least one column when datasetId is provided");
        }
        for (String featureColumn : featureColumns) {
            if (!dataset.headers().contains(featureColumn)) {
                throw TaroApiException.badRequest("featureColumn is not present in dataset headers: " + featureColumn);
            }
            if (featureColumn.equals(targetColumn)) {
                throw TaroApiException.badRequest("featureColumns must not include the targetColumn");
            }
        }
    }

    private record StoredRetrainingJob(
            String jobId,
            String callerId,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant completedAt,
            Instant publishedAt,
            String trainingWindowLabel,
            List<String> selectedTraits,
            ResultKind resultKind,
            String datasetId,
            String datasetFileName,
            Integer datasetRowCount,
            String targetColumn,
            List<String> featureColumns,
            boolean notifyOnCompletion,
            String topologyVersionId,
            String scenarioBundleId,
            String traitHash,
            boolean completeOnly,
            int exportRowCount,
            int completeExportRowCount,
            String basePublishedModelId,
            RetrainingJobStatus status,
            String releaseArtifactId,
            String validationSummary,
            String failureReason,
            String publishedModelId
    ) {
        private RetrainingJobResponse toResponse() {
            return new RetrainingJobResponse(
                    jobId,
                    status,
                    createdAt,
                    updatedAt,
                    startedAt,
                    completedAt,
                    publishedAt,
                    trainingWindowLabel,
                    selectedTraits,
                    resultKind,
                    datasetId,
                    datasetFileName,
                    datasetRowCount,
                    targetColumn,
                    featureColumns,
                    notifyOnCompletion,
                    topologyVersionId,
                    scenarioBundleId,
                    traitHash,
                    completeOnly,
                    exportRowCount,
                    completeExportRowCount,
                    basePublishedModelId,
                    releaseArtifactId,
                    validationSummary,
                    failureReason,
                    publishedModelId
            );
        }

        private StoredRetrainingJob withState(
                RetrainingJobStatus nextStatus,
                Instant nextStartedAt,
                Instant nextCompletedAt,
                Instant nextPublishedAt,
                String nextReleaseArtifactId,
                String nextValidationSummary,
                String nextFailureReason
        ) {
            return new StoredRetrainingJob(
                    jobId,
                    callerId,
                    createdAt,
                    nextPublishedAt != null ? nextPublishedAt : (nextCompletedAt != null ? nextCompletedAt : nextStartedAt),
                    nextStartedAt,
                    nextCompletedAt,
                    nextPublishedAt,
                    trainingWindowLabel,
                    selectedTraits,
                    resultKind,
                    datasetId,
                    datasetFileName,
                    datasetRowCount,
                    targetColumn,
                    featureColumns,
                    notifyOnCompletion,
                    topologyVersionId,
                    scenarioBundleId,
                    traitHash,
                    completeOnly,
                    exportRowCount,
                    completeExportRowCount,
                    basePublishedModelId,
                    nextStatus,
                    nextReleaseArtifactId,
                    nextValidationSummary,
                    nextFailureReason,
                    publishedModelId
            );
        }

        private StoredRetrainingJob withPublishedModelId(String nextPublishedModelId) {
            return new StoredRetrainingJob(
                    jobId,
                    callerId,
                    createdAt,
                    publishedAt == null ? updatedAt : publishedAt,
                    startedAt,
                    completedAt,
                    publishedAt,
                    trainingWindowLabel,
                    selectedTraits,
                    resultKind,
                    datasetId,
                    datasetFileName,
                    datasetRowCount,
                    targetColumn,
                    featureColumns,
                    notifyOnCompletion,
                    topologyVersionId,
                    scenarioBundleId,
                    traitHash,
                    completeOnly,
                    exportRowCount,
                    completeExportRowCount,
                    basePublishedModelId,
                    status,
                    releaseArtifactId,
                    validationSummary,
                    failureReason,
                    nextPublishedModelId
            );
        }
    }

    private record PublishedModelRecord(
            String callerId,
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
        private PublishedServingModelResponse toResponse() {
            return new PublishedServingModelResponse(
                    activeModelId,
                    sourceTrainingJobId,
                    releaseArtifactId,
                    publishedAt,
                    trainingWindowLabel,
                    selectedTraits,
                    resultKind,
                    datasetId,
                    datasetFileName,
                    targetColumn,
                    featureColumns,
                    exportRowCount,
                    completeExportRowCount
            );
        }
    }
}
