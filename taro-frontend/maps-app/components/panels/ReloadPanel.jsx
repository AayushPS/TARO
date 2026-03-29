import { useOperations } from "@maps/context/OperationsContext";

export function ReloadPanel() {
  const { governance, health, topologyStatus } = useOperations();

  return (
    <article className="panel-card">
      <div className="panel-heading">
        <h3>Reload</h3>
      </div>
      <dl className="detail-list">
        <div>
          <dt>Topology</dt>
          <dd>{health?.activeTopologyVersion || governance?.activeTopologyVersion || "n/a"}</dd>
        </div>
        <div>
          <dt>Serving rollback</dt>
          <dd>{governance?.servingGovernance?.rollbackAction || "n/a"}</dd>
        </div>
        <div>
          <dt>Control posture</dt>
          <dd>{typeof topologyStatus === "string" ? topologyStatus : topologyStatus.status}</dd>
        </div>
      </dl>
      <div className="placeholder-card">
        <strong>Controls unavailable</strong>
        <span>
          Topology validate/publish endpoints are planned but not implemented in the current backend.
        </span>
      </div>
    </article>
  );
}
