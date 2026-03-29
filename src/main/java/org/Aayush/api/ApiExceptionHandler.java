package org.Aayush.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stage F1 centralized exception mapping for the TARO HTTP/API surface.
 * Satisfies closure criterion: API error posture is explicit and predictable.
 */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private final Clock clock;

    public ApiExceptionHandler(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    /**
     * Stage F1 maps explicit API failures into the stable JSON envelope.
     * Satisfies closure criterion: API error posture is explicit for expired, incompatible, or unauthorized retained results.
     */
    @ExceptionHandler(TaroApiException.class)
    public ResponseEntity<ApiErrorResponse> handleTaroApiException(
            TaroApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ApiErrorResponse(
                        exception.getErrorCode().name(),
                        exception.getHttpStatus().value(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        clock.instant()
                ));
    }

    /**
     * Stage F1 maps request-binding failures into the stable invalid-request envelope.
     * Satisfies closure criterion: request validation and error contracts are explicit.
     */
    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        String message = invalidRequestMessage(exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        ApiErrorCode.INVALID_REQUEST.name(),
                        HttpStatus.BAD_REQUEST.value(),
                        message,
                        request.getRequestURI(),
                        clock.instant()
                ));
    }

    private String invalidRequestMessage(Exception exception) {
        if (exception instanceof BindException bindException) {
            List<String> messages = bindException.getBindingResult()
                    .getAllErrors()
                    .stream()
                    .map(error -> {
                        if (error instanceof FieldError fieldError) {
                            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                        }
                        return error.getDefaultMessage();
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (!messages.isEmpty()) {
                return messages.stream().collect(Collectors.joining("; "));
            }
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining("; "));
        }
        return exception.getMessage() == null ? "invalid request payload" : exception.getMessage();
    }
}
