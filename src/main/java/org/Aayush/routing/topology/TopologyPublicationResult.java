package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Value;

/**
 * Outcome of one structural publication attempt.
 */
@Value
@Builder
public class TopologyPublicationResult {
    TopologyVersion topologyVersion;
    TopologyRuntimeSnapshot candidateSnapshot;
    TopologyModelSource candidateSource;
    StructuralChangeSet changeSet;
    boolean reloaded;
}
