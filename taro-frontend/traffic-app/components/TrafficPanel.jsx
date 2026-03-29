import { useTraffic } from "@traffic/context/TrafficContext";

export function TrafficPanel() {
  const { available, feedStatus } = useTraffic();

  return (
    <section className="traffic-card">
      <div className="traffic-card-header">
        <h2>Traffic Feed</h2>
        <span className={`traffic-badge traffic-badge-${feedStatus}`}>{feedStatus}</span>
      </div>
      {available ? (
        <div className="placeholder-copy">Live traffic feed wiring is ready to connect to backend endpoints.</div>
      ) : (
        <div className="placeholder-card">
          <strong>Traffic endpoints unavailable</strong>
          <span>
            `GET /api/v1/traffic/stream` and `GET /api/v1/traffic/recent` are not implemented in the current backend.
          </span>
        </div>
      )}
    </section>
  );
}
