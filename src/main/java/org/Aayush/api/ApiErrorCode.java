package org.Aayush.api;

/**
 * Stage F1 stable error codes for the TARO HTTP/API surface.
 * Satisfies closure criterion: API error posture is explicit for expired, incompatible, or unauthorized retained results.
 */
public enum ApiErrorCode {
    INVALID_REQUEST,
    INVALID_RESULT_SET_ID,
    UNAUTHORIZED_RESULT_ACCESS,
    RESULT_EXPIRED,
    RESULT_INCOMPATIBLE,
    RESULT_EVICTED,
    SERVICE_UNAVAILABLE,
    TRAINING_DATASET_NOT_FOUND,
    TRAINING_JOB_NOT_FOUND,
    TRAINING_JOB_CONFLICT,
    ACTIVE_MODEL_NOT_FOUND
}
