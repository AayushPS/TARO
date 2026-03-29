import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { PLANNED_ENDPOINTS } from "@shared/api/endpoints";
import { useConfig } from "@shared/context/ConfigContext";

const TrafficContext = createContext(null);

export function TrafficProvider({ children }) {
  const { client } = useConfig();
  const [feedStatus, setFeedStatus] = useState("paused");
  const [available, setAvailable] = useState(false);
  const [requestFeed] = useState([]);
  const [aggregates] = useState(new Map());

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    Promise.all([
      client.probe(PLANNED_ENDPOINTS.trafficStream, controller.signal),
      client.probe(PLANNED_ENDPOINTS.trafficRecent, controller.signal)
    ]).then(([streamProbe, recentProbe]) => {
      if (!active) {
        return;
      }
      const nextAvailable = streamProbe.available || recentProbe.available;
      setAvailable(nextAvailable);
      setFeedStatus(nextAvailable ? "paused" : "error");
    }).catch(() => {
      if (!active) {
        return;
      }
      setAvailable(false);
      setFeedStatus("error");
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [client]);

  const value = useMemo(() => ({
    requestFeed,
    aggregates,
    feedStatus,
    available,
    pause: () => setFeedStatus("paused"),
    resume: () => setFeedStatus(available ? "live" : "error")
  }), [aggregates, available, feedStatus, requestFeed]);

  return <TrafficContext.Provider value={value}>{children}</TrafficContext.Provider>;
}

export function useTraffic() {
  const value = useContext(TrafficContext);
  if (!value) {
    throw new Error("TrafficContext is not available");
  }
  return value;
}
