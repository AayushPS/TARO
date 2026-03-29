import { useEffect, useState } from "react";
import { LIVE_ENDPOINTS } from "@shared/api/endpoints";
import { normalizeFeedbackResponse } from "@shared/api/transforms";
import { useConfig } from "@shared/context/ConfigContext";
import { useRoute } from "@maps/context/RouteContext";

function initialForm(resultSetId) {
  return {
    resultSetId: resultSetId || "",
    outcomeStatus: "PARTIAL",
    observedAtTicks: "",
    observedArrivalTicks: "",
    observedCostSeconds: "",
    observationCount: ""
  };
}

export function FeedbackPanel() {
  const { client, appendEventLog, eventLogEntries } = useConfig();
  const { result } = useRoute();
  const [formState, setFormState] = useState(initialForm(result?.resultSetId));
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);

  useEffect(() => {
    if (result?.resultSetId) {
      setFormState((current) => ({
        ...current,
        resultSetId: result.resultSetId
      }));
    }
  }, [result?.resultSetId]);

  const feedbackEntries = eventLogEntries.filter((entry) => entry.type === "feedback").slice(-50).reverse();

  function updateField(event) {
    const { name, value } = event.target;
    setFormState((current) => ({
      ...current,
      [name]: value
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setStatus("submitting");
    setError(null);
    const payload = {
      outcomeStatus: formState.outcomeStatus,
      observedAtTicks: formState.observedAtTicks ? Number(formState.observedAtTicks) : null,
      observedArrivalTicks: formState.observedArrivalTicks ? Number(formState.observedArrivalTicks) : null,
      observedCostSeconds: formState.observedCostSeconds ? Number(formState.observedCostSeconds) : null,
      observationCount: formState.observationCount ? Number(formState.observationCount) : null
    };
    const response = await client.post(
      LIVE_ENDPOINTS.routeFeedback(formState.resultSetId),
      payload,
      undefined,
      { callerScoped: true }
    );
    if (!response.ok) {
      setStatus("error");
      setError(response.error);
      return;
    }
    const feedback = normalizeFeedbackResponse(response.data);
    appendEventLog({
      type: "feedback",
      message: `Feedback recorded for ${feedback.resultSetId}`,
      payload: feedback
    });
    setStatus("success");
    setFormState(initialForm(result?.resultSetId || ""));
  }

  return (
    <section className="panel-stack">
      <article className="panel-card">
        <div className="panel-heading">
          <h3>Feedback</h3>
          <span className={`status-pill status-${status}`}>{status}</span>
        </div>
        <form className="request-form" onSubmit={handleSubmit}>
          <label>
            <span>Result set ID</span>
            <input name="resultSetId" value={formState.resultSetId} onChange={updateField} readOnly={Boolean(result?.resultSetId)} />
          </label>
          <div className="two-up">
            <label>
              <span>Outcome</span>
              <select name="outcomeStatus" value={formState.outcomeStatus} onChange={updateField}>
                <option value="PARTIAL">Partial</option>
                <option value="COMPLETE">Complete</option>
              </select>
            </label>
            <label>
              <span>Observation count</span>
              <input name="observationCount" value={formState.observationCount} onChange={updateField} inputMode="numeric" />
            </label>
          </div>
          <div className="two-up">
            <label>
              <span>Observed at ticks</span>
              <input name="observedAtTicks" value={formState.observedAtTicks} onChange={updateField} inputMode="numeric" />
            </label>
            <label>
              <span>Observed arrival ticks</span>
              <input name="observedArrivalTicks" value={formState.observedArrivalTicks} onChange={updateField} inputMode="numeric" />
            </label>
          </div>
          <label>
            <span>Observed cost seconds</span>
            <input name="observedCostSeconds" value={formState.observedCostSeconds} onChange={updateField} inputMode="decimal" />
          </label>
          <button className="primary-button" type="submit" disabled={!formState.resultSetId}>
            Submit feedback
          </button>
        </form>
        {error ? <div className="error-banner">{error.message}</div> : null}
      </article>
      <article className="panel-card">
        <div className="panel-heading">
          <h3>Feedback Log</h3>
          <span>{feedbackEntries.length} entries</span>
        </div>
        <div className="simple-log">
          {feedbackEntries.length ? feedbackEntries.map((entry) => (
            <div key={entry.id} className="simple-log-entry">
              <strong>{entry.message}</strong>
              <span>{entry.payload?.outcomeStatus || "n/a"}</span>
            </div>
          )) : <div className="placeholder-copy">No feedback events yet.</div>}
        </div>
      </article>
    </section>
  );
}
