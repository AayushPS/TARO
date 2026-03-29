package org.Aayush.api;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * Stage F1 API exception carrying a stable error code and HTTP status.
 * Satisfies closure criterion: API error posture is explicit for expired, incompatible, or unauthorized retained results.
 */
public final class TaroApiException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final ApiErrorCode errorCode;

    private TaroApiException(HttpStatus httpStatus, ApiErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Stage F1 returns a versioned invalid-request failure.
     * Satisfies closure criterion: request validation and error contracts are explicit.
     */
    public static TaroApiException badRequest(String message) {
        return new TaroApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, message);
    }

    /**
     * Stage F1 returns an explicit invalid-result-set failure.
     * Satisfies closure criterion: request validation and error contracts are explicit.
     */
    public static TaroApiException invalidResultSetId(String resultSetId) {
        return new TaroApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.INVALID_RESULT_SET_ID,
                "unknown resultSetId: " + resultSetId
        );
    }

    /**
     * Stage F1 returns an explicit unauthorized-retained-result failure.
     * Satisfies closure criterion: API error posture is explicit for unauthorized retained results.
     */
    public static TaroApiException unauthorizedResultAccess(String resultSetId) {
        return new TaroApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.UNAUTHORIZED_RESULT_ACCESS,
                "caller is not authorized to inspect resultSetId: " + resultSetId
        );
    }

    /**
     * Stage F1 returns an explicit expired-retained-result failure.
     * Satisfies closure criterion: API error posture is explicit for expired retained results.
     */
    public static TaroApiException expiredResult(String resultSetId) {
        return new TaroApiException(
                HttpStatus.GONE,
                ApiErrorCode.RESULT_EXPIRED,
                "retained result has expired: " + resultSetId
        );
    }

    /**
     * Stage F1 returns an explicit reload-incompatible-retained-result failure.
     * Satisfies closure criterion: API error posture is explicit for incompatible retained results.
     */
    public static TaroApiException incompatibleResult(String resultSetId) {
        return new TaroApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.RESULT_INCOMPATIBLE,
                "retained result is incompatible with the active topology snapshot: " + resultSetId
        );
    }

    /**
     * Stage F1 returns an explicit evicted-retained-result failure.
     * Satisfies closure criterion: retained-result retrieval remains explicit even when storage policy removes an entry before expiry.
     */
    public static TaroApiException evictedResult(String resultSetId) {
        return new TaroApiException(
                HttpStatus.GONE,
                ApiErrorCode.RESULT_EVICTED,
                "retained result is no longer available in the active store: " + resultSetId
        );
    }

    /**
     * Stage F1 returns an explicit service-unavailable failure.
     * Satisfies closure criterion: health/admin behavior exposes operator-facing API readiness explicitly.
     */
    public static TaroApiException serviceUnavailable(String message) {
        return new TaroApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.SERVICE_UNAVAILABLE, message);
    }

    /**
     * Returns the HTTP status for this API failure.
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the stable Stage F1 API error code.
     */
    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
