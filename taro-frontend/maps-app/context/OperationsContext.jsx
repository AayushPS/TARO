import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { LIVE_ENDPOINTS, PLANNED_ENDPOINTS } from "@shared/api/endpoints";
import {
  normalizeGovernanceResponse,
  normalizeHealthResponse,
  normalizeMetricsResponse
} from "@shared/api/transforms";
import { useConfig } from "@shared/context/ConfigContext";
import { usePolling } from "@shared/hooks/usePolling";

const OperationsContext = createContext(null);

function capabilityUnavailable(endpoint) {
  return {
    ok: false,
    code: "ENDPOINT_UNAVAILABLE",
    endpoint
  };
}

function mapHealthStatus(metrics, health) {
  const overallStatus = metrics?.alerts?.overallStatus;
  if (overallStatus === "HEALTHY") {
    return "ok";
  }
  if (overallStatus === "WARN" || overallStatus === "DEGRADED") {
    return "degraded";
  }
  if (health?.status === "UP") {
    return "ok";
  }
  if (health?.status) {
    return "degraded";
  }
  return "unknown";
}

export function OperationsProvider({ children }) {
  const { client, pollIntervalMs, appendEventLog } = useConfig();
  const [pollPaused, setPollPaused] = useState(false);
  const [capabilities, setCapabilities] = useState({
    quarantine: false,
    topology: false
  });

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    Promise.all([
      client.probe(PLANNED_ENDPOINTS.quarantine, controller.signal),
      client.probe(PLANNED_ENDPOINTS.topologyStatus, controller.signal)
    ]).then(([quarantineProbe, topologyProbe]) => {
      if (!active) {
        return;
      }
      setCapabilities({
        quarantine: quarantineProbe.available,
        topology: topologyProbe.available
      });
    }).catch(() => {
      if (!active) {
        return;
      }
      setCapabilities({
        quarantine: false,
        topology: false
      });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [client]);

  const healthPolling = usePolling(
    () => client.get(LIVE_ENDPOINTS.health),
    pollIntervalMs,
    { paused: pollPaused }
  );
  const metricsPolling = usePolling(
    () => client.get(LIVE_ENDPOINTS.metrics),
    pollIntervalMs,
    { paused: pollPaused }
  );
  const governancePolling = usePolling(
    () => client.get(LIVE_ENDPOINTS.governance),
    pollIntervalMs,
    { paused: pollPaused }
  );

  const health = normalizeHealthResponse(healthPolling.data);
  const metrics = normalizeMetricsResponse(metricsPolling.data);
  const governance = normalizeGovernanceResponse(governancePolling.data);
  const healthStatus = mapHealthStatus(metrics, health);

  const addQuarantine = useCallback(async (payload) => {
    if (!capabilities.quarantine) {
      return capabilityUnavailable(PLANNED_ENDPOINTS.quarantine);
    }
    const result = await client.post(PLANNED_ENDPOINTS.quarantine, payload, undefined, { callerScoped: false });
    if (!result.ok) {
      appendEventLog({
        type: "quarantine-error",
        message: "Quarantine request failed",
        error: result.error
      });
    }
    return result;
  }, [appendEventLog, capabilities.quarantine, client]);

  const validateTopology = useCallback(async () => {
    if (!capabilities.topology) {
      return capabilityUnavailable(PLANNED_ENDPOINTS.topologyValidate);
    }
    return client.post(PLANNED_ENDPOINTS.topologyValidate, {});
  }, [capabilities.topology, client]);

  const publishTopology = useCallback(async () => {
    if (!capabilities.topology) {
      return capabilityUnavailable(PLANNED_ENDPOINTS.topologyPublish);
    }
    return client.post(PLANNED_ENDPOINTS.topologyPublish, {});
  }, [capabilities.topology, client]);

  const value = useMemo(() => ({
    health,
    metrics,
    governance,
    healthStatus,
    quarantine: capabilities.quarantine ? [] : "unavailable",
    addQuarantine,
    topologyStatus: capabilities.topology ? { status: "available" } : "unavailable",
    validateTopology,
    publishTopology,
    pollPaused,
    setPollPaused
  }), [
    addQuarantine,
    capabilities.quarantine,
    capabilities.topology,
    governance,
    health,
    healthStatus,
    metrics,
    pollPaused,
    publishTopology,
    validateTopology
  ]);

  return <OperationsContext.Provider value={value}>{children}</OperationsContext.Provider>;
}

export function useOperations() {
  const value = useContext(OperationsContext);
  if (!value) {
    throw new Error("OperationsContext is not available");
  }
  return value;
}
