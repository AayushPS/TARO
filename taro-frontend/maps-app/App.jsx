import { useMemo, useState } from "react";
import { AlertBar } from "./components/layout/AlertBar";
import { MapCanvas } from "./components/map/MapCanvas";
import { RequestPanel } from "./components/sidebar/RequestPanel";
import { ScenarioBundle } from "./components/sidebar/ScenarioBundle";
import { ResultMetadata } from "./components/sidebar/ResultMetadata";
import { FeedbackPanel } from "./components/panels/FeedbackPanel";
import { HealthPanel } from "./components/panels/HealthPanel";
import { QuarantinePanel } from "./components/panels/QuarantinePanel";
import { ReloadPanel } from "./components/panels/ReloadPanel";
import { AuditLog } from "./components/panels/AuditLog";
import { RouteProvider } from "./context/RouteContext";
import { MapProvider } from "./context/MapContext";
import { OperationsProvider } from "./context/OperationsContext";

const TABS = [
  ["feedback", "Feedback"],
  ["health", "Health"],
  ["quarantine", "Quarantine"],
  ["reload", "Reload"],
  ["log", "Log"]
];

function ActivePanel({ tab }) {
  switch (tab) {
    case "feedback":
      return <FeedbackPanel />;
    case "health":
      return <HealthPanel />;
    case "quarantine":
      return <QuarantinePanel />;
    case "reload":
      return <ReloadPanel />;
    case "log":
      return <AuditLog />;
    default:
      return null;
  }
}

export function MapsApp() {
  const [activeTab, setActiveTab] = useState("feedback");
  const tabs = useMemo(() => TABS, []);

  return (
    <RouteProvider>
      <MapProvider>
        <OperationsProvider>
          <div className="maps-app-shell">
            <AlertBar />
            <div className="maps-main-grid">
              <aside className="maps-sidebar">
                <div className="sidebar-title">
                  <span className="eyebrow">TARO</span>
                  <h1>Maps Console</h1>
                </div>
                <RequestPanel />
                <ScenarioBundle />
                <ResultMetadata />
                <section className="panel-card tab-strip-panel">
                  <div className="tab-strip">
                    {tabs.map(([key, label]) => (
                      <button
                        key={key}
                        type="button"
                        className={`tab-chip ${activeTab === key ? "tab-chip-active" : ""}`}
                        onClick={() => setActiveTab(key)}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                  <div className="tab-panel-body">
                    <ActivePanel tab={activeTab} />
                  </div>
                </section>
              </aside>
              <main className="maps-stage">
                <MapCanvas />
              </main>
            </div>
          </div>
        </OperationsProvider>
      </MapProvider>
    </RouteProvider>
  );
}
