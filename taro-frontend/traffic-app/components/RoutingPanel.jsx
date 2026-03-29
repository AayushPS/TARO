import { useInstances } from "@traffic/context/InstanceContext";

export function RoutingPanel() {
  const { available, selectedInstance } = useInstances();

  return (
    <section className="traffic-card">
      <div className="traffic-card-header">
        <h2>Routing</h2>
      </div>
      {available ? (
        <div className="placeholder-copy">
          Instance registry support will render here. Selected instance: {selectedInstance?.instanceId || "none"}.
        </div>
      ) : (
        <div className="placeholder-card">
          <strong>Instance and rule APIs unavailable</strong>
          <span>
            `GET /api/v1/instances` and `/api/v1/routing/rules` are still planned backend surfaces.
          </span>
        </div>
      )}
    </section>
  );
}
