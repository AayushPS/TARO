import { createContext, useContext, useMemo } from "react";
import { createTaroClient } from "@shared/api/TaroHttpClient";
import { useEventLog } from "@shared/hooks/useEventLog";

const ConfigContext = createContext(null);

function queryParam(name) {
  if (typeof window === "undefined") {
    return null;
  }
  return new URLSearchParams(window.location.search).get(name);
}

function readWindowConfig() {
  if (typeof window === "undefined" || !window.__TARO_CONFIG__) {
    return {};
  }
  return window.__TARO_CONFIG__;
}

export function ConfigProvider({ children }) {
  const runtimeConfig = readWindowConfig();
  const apiBaseUrl = queryParam("api") || runtimeConfig.apiBaseUrl || "http://localhost:8080";
  const callerId = queryParam("caller") || runtimeConfig.callerId || "taro-frontend";
  const pollIntervalMs = Number(runtimeConfig.pollIntervalMs || 15000);
  const maxLogEntries = Number(runtimeConfig.maxLogEntries || 200);
  const { entries, append, clear } = useEventLog(maxLogEntries);

  const client = useMemo(() => createTaroClient({
    baseUrl: apiBaseUrl,
    timeoutMs: 15000,
    callerId,
    onLog: (entry) => {
      append(entry);
    }
  }), [apiBaseUrl, append, callerId]);

  const value = useMemo(() => ({
    apiBaseUrl,
    callerId,
    pollIntervalMs,
    maxLogEntries,
    client,
    eventLogEntries: entries,
    appendEventLog: append,
    clearEventLog: clear
  }), [apiBaseUrl, callerId, pollIntervalMs, maxLogEntries, client, entries, append, clear]);

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>;
}

export function useConfig() {
  const value = useContext(ConfigContext);
  if (!value) {
    throw new Error("ConfigContext is not available");
  }
  return value;
}
