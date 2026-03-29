import { useEffect } from "react";
import { useMapContext } from "@maps/context/MapContext";
import { useRoute } from "@maps/context/RouteContext";

const LAYER_ID = "asymmetry-layer";

export function AsymmetryLayer() {
  const { mapRef, registerLayer, removeLayer } = useMapContext();
  const { result } = useRoute();

  useEffect(() => {
    if (!window.L || !mapRef.current) {
      return undefined;
    }
    removeLayer(LAYER_ID);
    const layerGroup = window.L.layerGroup();
    (result?.asymmetricSegments || []).forEach((segment) => {
      if (!Array.isArray(segment?.points) || segment.points.length < 2) {
        return;
      }
      window.L.polyline(segment.points.map((point) => [point.x, point.y]), {
        color: "#dc2626",
        weight: 6,
        opacity: 0.75
      }).addTo(layerGroup);
    });
    layerGroup.addTo(mapRef.current);
    registerLayer(LAYER_ID, layerGroup);
    return () => {
      removeLayer(LAYER_ID);
    };
  }, [mapRef, registerLayer, removeLayer, result]);

  return null;
}
