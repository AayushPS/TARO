export function RateLimitPanel() {
  return (
    <section className="traffic-card">
      <div className="traffic-card-header">
        <h2>Rate Limits</h2>
      </div>
      <div className="placeholder-card">
        <strong>Rate-limit API unavailable</strong>
        <span>
          This panel is intentionally held in placeholder mode until `GET /api/v1/ratelimits` exists.
        </span>
      </div>
    </section>
  );
}
