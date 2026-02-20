package org.Aayush.routing.traits.addressing;

import lombok.Value;

import java.util.Objects;

/**
 * Canonical typed request endpoint shape for Stage 15 addressing.
 *
 * <p>The payload supports either external-id or coordinate inputs.
 * Coordinate payloads optionally carry a per-address strategy hint,
 * while final strategy selection remains request-scoped.</p>
 */
@Value
public class AddressInput {
    AddressType type;
    String externalId;
    Double coordinateFirst;
    Double coordinateSecond;
    String coordinateStrategyHintId;

    private AddressInput(
            AddressType type,
            String externalId,
            Double coordinateFirst,
            Double coordinateSecond,
            String coordinateStrategyHintId
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.externalId = externalId;
        this.coordinateFirst = coordinateFirst;
        this.coordinateSecond = coordinateSecond;
        this.coordinateStrategyHintId = coordinateStrategyHintId;
    }

    /**
     * Creates an external-id typed address input.
     */
    public static AddressInput ofExternalId(String externalId) {
        return new AddressInput(AddressType.EXTERNAL_ID, externalId, null, null, null);
    }

    /**
     * Creates a coordinate typed input with explicit per-address strategy hint.
     */
    public static AddressInput ofCoordinates(double first, double second, String coordinateStrategyHintId) {
        return new AddressInput(AddressType.COORDINATES, null, first, second, coordinateStrategyHintId);
    }

    /**
     * Creates an XY coordinate typed input.
     */
    public static AddressInput ofXY(double x, double y) {
        return ofCoordinates(x, y, CoordinateStrategyRegistry.STRATEGY_XY);
    }

    /**
     * Creates a LAT/LON coordinate typed input.
     */
    public static AddressInput ofLatLon(double lat, double lon) {
        return ofCoordinates(lat, lon, CoordinateStrategyRegistry.STRATEGY_LAT_LON);
    }
}
