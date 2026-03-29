import { useOperations } from "@maps/context/OperationsContext";

export function QuarantinePanel() {
  const { quarantine } = useOperations();

  return (
    <article className="panel-card">
      <div className="panel-heading">
        <h3>Quarantine</h3>
      </div>
      {quarantine === "unavailable" ? (
        <div className="placeholder-card">
          <strong>API not yet available</strong>
          <span>
            The frontend surface is reserved, but the backend does not currently expose
            quarantine registry or mutation endpoints.
          </span>
        </div>
      ) : (
        <div className="placeholder-copy">No quarantine entries loaded.</div>
      )}
    </article>
  );
}
