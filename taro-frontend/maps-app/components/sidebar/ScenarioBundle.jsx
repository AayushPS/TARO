import { useMemo } from "react";
import { useRoute } from "@maps/context/RouteContext";

function entriesForResult(result) {
  if (!result) {
    return [];
  }
  if (result.scenarios.length) {
    return result.scenarios.map((scenario) => ({
      id: scenario.scenarioId,
      label: scenario.label || scenario.scenarioId,
      probability: scenario.probability || 0,
      path: scenario.route.pathExternalNodeIds
    }));
  }
  return result.alternatives.map((selection) => ({
    id: selection.id,
    label: selection.dominantScenarioLabel || selection.id,
    probability: selection.optimalityProbability || 0,
    path: selection.route.pathExternalNodeIds
  }));
}

export function ScenarioBundle() {
  const { result, selectedScenarioId, selectScenario } = useRoute();
  const entries = useMemo(
    () => entriesForResult(result).sort((left, right) => (right.probability || 0) - (left.probability || 0)),
    [result]
  );

  return (
    <section className="panel-card scenario-panel">
      <div className="panel-heading">
        <h2>Scenario Bundle</h2>
        <span>{result?.scenarioCount ?? 0} scenarios</span>
      </div>
      {entries.length ? (
        <div className="scenario-list">
          {entries.map((entry) => {
            const active = selectedScenarioId === entry.id;
            return (
              <button
                key={entry.id}
                className={`scenario-item ${active ? "scenario-item-active" : ""}`}
                type="button"
                onClick={() => selectScenario(entry.id)}
              >
                <div className="scenario-topline">
                  <strong>{entry.label}</strong>
                  <span>{Math.round((entry.probability || 0) * 100)}%</span>
                </div>
                <div className="scenario-bar">
                  <span style={{ width: `${Math.max(4, Math.round((entry.probability || 0) * 100))}%` }} />
                </div>
                <div className="scenario-path">{entry.path.join(" -> ")}</div>
              </button>
            );
          })}
        </div>
      ) : (
        <div className="placeholder-copy">No scenario bundle loaded yet.</div>
      )}
    </section>
  );
}
