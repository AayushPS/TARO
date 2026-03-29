import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { PLANNED_ENDPOINTS } from "@shared/api/endpoints";
import { useConfig } from "@shared/context/ConfigContext";

const InstanceContext = createContext(null);

function capabilityUnavailable(endpoint) {
  return {
    ok: false,
    code: "ENDPOINT_UNAVAILABLE",
    endpoint
  };
}

export function InstanceProvider({ children }) {
  const { client } = useConfig();
  const [available, setAvailable] = useState(false);
  const [instances] = useState([]);
  const [routingRules] = useState([]);
  const [selectedInstance, setSelectedInstance] = useState(null);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    Promise.all([
      client.probe(PLANNED_ENDPOINTS.instances, controller.signal),
      client.probe(PLANNED_ENDPOINTS.routingRules, controller.signal)
    ]).then(([instancesProbe, rulesProbe]) => {
      if (!active) {
        return;
      }
      setAvailable(instancesProbe.available || rulesProbe.available);
    }).catch(() => {
      if (!active) {
        return;
      }
      setAvailable(false);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [client]);

  const value = useMemo(() => ({
    available,
    instances,
    routingRules,
    selectedInstance,
    select: (instanceId) => setSelectedInstance(instances.find((instance) => instance.instanceId === instanceId) || null),
    addRule: async () => capabilityUnavailable(PLANNED_ENDPOINTS.routingRules),
    deleteRule: async (ruleId) => capabilityUnavailable(`${PLANNED_ENDPOINTS.routingRules}/${ruleId}`)
  }), [available, instances, routingRules, selectedInstance]);

  return <InstanceContext.Provider value={value}>{children}</InstanceContext.Provider>;
}

export function useInstances() {
  const value = useContext(InstanceContext);
  if (!value) {
    throw new Error("InstanceContext is not available");
  }
  return value;
}
