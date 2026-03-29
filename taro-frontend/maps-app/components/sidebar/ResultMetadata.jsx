import { useRoute } from "@maps/context/RouteContext";
import { formatSeconds } from "@shared/utils/duration";

function badgeEntries(result) {
  if (!result) {
    return [];
  }
  const entries = [];
  if (result.quarantineActive === true) {
    entries.push({ tone: "warning", text: "Live incident influencing route" });
  }
  if ((result.asymmetricSegments || []).length > 0) {
    entries.push({ tone: "signal", text: `Asymmetric corridor - ${result.asymmetricSegments.length} segments` });
  }
  if (typeof result.optimalityProbability === "number" && result.optimalityProbability < 0.5) {
    entries.push({ tone: "warning", text: "No dominant route - high uncertainty" });
  }
  const departureTicks = result.request?.routeRequest?.departureTicks;
  if (Number.isFinite(departureTicks) && departureTicks > Math.floor(Date.now() / 1000) + (72 * 3600)) {
    entries.push({ tone: "accent", text: "Far horizon - P90 uncertainty wider" });
  }
  return entries;
}

export function ResultMetadata() {
  const { result } = useRoute();

  if (!result?.expectedRoute || !result?.robustRoute) {
    return (
      <section className="panel-card metadata-panel">
        <div className="panel-heading">
          <h2>Result Metadata</h2>
        </div>
        <div className="placeholder-copy">No retained or freshly evaluated route result is active.</div>
      </section>
    );
  }

  const badges = badgeEntries(result);

  return (
    <section className="panel-card metadata-panel">
      <div className="panel-heading">
        <h2>Result Metadata</h2>
        <button
          className="ghost-button"
          type="button"
          onClick={() => navigator.clipboard.writeText(result.resultSetId)}
        >
          Copy resultSetId
        </button>
      </div>
      <div className="metric-grid">
        <article>
          <span>Expected ETA</span>
          <strong>{formatSeconds(result.expectedRoute.expectedCost)}</strong>
        </article>
        <article>
          <span>Expected P90</span>
          <strong>{formatSeconds(result.expectedRoute.p90Cost)}</strong>
        </article>
        <article>
          <span>Robust ETA</span>
          <strong>{formatSeconds(result.robustRoute.expectedCost)}</strong>
        </article>
        <article>
          <span>Robust P90</span>
          <strong>{formatSeconds(result.robustRoute.p90Cost)}</strong>
        </article>
      </div>
      <div className="badge-row">
        {badges.length ? badges.map((badge) => (
          <span key={badge.text} className={`inline-badge inline-badge-${badge.tone}`}>
            {badge.text}
          </span>
        )) : <span className="placeholder-copy">No temporal badges asserted by current backend fields.</span>}
      </div>
      <dl className="detail-list">
        <div>
          <dt>Topology</dt>
          <dd>{result.topologyVersionId || "n/a"}</dd>
        </div>
        <div>
          <dt>Scenario bundle</dt>
          <dd>{result.scenarioBundleId || "n/a"}</dd>
        </div>
        <div>
          <dt>Expected winner</dt>
          <dd>{result.expectedRoute.route.pathExternalNodeIds.join(" -> ")}</dd>
        </div>
        <div>
          <dt>Robust winner</dt>
          <dd>{result.robustRoute.route.pathExternalNodeIds.join(" -> ")}</dd>
        </div>
      </dl>
    </section>
  );
}
