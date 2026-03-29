import { useMemo, useState } from "react";
import { TrafficProvider, useTraffic } from "./context/TrafficContext";
import { InstanceProvider } from "./context/InstanceContext";
import { TrafficPanel } from "./components/TrafficPanel";
import { RoutingPanel } from "./components/RoutingPanel";
import { RateLimitPanel } from "./components/RateLimitPanel";
import { IngestionPanel } from "./components/IngestionPanel";

const TABS = [
  ["traffic", "Traffic"],
  ["routing", "Routing"],
  ["ratelimits", "Rate Limits"],
  ["ingestion", "Ingestion"]
];

function TrafficShell() {
  const [activeTab, setActiveTab] = useState("traffic");
  const tabs = useMemo(() => TABS, []);
  const { feedStatus, available, pause, resume } = useTraffic();

  return (
    <div className="traffic-shell">
      <header className="traffic-header">
        <div>
          <span className="eyebrow">TARO</span>
          <h1>Traffic Handler</h1>
        </div>
        <div className="traffic-header-actions">
          <span className={`traffic-badge traffic-badge-${feedStatus}`}>
            {available ? (feedStatus === "live" ? "LIVE" : "PAUSED") : "UNAVAILABLE"}
          </span>
          <button className="ghost-button" type="button" onClick={feedStatus === "live" ? pause : resume}>
            {feedStatus === "live" ? "Pause" : "Resume"}
          </button>
        </div>
      </header>
      <div className="traffic-layout">
        <aside className="traffic-sidebar">
          {tabs.map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={`traffic-nav ${activeTab === key ? "traffic-nav-active" : ""}`}
              onClick={() => setActiveTab(key)}
            >
              {label}
            </button>
          ))}
        </aside>
        <main className="traffic-main">
          {activeTab === "traffic" ? <TrafficPanel /> : null}
          {activeTab === "routing" ? <RoutingPanel /> : null}
          {activeTab === "ratelimits" ? <RateLimitPanel /> : null}
          {activeTab === "ingestion" ? <IngestionPanel /> : null}
        </main>
      </div>
    </div>
  );
}

export function TrafficApp() {
  return (
    <TrafficProvider>
      <InstanceProvider>
        <TrafficShell />
      </InstanceProvider>
    </TrafficProvider>
  );
}
