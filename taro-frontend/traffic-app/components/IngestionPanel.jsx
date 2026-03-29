export function IngestionPanel() {
  return (
    <section className="traffic-card">
      <div className="traffic-card-header">
        <h2>Ingestion</h2>
      </div>
      <div className="placeholder-card">
        <strong>Ingestion status API unavailable</strong>
        <span>
          The E1-E5 pipeline shell is reserved here, but `GET /api/v1/ingestion/status` is not implemented yet.
        </span>
      </div>
    </section>
  );
}
