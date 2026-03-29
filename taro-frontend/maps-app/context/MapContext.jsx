import { createContext, useCallback, useContext, useMemo, useRef } from "react";

const MapContext = createContext(null);

export function MapProvider({ children }) {
  const mapRef = useRef(null);
  const layersRef = useRef(new Map());

  const registerLayer = useCallback((id, layer) => {
    layersRef.current.set(id, layer);
  }, []);

  const removeLayer = useCallback((id) => {
    const layer = layersRef.current.get(id);
    if (layer && mapRef.current?.hasLayer(layer)) {
      mapRef.current.removeLayer(layer);
    }
    layersRef.current.delete(id);
  }, []);

  const fitBounds = useCallback((bounds) => {
    if (!bounds || !mapRef.current) {
      return;
    }
    mapRef.current.fitBounds(bounds, {
      padding: [32, 32],
      animate: true
    });
  }, []);

  const value = useMemo(() => ({
    mapRef,
    registerLayer,
    removeLayer,
    fitBounds
  }), [fitBounds, registerLayer, removeLayer]);

  return <MapContext.Provider value={value}>{children}</MapContext.Provider>;
}

export function useMapContext() {
  const value = useContext(MapContext);
  if (!value) {
    throw new Error("MapContext is not available");
  }
  return value;
}
