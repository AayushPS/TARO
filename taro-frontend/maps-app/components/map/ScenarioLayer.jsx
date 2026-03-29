import { useEffect } from "react";
import { useMapContext } from "@maps/context/MapContext";
import { useRoute } from "@maps/context/RouteContext";
import { hasRouteGeometry, routeLatLngs } from "@shared/utils/geo";

const LAYER_ID = "scenario-layer";

function scenariosFromResult(result) {
  if (!result) {
    return [];
  }
  if (result.scenarios.length) {
    return result.scenarios.map((scenario, index) => ({
      id: scenario.scenarioId || `scenario-${index}`,
      probability: scenario.probability,
      route: scenario.route
    }));
  }
  return result.alternatives.map((scenario) => ({
    id: scenario.id,
    probability: scenario.optimalityProbability,
    route: scenario.route
  }));
}

export function ScenarioLayer() {
  const { mapRef, registerLayer, removeLayer } = useMapContext();
  const { result, selectedScenarioId } = useRoute();

  useEffect(() => {
    if (!window.L || !mapRef.current) {
      return undefined;
    }
    removeLayer(LAYER_ID);
    const layerGroup = window.L.layerGroup();
    scenariosFromResult(result).forEach((scenario, index) => {
      if (!hasRouteGeometry(scenario.route)) {
        return;
      }
      const selected = scenario.id === selectedScenarioId;
      window.L.polyline(routeLatLngs(scenario.route), {
        color: selected ? ["#0f766e", "#c2410c", "#1d4ed8", "#9333ea"][index % 4] : "#94a3b8",
        weight: selected ? 4 : 2,
        opacity: selected ? 0.9 : 0.5
      }).addTo(layerGroup);
    });
    layerGroup.addTo(mapRef.current);
    registerLayer(LAYER_ID, layerGroup);
    return () => {
      removeLayer(LAYER_ID);
    };
  }, [mapRef, registerLayer, removeLayer, result, selectedScenarioId]);

  return null;
}
