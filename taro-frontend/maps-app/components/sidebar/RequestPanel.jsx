import { useMemo, useState } from "react";
import { useRoute } from "@maps/context/RouteContext";

function defaultRequestState() {
  return {
    sourceExternalId: "N0",
    targetExternalId: "N3",
    departureTicks: String(Math.floor(Date.now() / 1000) + 300),
    horizonTicks: "3600",
    preferredObjective: "EXPECTED_ETA",
    topKAlternatives: "3",
    resultTtlSeconds: "600",
    retrieveId: ""
  };
}

export function RequestPanel() {
  const { status, error, submitRoute, retrieveById } = useRoute();
  const [formState, setFormState] = useState(defaultRequestState);

  const canSubmit = useMemo(() => (
    formState.sourceExternalId.trim()
    && formState.targetExternalId.trim()
    && formState.departureTicks.trim()
  ), [formState.departureTicks, formState.sourceExternalId, formState.targetExternalId]);

  function updateField(event) {
    const { name, value } = event.target;
    setFormState((current) => ({
      ...current,
      [name]: value
    }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    submitRoute({
      source: { externalId: formState.sourceExternalId.trim() },
      target: { externalId: formState.targetExternalId.trim() },
      departureTicks: Number(formState.departureTicks),
      horizonTicks: Number(formState.horizonTicks),
      preferredObjective: formState.preferredObjective,
      topKAlternatives: Number(formState.topKAlternatives),
      resultTtlSeconds: Number(formState.resultTtlSeconds)
    });
  }

  function handleRetrieve() {
    if (!formState.retrieveId.trim()) {
      return;
    }
    retrieveById(formState.retrieveId.trim());
  }

  return (
    <section className="panel-card request-panel">
      <div className="panel-heading">
        <h2>Route Request</h2>
        <span className={`status-pill status-${status}`}>{status}</span>
      </div>
      <form className="request-form" onSubmit={handleSubmit}>
        <label>
          <span>Source external ID</span>
          <input name="sourceExternalId" value={formState.sourceExternalId} onChange={updateField} />
        </label>
        <label>
          <span>Target external ID</span>
          <input name="targetExternalId" value={formState.targetExternalId} onChange={updateField} />
        </label>
        <div className="two-up">
          <label>
            <span>Departure ticks</span>
            <input name="departureTicks" value={formState.departureTicks} onChange={updateField} inputMode="numeric" />
          </label>
          <label>
            <span>Horizon ticks</span>
            <input name="horizonTicks" value={formState.horizonTicks} onChange={updateField} inputMode="numeric" />
          </label>
        </div>
        <div className="two-up">
          <label>
            <span>Preferred objective</span>
            <select name="preferredObjective" value={formState.preferredObjective} onChange={updateField}>
              <option value="EXPECTED_ETA">Expected ETA</option>
              <option value="ROBUST_P90">Robust P90</option>
            </select>
          </label>
          <label>
            <span>Top-K alternatives</span>
            <input name="topKAlternatives" value={formState.topKAlternatives} onChange={updateField} inputMode="numeric" />
          </label>
        </div>
        <label>
          <span>Result TTL seconds</span>
          <input name="resultTtlSeconds" value={formState.resultTtlSeconds} onChange={updateField} inputMode="numeric" />
        </label>
        <div className="button-row">
          <button className="primary-button" type="submit" disabled={!canSubmit || status === "loading"}>
            {status === "loading" ? "Routing…" : "Route Now"}
          </button>
        </div>
      </form>
      <div className="retrieve-block">
        <label>
          <span>Retrieve retained result</span>
          <input name="retrieveId" value={formState.retrieveId} onChange={updateField} placeholder="resultSetId" />
        </label>
        <button className="secondary-button" type="button" onClick={handleRetrieve}>
          Retrieve by ID
        </button>
      </div>
      {error ? (
        <div className="error-banner" role="alert">
          {error.message}
        </div>
      ) : null}
    </section>
  );
}
