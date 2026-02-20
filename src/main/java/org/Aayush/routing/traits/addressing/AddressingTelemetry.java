package org.Aayush.routing.traits.addressing;

/**
 * Internal deterministic Stage 15 addressing telemetry.
 */
public record AddressingTelemetry(
        int endpointCount,
        int uniqueEndpointCount,
        int resolveCalls,
        int dedupSaved,
        int externalIdResolveCount,
        int coordinateResolveCount,
        int snapThresholdRejectCount,
        long normalizationNanos,
        boolean mixedMode
) {
    /**
     * Returns a zeroed telemetry payload.
     */
    public static AddressingTelemetry empty() {
        return new AddressingTelemetry(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false
        );
    }
}
