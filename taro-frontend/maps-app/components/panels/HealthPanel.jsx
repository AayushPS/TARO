import { useOperations } from "@maps/context/OperationsContext";
import { absoluteTime, relativeTime } from "@shared/utils/time";

function metricValue(value) {
  if (value === null || value === undefined) {
    return "n/a";
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? String(value) : value.toFixed(2);
  }
  return String(value);
}

export function HealthPanel() {
  const { health, metrics, governance, healthStatus, pollPaused, setPollPaused } = useOperations();

  return (
    <section className="panel-stack">
      <article className="panel-card">
        <div className="panel-heading">
          <h3>Health</h3>
          <button className="ghost-button" type="button" onClick={() => setPollPaused(!pollPaused)}>
            {pollPaused ? "Resume polling" : "Pause polling"}
          </button>
        </div>
        <div className="metric-grid">
          <article>
            <span>Health posture</span>
            <strong>{healthStatus}</strong>
          </article>
          <article>
            <span>Health status</span>
            <strong>{health?.status || "n/a"}</strong>
          </article>
          <article>
            <span>Active topology</span>
            <strong>{health?.activeTopologyVersion || "n/a"}</strong>
          </article>
          <article>
            <span>Caller scoped results</span>
            <strong>{metricValue(metrics?.callerScopedResultCount ?? health?.callerScopedResultCount)}</strong>
          </article>
        </div>
        <dl className="detail-list">
          <div>
            <dt>Observed</dt>
            <dd>{absoluteTime(metrics?.observedAt || health?.observedAt)}</dd>
          </div>
          <div>
            <dt>Relative</dt>
            <dd>{relativeTime(metrics?.observedAt || health?.observedAt)}</dd>
          </div>
          <div>
            <dt>Alert</dt>
            <dd>{metrics?.alerts?.overallStatus || health?.alertStatus || "n/a"}</dd>
          </div>
          <div>
            <dt>Reload</dt>
            <dd>{health?.reloadHealth || metrics?.alerts?.reloadStatus || "n/a"}</dd>
          </div>
        </dl>
      </article>
      <article className="panel-card">
        <div className="panel-heading">
          <h3>Metrics</h3>
        </div>
        <div className="metrics-columns">
          {[
            ["Route eval", metrics?.routeEvaluations],
            ["Matrix eval", metrics?.matrixEvaluations],
            ["Route lookup", metrics?.routeLookups],
            ["Route feedback", metrics?.routeFeedback]
          ].map(([label, metric]) => (
            <div key={label} className="metric-column">
              <strong>{label}</strong>
              <span>Requests: {metricValue(metric?.requestCount)}</span>
              <span>Errors: {metricValue(metric?.errorCount)}</span>
              <span>Avg ms: {metricValue(metric?.averageLatencyMillis)}</span>
            </div>
          ))}
        </div>
      </article>
      <article className="panel-card">
        <div className="panel-heading">
          <h3>Governance</h3>
        </div>
        <div className="governance-grid">
          <div>
            <strong>Builder rollout</strong>
            <p>{governance?.builderGovernance?.rolloutSequence || "n/a"}</p>
          </div>
          <div>
            <strong>Serving rollout</strong>
            <p>{governance?.servingGovernance?.rolloutSequence || "n/a"}</p>
          </div>
        </div>
      </article>
    </section>
  );
}
