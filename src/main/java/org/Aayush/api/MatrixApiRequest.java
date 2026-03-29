package org.Aayush.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Stage F1 versioned HTTP matrix request contract.
 * Satisfies closure criterion: request validation and versioned API behavior are explicit.
 */
public record MatrixApiRequest(
        @Valid @NotEmpty List<RouteApiRequest.EndpointPayload> sources,
        @Valid @NotEmpty List<RouteApiRequest.EndpointPayload> targets,
        @NotNull @PositiveOrZero Long departureTicks,
        @Positive Long horizonTicks,
        @Positive Long resultTtlSeconds,
        Boolean allowMixedAddressing,
        @PositiveOrZero Double maxSnapDistance
) {
}
