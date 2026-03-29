import { useEffect } from "react";
import { useMapContext } from "@maps/context/MapContext";
import { useRoute } from "@maps/context/RouteContext";
import { hasRouteGeometry, routeLatLngs } from "@shared/utils/geo";

const LAYER_ID = "route-layer";

function styleFor(kind) {
  if (kind === "robust") {
    return {
      color: "#ea580c",
      weight: 5,
      dashArray: "12 10"
    };
  }
  return {
    color: "#2563eb",
    weight: 5
  };
}

export function RouteLayer() {
  const { mapRef, registerLayer, removeLayer } = useMapContext();
  const { result } = useRoute();

  useEffect(() => {
    if (!window.L || !mapRef.current) {
      return undefined;
    }
    removeLayer(LAYER_ID);
    const layerGroup = window.L.layerGroup();
    const entries = [
      result?.expectedRoute ? { kind: "expected", shape: result.expectedRoute.route } : null,
      result?.robustRoute ? { kind: "robust", shape: result.robustRoute.route } : null
    ].filter(Boolean);

    entries.forEach((entry) => {
      if (!hasRouteGeometry(entry.shape)) {
        return;
      }
      window.L.polyline(routeLatLngs(entry.shape), styleFor(entry.kind)).addTo(layerGroup);
    });

    layerGroup.addTo(mapRef.current);
    registerLayer(LAYER_ID, layerGroup);
    return () => {
      removeLayer(LAYER_ID);
    };
  }, [mapRef, registerLayer, removeLayer, result]);

  return null;
}
