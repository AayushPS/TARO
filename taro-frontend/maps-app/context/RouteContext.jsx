import { createContext, startTransition, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { LIVE_ENDPOINTS } from "@shared/api/endpoints";
import { buildRouteResultViewModel } from "@shared/api/transforms";
import { useConfig } from "@shared/context/ConfigContext";
import { useAbortableFetch } from "@shared/hooks/useAbortableFetch";

const RouteContext = createContext(null);

function buildRouteEnvelopeFromSummary(summary) {
  return {
    resultSetId: summary?.resultSetId || null,
    retained: true,
    expiresAt: summary?.expiresAt || null,
    topologyVersion: summary?.topologyVersion || null,
    summary
  };
}

export function RouteProvider({ children }) {
  const { client, appendEventLog } = useConfig();
  const [selectedScenarioId, setSelectedScenarioId] = useState(null);

  const fetchRoute = useCallback(async (signal, action) => {
    if (!action) {
      return {
        ok: false,
        error: {
          status: 0,
          code: "INVALID_ACTION",
          message: "Missing route action"
        }
      };
    }

    if (action.type === "submit") {
      const envelopeResult = await client.post(LIVE_ENDPOINTS.route, action.request, signal, { callerScoped: true });
      if (!envelopeResult.ok) {
        return envelopeResult;
      }

      const envelope = envelopeResult.data;
      let detail = null;
      if (envelope?.retained && envelope?.resultSetId) {
        const detailResult = await client.get(LIVE_ENDPOINTS.routeDetail(envelope.resultSetId), signal, { callerScoped: true });
        if (detailResult.ok) {
          detail = detailResult.data;
        } else if (detailResult.error.code !== "ABORTED") {
          appendEventLog({
            type: "route-detail-warning",
            message: `Detail lookup degraded for ${envelope.resultSetId}`,
            detailError: detailResult.error
          });
        }
      }
      return {
        ok: true,
        data: buildRouteResultViewModel(envelope, detail)
      };
    }

    if (action.type === "retrieve") {
      const summaryResult = await client.get(LIVE_ENDPOINTS.routeSummary(action.resultSetId), signal, { callerScoped: true });
      if (!summaryResult.ok) {
        return summaryResult;
      }
      const detailResult = await client.get(LIVE_ENDPOINTS.routeDetail(action.resultSetId), signal, { callerScoped: true });
      return {
        ok: true,
        data: buildRouteResultViewModel(
          buildRouteEnvelopeFromSummary(summaryResult.data),
          detailResult.ok ? detailResult.data : null
        )
      };
    }

    return {
      ok: false,
      error: {
        status: 0,
        code: "UNSUPPORTED_ACTION",
        message: `Unsupported route action: ${action.type}`
      }
    };
  }, [appendEventLog, client]);

  const requestState = useAbortableFetch(fetchRoute);
  const result = requestState.data?.data || null;

  useEffect(() => {
    if (!result) {
      setSelectedScenarioId(null);
      return;
    }
    const nextScenarioId = result.scenarios[0]?.scenarioId || result.alternatives[0]?.id || null;
    setSelectedScenarioId(nextScenarioId);
  }, [result?.resultSetId]);

  const submitRoute = useCallback((request) => {
    startTransition(() => {
      requestState.execute({ type: "submit", request });
    });
  }, [requestState]);

  const retrieveById = useCallback((resultSetId) => {
    startTransition(() => {
      requestState.execute({ type: "retrieve", resultSetId });
    });
  }, [requestState]);

  const selectScenario = useCallback((scenarioId) => {
    setSelectedScenarioId(scenarioId);
  }, []);

  const value = useMemo(() => ({
    status: requestState.status,
    result,
    selectedScenarioId,
    error: requestState.error,
    submitRoute,
    retrieveById,
    selectScenario
  }), [requestState.error, requestState.status, result, retrieveById, selectScenario, selectedScenarioId, submitRoute]);

  return <RouteContext.Provider value={value}>{children}</RouteContext.Provider>;
}

export function useRoute() {
  const value = useContext(RouteContext);
  if (!value) {
    throw new Error("RouteContext is not available");
  }
  return value;
}
