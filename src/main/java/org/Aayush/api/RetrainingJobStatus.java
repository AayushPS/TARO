package org.Aayush.api;

/**
 * Control-plane retraining job lifecycle states.
 */
public enum RetrainingJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PUBLISHED
}
