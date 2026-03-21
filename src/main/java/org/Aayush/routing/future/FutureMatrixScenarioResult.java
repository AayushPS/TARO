package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.core.MatrixResponse;

import java.util.List;

/**
 * One scenario-specific matrix output retained inside a future-aware matrix result set.
 */
@Value
@Builder
public class FutureMatrixScenarioResult {
    String scenarioId;
    String label;
    double probability;
    MatrixResponse matrix;
    @Singular("explanationTag")
    List<String> explanationTags;
}
