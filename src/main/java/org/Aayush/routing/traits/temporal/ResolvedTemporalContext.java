package org.Aayush.routing.traits.temporal;

import lombok.Builder;
import lombok.Value;

import java.time.ZoneOffset;

/**
 * Immutable resolved temporal context attached to normalized internal requests.
 */
@Value
@Builder
public class ResolvedTemporalContext {
    private static final ResolvedTemporalContext DEFAULT_CALENDAR_UTC = defaultCalendarUtcContext();

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

    /**
     * Returns canonical compatibility context equivalent to Stage 15 behavior.
     */
    public static ResolvedTemporalContext defaultCalendarUtc() {
        return DEFAULT_CALENDAR_UTC;
    }

    private static ResolvedTemporalContext defaultCalendarUtcContext() {
        TemporalResolutionStrategy strategy = new CalendarTemporalResolutionStrategy();
        TemporalContextResolver resolver = new TemporalContextResolver(strategy, ZoneOffset.UTC, null);
        return ResolvedTemporalContext.builder()
                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                .temporalStrategyId(strategy.id())
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .zoneId(ZoneOffset.UTC.getId())
                .dayMaskAware(strategy.dayMaskAware())
                .resolver(resolver)
                .build();
    }
}
