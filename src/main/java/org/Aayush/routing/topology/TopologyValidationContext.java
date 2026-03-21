package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Validation context exposed to v13 publication gates.
 */
@Value
@Builder
public class TopologyValidationContext {
    TopologyRuntimeSnapshot currentSnapshot;
    TopologyRuntimeSnapshot candidateSnapshot;
    TopologyModelSource currentSource;
    TopologyModelSource candidateSource;
    StructuralChangeSet changeSet;
    Instant validatedAt;
}
