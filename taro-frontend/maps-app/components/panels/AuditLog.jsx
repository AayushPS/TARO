import { useState } from "react";
import { useConfig } from "@shared/context/ConfigContext";
import { absoluteTime } from "@shared/utils/time";

export function AuditLog() {
  const { eventLogEntries, clearEventLog } = useConfig();
  const [expandedId, setExpandedId] = useState(null);

  function handleExport() {
    const blob = new Blob([JSON.stringify(eventLogEntries, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "taro-event-log.json";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <article className="panel-card">
      <div className="panel-heading">
        <h3>Audit Log</h3>
        <div className="button-row">
          <button className="ghost-button" type="button" onClick={handleExport}>Export</button>
          <button className="ghost-button" type="button" onClick={clearEventLog}>Clear</button>
        </div>
      </div>
      <div className="audit-log">
        {eventLogEntries.length ? eventLogEntries.slice().reverse().map((entry) => (
          <button
            key={entry.id}
            className="audit-row"
            type="button"
            onClick={() => setExpandedId((current) => current === entry.id ? null : entry.id)}
          >
            <div className="audit-topline">
              <strong>{entry.type || "log"}</strong>
              <span>{absoluteTime(entry.timestamp)}</span>
            </div>
            <div className="audit-message">{entry.message || `${entry.method || "GET"} ${entry.url || ""}`}</div>
            {expandedId === entry.id ? (
              <pre className="audit-detail">{JSON.stringify(entry, null, 2)}</pre>
            ) : null}
          </button>
        )) : <div className="placeholder-copy">No HTTP or operator log entries yet.</div>}
      </div>
    </article>
  );
}
