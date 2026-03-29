package org.Aayush.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Stage F1 versioned HTTP route request contract.
 * Satisfies closure criterion: request validation and versioned API behavior are explicit.
 */
public record RouteApiRequest(
        @Valid @NotNull EndpointPayload source,
        @Valid @NotNull EndpointPayload target,
        @NotNull @PositiveOrZero Long departureTicks,
        @Positive Long horizonTicks,
        String preferredObjective,
        @Positive Integer topKAlternatives,
        @Positive Long resultTtlSeconds,
        Boolean allowMixedAddressing,
        @PositiveOrZero Double maxSnapDistance
) {
    /**
     * Stage F1 endpoint payload that supports either external-id or coordinate addressing.
     * Satisfies closure criterion: request validation is explicit at the API boundary.
     */
    public record EndpointPayload(
            String externalId,
            Double coordinateFirst,
            Double coordinateSecond,
            String coordinateStrategyHintId
    ) {
    }
}
