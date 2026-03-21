package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable topology identity bound to one runtime snapshot.
 */
@Value
@Builder
public class TopologyVersion {
    String modelVersion;
    String topologyVersion;
    Instant generatedAt;
    String sourceDataLineageHash;
    String changeSetHash;
}
