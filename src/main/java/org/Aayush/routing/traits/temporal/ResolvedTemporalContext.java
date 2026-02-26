package org.Aayush.routing.traits.temporal;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable resolved temporal context attached to normalized internal requests.
 */
@Value
@Builder
public class ResolvedTemporalContext {
    /**
     * Locked temporal trait id.
     */
    String temporalTraitId;

    /**
     * Locked temporal strategy id.
     */
    String temporalStrategyId;

    /**
     * Locked timezone policy id, or {@code null} when not applicable.
     */
    String timezonePolicyId;

    /**
     * Locked zone id string, or {@code null} when not applicable.
     */
    String zoneId;

    /**
     * True when day-mask aware profile sampling is active.
     */
    boolean dayMaskAware;

    /**
     * Bound temporal resolver used by cost-engine hot path.
     */
    TemporalContextResolver resolver;
}
