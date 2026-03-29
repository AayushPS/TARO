import { useOperations } from "@maps/context/OperationsContext";

export function AlertBar() {
  const { healthStatus, metrics, health } = useOperations();

  if (healthStatus !== "degraded") {
    return null;
  }

  return (
    <div className="alert-bar">
      <strong>Degraded posture</strong>
      <span>
        Metrics alert: {metrics?.alerts?.overallStatus || "unknown"}.
        Health surface: {health?.status || "unknown"}.
      </span>
    </div>
  );
}
