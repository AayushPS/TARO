package org.Aayush.routing.future;

import java.time.Duration;

/**
 * Common future-aware request contract used to materialize one scenario bundle.
 */
public interface ScenarioBundleRequest {
    long getHorizonTicks();

    Duration getResultTtl();

    long getDepartureTicks();
}
