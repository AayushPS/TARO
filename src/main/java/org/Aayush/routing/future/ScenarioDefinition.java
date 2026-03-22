package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.overlay.LiveUpdate;

import java.util.List;

/**
 * One deterministic scenario inside a future-aware bundle.
 */
@Value
@Builder
public class ScenarioDefinition {
    String scenarioId;
    String label;
    double probability;
    ScenarioProbabilityAudit probabilityAudit;
    @Singular("explanationTag")
    List<String> explanationTags;
    @Singular("liveUpdate")
    List<LiveUpdate> liveUpdates;
}
