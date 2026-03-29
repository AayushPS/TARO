export function geoJsonToBounds(points) {
  if (!points || points.length < 2 || typeof window === "undefined" || !window.L) {
    return null;
  }
  return window.L.latLngBounds(points.map((point) => [point.x, point.y]));
}

export function mergeBounds(boundsList) {
  if (!boundsList.length || typeof window === "undefined" || !window.L) {
    return null;
  }
  const seed = boundsList.find(Boolean);
  if (!seed) {
    return null;
  }
  const merged = window.L.latLngBounds(seed.getSouthWest(), seed.getNorthEast());
  boundsList.filter(Boolean).slice(1).forEach((bounds) => {
    merged.extend(bounds);
  });
  return merged;
}

export function routeLatLngs(routeShape) {
  return (routeShape?.pathPoints || []).map((point) => [point.x, point.y]);
}

export function hasRouteGeometry(routeShape) {
  return routeLatLngs(routeShape).length > 1;
}
