package org.Aayush.routing.topology;

/**
 * Retained-result store that participates in topology reload compatibility handling.
 */
public interface TopologyBoundResultStore {
    void purgeExpired();

    void invalidateForTopology(TopologyVersion topologyVersion);
}
