package org.Aayush.routing.traits.temporal;

/**
 * Immutable temporal-trait contract.
 *
 * <p>A temporal trait selects the strategy family used to resolve day and bucket
 * coordinates for time-dependent profile sampling.</p>
 */
public interface TemporalTrait {

    /**
     * Returns stable temporal trait identifier (for example {@code LINEAR}).
     */
    String id();

    /**
     * Returns strategy identifier bound to this trait.
     */
    String strategyId();
}
