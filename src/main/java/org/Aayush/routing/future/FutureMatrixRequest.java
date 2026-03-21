package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.MatrixRequest;

import java.time.Duration;
import java.util.Objects;

/**
 * Client-facing future-aware matrix request.
 */
@Value
@Builder
public class FutureMatrixRequest implements ScenarioBundleRequest {
    MatrixRequest matrixRequest;
    @Builder.Default
    long horizonTicks = 3_600L;
    @Builder.Default
    Duration resultTtl = Duration.ofMinutes(10);

    @Override
    public long getDepartureTicks() {
        return Objects.requireNonNull(matrixRequest, "matrixRequest").getDepartureTicks();
    }
}
