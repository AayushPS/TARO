import { useEffect } from "react";
import { useMapContext } from "@maps/context/MapContext";
import { useRoute } from "@maps/context/RouteContext";

const LAYER_ID = "quarantine-layer";
const STYLE_ID = "taro-quarantine-pulse-style";

function ensurePulseStyle() {
  if (typeof document === "undefined" || document.getElementById(STYLE_ID)) {
    return;
  }
  const style = document.createElement("style");
  style.id = STYLE_ID;
  style.textContent = `
    @keyframes taroPulse {
      0% { transform: scale(0.94); opacity: 0.9; }
      100% { transform: scale(1.08); opacity: 0.3; }
    }
    .taro-quarantine-pulse {
      animation: taroPulse 1.2s ease-in-out infinite alternate;
    }
  `;
  document.head.appendChild(style);
}

export function QuarantineLayer() {
  const { mapRef, registerLayer, removeLayer } = useMapContext();
  const { result } = useRoute();

  useEffect(() => {
    if (!window.L || !mapRef.current) {
      return undefined;
    }
    ensurePulseStyle();
    removeLayer(LAYER_ID);
    const layerGroup = window.L.layerGroup();
    (result?.quarantineZones || []).forEach((zone) => {
      if (!Number.isFinite(zone?.x) || !Number.isFinite(zone?.y)) {
        return;
      }
      window.L.circleMarker([zone.x, zone.y], {
        radius: zone.radius || 10,
        color: "#b42318",
        weight: 2,
        fillColor: "#ef4444",
        fillOpacity: 0.28,
        className: "taro-quarantine-pulse"
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
