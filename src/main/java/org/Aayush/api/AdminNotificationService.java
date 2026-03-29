package org.Aayush.api;

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
 * In-memory caller-scoped admin notification feed.
 */
@Component
public final class AdminNotificationService {
    private static final int MAX_NOTIFICATIONS_PER_CALLER = 64;

    private final Clock clock;
    private final LinkedHashMap<String, ArrayList<StoredNotification>> notificationsByCaller = new LinkedHashMap<>();

    public AdminNotificationService(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    public synchronized List<AdminNotificationResponse> notifications(String callerId, int limit) {
        String normalizedCallerId = requireText(callerId, "callerId");
        int boundedLimit = Math.max(1, limit);
        ArrayList<StoredNotification> notifications = notificationsByCaller.get(normalizedCallerId);
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, notifications.size() - boundedLimit);
        List<StoredNotification> slice = notifications.subList(fromIndex, notifications.size());
        ArrayList<AdminNotificationResponse> responses = new ArrayList<>();
        for (int index = slice.size() - 1; index >= 0; index--) {
            responses.add(slice.get(index).toResponse());
        }
        return List.copyOf(responses);
    }

    public synchronized void recordDatasetUploaded(String callerId, TrainingDatasetResponse dataset) {
        Objects.requireNonNull(dataset, "dataset");
        append(callerId,
                "DATASET_UPLOADED",
                "Dataset uploaded",
                dataset.fileName() + " with " + dataset.rowCount() + " rows is ready for training.",
                dataset.datasetId(),
                null);
    }

    public synchronized void recordTrainingJobCreated(String callerId, RetrainingJobResponse job) {
        Objects.requireNonNull(job, "job");
        append(callerId,
                "TRAINING_JOB_CREATED",
                "Training job created",
                job.jobId() + " was created for " + job.trainingWindowLabel() + ".",
                job.datasetId(),
                job.jobId());
    }

    public synchronized void recordTrainingJobCompleted(String callerId, RetrainingJobResponse job) {
        Objects.requireNonNull(job, "job");
        String detail = job.status() == RetrainingJobStatus.SUCCEEDED
                ? job.jobId() + " completed successfully."
                : job.jobId() + " failed: " + job.failureReason();
        append(callerId,
                "TRAINING_JOB_COMPLETED",
                "Training job finished",
                detail,
                job.datasetId(),
                job.jobId());
    }

    public synchronized void recordModelPublished(String callerId, PublishedServingModelResponse model) {
        Objects.requireNonNull(model, "model");
        append(callerId,
                "MODEL_PUBLISHED",
                "Model published",
                model.activeModelId() + " is now active for routing queries.",
                model.datasetId(),
                model.sourceTrainingJobId());
    }

    synchronized void clear() {
        notificationsByCaller.clear();
    }

    private void append(
            String callerId,
            String type,
            String title,
            String detail,
            String relatedDatasetId,
            String relatedJobId
    ) {
        String normalizedCallerId = requireText(callerId, "callerId");
        ArrayList<StoredNotification> notifications =
                notificationsByCaller.computeIfAbsent(normalizedCallerId, ignored -> new ArrayList<>());
        notifications.add(new StoredNotification(
                "notification-" + UUID.randomUUID(),
                type,
                title,
                detail,
                clock.instant(),
                relatedDatasetId,
                relatedJobId
        ));
        while (notifications.size() > MAX_NOTIFICATIONS_PER_CALLER) {
            notifications.removeFirst();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw TaroApiException.badRequest(fieldName + " must be non-blank");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw TaroApiException.badRequest(fieldName + " must be non-blank");
        }
        return trimmed;
    }

    private record StoredNotification(
            String notificationId,
            String type,
            String title,
            String detail,
            Instant createdAt,
            String relatedDatasetId,
            String relatedJobId
    ) {
        private AdminNotificationResponse toResponse() {
            return new AdminNotificationResponse(
                    notificationId,
                    type,
                    title,
                    detail,
                    createdAt,
                    relatedDatasetId,
                    relatedJobId
            );
        }
    }
}
