package org.Aayush.routing.traits.temporal;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.ZoneId;

/**
 * Timezone policy contract used by calendar temporal mode.
 */
public interface TemporalTimezonePolicy {

    /**
     * Returns stable policy identifier.
     */
    String id();

    /**
     * Resolves runtime zone id from temporal runtime configuration.
     *
     * @param runtimeConfig locked runtime configuration.
     * @return resolved zone id.
     * @throws PolicyResolutionException when policy-specific configuration is invalid.
     */
    ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig);

    /**
     * Policy-level resolution failure with deterministic reason code.
     */
    @Getter
    @Accessors(fluent = true)
    final class PolicyResolutionException extends RuntimeException {
        private final String reasonCode;

        /**
         * Creates a reason-coded timezone policy failure.
         */
        public PolicyResolutionException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        /**
         * Creates a reason-coded timezone policy failure with cause.
         */
        public PolicyResolutionException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }
    }
}
