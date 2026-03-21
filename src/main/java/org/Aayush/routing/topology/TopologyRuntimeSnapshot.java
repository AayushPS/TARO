package org.Aayush.routing.topology;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;

/**
 * One active topology-bound routing runtime snapshot.
 */
@Value
@Builder
public class TopologyRuntimeSnapshot {
    RouteCore routeCore;
    TopologyVersion topologyVersion;
    @Builder.Default
    FailureQuarantine failureQuarantine = new FailureQuarantine();
}
