import { useEffect, useMemo, useRef } from "react";
import { useLeaflet } from "@shared/hooks/useLeaflet";
import { geoJsonToBounds, mergeBounds } from "@shared/utils/geo";
import { useMapContext } from "@maps/context/MapContext";
import { useRoute } from "@maps/context/RouteContext";
import { RouteLayer } from "./RouteLayer";
import { ScenarioLayer } from "./ScenarioLayer";
import { AsymmetryLayer } from "./AsymmetryLayer";
import { QuarantineLayer } from "./QuarantineLayer";

function boundsFromResult(result) {
  if (!result) {
    return [];
  }
  const shapes = [
    result.expectedRoute?.route,
    result.robustRoute?.route,
    ...result.alternatives.map((selection) => selection.route),
    ...result.scenarios.map((scenario) => scenario.route)
  ];
  return shapes
    .map((shape) => geoJsonToBounds(shape?.pathPoints || []))
    .filter(Boolean);
}

export function MapCanvas() {
  const containerRef = useRef(null);
  const { ready } = useLeaflet();
  const { mapRef, fitBounds } = useMapContext();
  const { result } = useRoute();

  useEffect(() => {
    if (!ready || !containerRef.current || mapRef.current || !window.L) {
      return undefined;
    }
    const map = window.L.map(containerRef.current, {
      zoomControl: false
    }).setView([20, 0], 2);
    window.L.control.zoom({ position: "topright" }).addTo(map);
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [mapRef, ready]);

  const resultBounds = useMemo(() => mergeBounds(boundsFromResult(result)), [result]);

  useEffect(() => {
    if (resultBounds) {
      fitBounds(resultBounds);
    }
  }, [fitBounds, resultBounds]);

  if (!ready) {
    return <div className="map-loading">Loading map…</div>;
  }

  return (
    <div className="map-shell">
      <div className="map-surface" ref={containerRef} style={{ height: "100%" }} />
      {!resultBounds ? (
        <div className="map-empty-state">
          <strong>No plotted geometry yet</strong>
          <span>Submit or retrieve a retained route result to draw the route surface.</span>
        </div>
      ) : null}
      <RouteLayer />
      <ScenarioLayer />
      <AsymmetryLayer />
      <QuarantineLayer />
    </div>
  );
}
