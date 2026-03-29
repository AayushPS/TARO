package org.Aayush.api;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.FutureMatrixRequest;
import org.Aayush.routing.future.FutureRouteObjective;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stage F1 mapper from versioned API DTOs to canonical future-routing service requests.
 * Satisfies closure criterion: request validation and versioned API behavior are explicit.
 */
@Component
public final class ApiRequestMapper {
    private static final long DEFAULT_HORIZON_TICKS = 3_600L;
    private static final Duration DEFAULT_RESULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_TOP_K_ALTERNATIVES = 3;

    /**
     * Stage F1 maps one HTTP route payload into the canonical future-aware route request.
     * Satisfies closure criterion: route endpoint behavior wraps existing future-aware routing semantics.
     */
    public FutureRouteRequest toFutureRouteRequest(RouteApiRequest apiRequest) {
        RouteApiRequest nonNullRequest = Objects.requireNonNull(apiRequest, "apiRequest");
        boolean mixedMode = endpointUsesCoordinates(nonNullRequest.source()) != endpointUsesCoordinates(nonNullRequest.target());
        if (mixedMode && !Boolean.TRUE.equals(nonNullRequest.allowMixedAddressing())) {
            throw TaroApiException.badRequest("mixed source/target addressing requires allowMixedAddressing=true");
        }

        RouteRequest routeRequest = RouteRequest.builder()
                .sourceExternalId(endpointExternalId(nonNullRequest.source(), "source"))
                .targetExternalId(endpointExternalId(nonNullRequest.target(), "target"))
                .sourceAddress(endpointAddress(nonNullRequest.source(), "source"))
                .targetAddress(endpointAddress(nonNullRequest.target(), "target"))
                .allowMixedAddressing(nonNullRequest.allowMixedAddressing())
                .maxSnapDistance(nonNullRequest.maxSnapDistance())
                .departureTicks(nonNullRequest.departureTicks())
                .build();

        return FutureRouteRequest.builder()
                .routeRequest(routeRequest)
                .horizonTicks(defaultLong(nonNullRequest.horizonTicks(), DEFAULT_HORIZON_TICKS))
                .preferredObjective(parseObjective(nonNullRequest.preferredObjective()))
                .topKAlternatives(defaultInt(nonNullRequest.topKAlternatives(), DEFAULT_TOP_K_ALTERNATIVES))
                .resultTtl(defaultDuration(nonNullRequest.resultTtlSeconds()))
                .build();
    }

    /**
     * Stage F1 maps one HTTP matrix payload into the canonical future-aware matrix request.
     * Satisfies closure criterion: matrix endpoint behavior wraps existing future-aware matrix semantics.
     */
    public FutureMatrixRequest toFutureMatrixRequest(MatrixApiRequest apiRequest) {
        MatrixApiRequest nonNullRequest = Objects.requireNonNull(apiRequest, "apiRequest");
        boolean sourceUsesCoordinates = endpointListUsesCoordinates(nonNullRequest.sources(), "sources");
        boolean targetUsesCoordinates = endpointListUsesCoordinates(nonNullRequest.targets(), "targets");
        boolean mixedMode = sourceUsesCoordinates != targetUsesCoordinates;
        if (mixedMode && !Boolean.TRUE.equals(nonNullRequest.allowMixedAddressing())) {
            throw TaroApiException.badRequest("mixed source/target addressing requires allowMixedAddressing=true");
        }

        MatrixRequest.MatrixRequestBuilder matrixRequestBuilder = MatrixRequest.builder()
                .allowMixedAddressing(nonNullRequest.allowMixedAddressing())
                .maxSnapDistance(nonNullRequest.maxSnapDistance())
                .departureTicks(nonNullRequest.departureTicks());
        if (sourceUsesCoordinates) {
            endpointAddresses(nonNullRequest.sources(), "sources").forEach(matrixRequestBuilder::sourceAddress);
        } else {
            endpointExternalIds(nonNullRequest.sources(), "sources").forEach(matrixRequestBuilder::sourceExternalId);
        }
        if (targetUsesCoordinates) {
            endpointAddresses(nonNullRequest.targets(), "targets").forEach(matrixRequestBuilder::targetAddress);
        } else {
            endpointExternalIds(nonNullRequest.targets(), "targets").forEach(matrixRequestBuilder::targetExternalId);
        }

        MatrixRequest matrixRequest = matrixRequestBuilder.build();

        return FutureMatrixRequest.builder()
                .matrixRequest(matrixRequest)
                .horizonTicks(defaultLong(nonNullRequest.horizonTicks(), DEFAULT_HORIZON_TICKS))
                .resultTtl(defaultDuration(nonNullRequest.resultTtlSeconds()))
                .build();
    }

    private boolean endpointListUsesCoordinates(
            List<RouteApiRequest.EndpointPayload> endpoints,
            String fieldName
    ) {
        boolean usesCoordinates = endpointUsesCoordinates(endpoints.getFirst());
        for (int i = 0; i < endpoints.size(); i++) {
            RouteApiRequest.EndpointPayload endpoint = endpoints.get(i);
            validateEndpoint(endpoint, fieldName + "[" + i + "]");
            if (endpointUsesCoordinates(endpoint) != usesCoordinates) {
                throw TaroApiException.badRequest(fieldName + " must use one addressing mode consistently");
            }
        }
        return usesCoordinates;
    }

    private List<String> endpointExternalIds(
            List<RouteApiRequest.EndpointPayload> endpoints,
            String fieldName
    ) {
        return endpoints.stream()
                .map(endpoint -> endpointExternalId(endpoint, fieldName))
                .toList();
    }

    private List<AddressInput> endpointAddresses(
            List<RouteApiRequest.EndpointPayload> endpoints,
            String fieldName
    ) {
        return endpoints.stream()
                .map(endpoint -> endpointAddress(endpoint, fieldName))
                .toList();
    }

    private String endpointExternalId(RouteApiRequest.EndpointPayload endpoint, String fieldName) {
        validateEndpoint(endpoint, fieldName);
        return endpoint.externalId();
    }

    private AddressInput endpointAddress(RouteApiRequest.EndpointPayload endpoint, String fieldName) {
        validateEndpoint(endpoint, fieldName);
        if (!endpointUsesCoordinates(endpoint)) {
            return null;
        }
        return AddressInput.ofCoordinates(
                endpoint.coordinateFirst(),
                endpoint.coordinateSecond(),
                endpoint.coordinateStrategyHintId()
        );
    }

    private void validateEndpoint(RouteApiRequest.EndpointPayload endpoint, String fieldName) {
        if (endpoint == null) {
            throw TaroApiException.badRequest(fieldName + " must be provided");
        }
        boolean hasExternalId = endpoint.externalId() != null && !endpoint.externalId().isBlank();
        boolean hasCoordinateFirst = endpoint.coordinateFirst() != null;
        boolean hasCoordinateSecond = endpoint.coordinateSecond() != null;
        boolean hasCoordinates = hasCoordinateFirst || hasCoordinateSecond;

        if (hasExternalId == hasCoordinates) {
            throw TaroApiException.badRequest(fieldName + " must provide exactly one of externalId or coordinate pair");
        }
        if (hasCoordinates && !(hasCoordinateFirst && hasCoordinateSecond)) {
            throw TaroApiException.badRequest(fieldName + " coordinateFirst and coordinateSecond must both be provided");
        }
    }

    private boolean endpointUsesCoordinates(RouteApiRequest.EndpointPayload endpoint) {
        return endpoint.externalId() == null || endpoint.externalId().isBlank();
    }

    private FutureRouteObjective parseObjective(String preferredObjective) {
        if (preferredObjective == null || preferredObjective.isBlank()) {
            return FutureRouteObjective.EXPECTED_ETA;
        }
        try {
            return FutureRouteObjective.valueOf(preferredObjective.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw TaroApiException.badRequest("preferredObjective must be one of EXPECTED_ETA, ROBUST_P90, or TOP_K_ALTERNATIVES");
        }
    }

    private long defaultLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private Duration defaultDuration(Long ttlSeconds) {
        return ttlSeconds == null ? DEFAULT_RESULT_TTL : Duration.ofSeconds(ttlSeconds);
    }
}
