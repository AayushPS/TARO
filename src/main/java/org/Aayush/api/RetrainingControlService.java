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
    private final Clock clock;
    private final LinkedHashMap<String, StoredRetrainingJob> jobsById = new LinkedHashMap<>();
    private final LinkedHashMap<String, PublishedModelRecord> activeModelsByCallerId = new LinkedHashMap<>();

    public RetrainingControlService(
            PredictionTelemetryStore predictionTelemetryStore,
            ObjectProvider<Clock> clockProvider
    ) {
        this.predictionTelemetryStore = Objects.requireNonNull(predictionTelemetryStore, "predictionTelemetryStore");
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
        List<PredictionTelemetryStore.ExportRow> rows = exportRows(
                normalizedCallerId,
                request.resultKind(),
                request.topologyVersionId(),
                request.scenarioBundleId(),
                request.traitHash(),
                request.completeOnly()
        );
        if (rows.isEmpty()) {
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
        return job.toResponse();
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
        StoredRetrainingJob job = requireJob(callerId, jobId);
        if (job.status() != RetrainingJobStatus.QUEUED && job.status() != RetrainingJobStatus.RUNNING) {
            throw TaroApiException.trainingJobConflict(jobId, "cannot complete from state " + job.status());
        }
        Instant now = clock.instant();
        if (request.succeeded()) {
            String releaseArtifactId = requireText(request.releaseArtifactId(), "releaseArtifactId");
            StoredRetrainingJob updated = job.withState(
                    RetrainingJobStatus.SUCCEEDED,
                    job.startedAt() == null ? now : job.startedAt(),
                    now,
                    null,
                    releaseArtifactId,
                    normalizeOptionalText(request.validationSummary()),
                    null
            );
            jobsById.put(jobId, updated);
            return updated.toResponse();
        }
        String failureReason = requireText(request.failureReason(), "failureReason");
        StoredRetrainingJob updated = job.withState(
                RetrainingJobStatus.FAILED,
                job.startedAt() == null ? now : job.startedAt(),
                now,
                null,
                null,
                normalizeOptionalText(request.validationSummary()),
                failureReason
        );
        jobsById.put(jobId, updated);
        return updated.toResponse();
    }

    public synchronized PublishedServingModelResponse publishRetrainingJob(String callerId, String jobId) {
        StoredRetrainingJob job = requireJob(callerId, jobId);
        if (job.status() != RetrainingJobStatus.SUCCEEDED) {
            throw TaroApiException.trainingJobConflict(jobId, "cannot publish from state " + job.status());
        }
        Instant now = clock.instant();
        String activeModelId = "published-" + UUID.randomUUID();
        PublishedModelRecord model = new PublishedModelRecord(
                requireText(callerId, "callerId"),
                activeModelId,
                job.jobId(),
                job.releaseArtifactId(),
                now,
                job.trainingWindowLabel(),
                job.selectedTraits(),
                job.resultKind(),
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
        return model.toResponse();
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
                    exportRowCount,
                    completeExportRowCount
            );
        }
    }
}
