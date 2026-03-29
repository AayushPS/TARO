export const LIVE_ENDPOINTS = {
  route: "/api/v1/route",
  routeSummary: (resultSetId) => `/api/v1/route/results/${encodeURIComponent(resultSetId)}/summary`,
  routeDetail: (resultSetId) => `/api/v1/route/results/${encodeURIComponent(resultSetId)}/detail`,
  matrix: "/api/v1/matrix",
  matrixSummary: (resultSetId) => `/api/v1/matrix/results/${encodeURIComponent(resultSetId)}/summary`,
  matrixDetail: (resultSetId) => `/api/v1/matrix/results/${encodeURIComponent(resultSetId)}/detail`,
  health: "/api/v1/health",
  metrics: "/api/v1/metrics",
  governance: "/api/v1/governance",
  routeFeedback: (resultSetId) => `/api/v1/feedback/route/results/${encodeURIComponent(resultSetId)}/outcome`,
  matrixFeedback: (resultSetId) => `/api/v1/feedback/matrix/results/${encodeURIComponent(resultSetId)}/outcome`,
  retainedResultPurge: "/api/v1/admin/retained-results/purge"
};

export const PLANNED_ENDPOINTS = {
  quarantine: "/api/v1/quarantine",
  topologyStatus: "/api/v1/topology/status",
  topologyValidate: "/api/v1/topology/validate",
  topologyPublish: "/api/v1/topology/publish",
  trafficStream: "/api/v1/traffic/stream",
  trafficRecent: "/api/v1/traffic/recent",
  instances: "/api/v1/instances",
  routingRules: "/api/v1/routing/rules",
  rateLimits: "/api/v1/ratelimits",
  ingestionStatus: "/api/v1/ingestion/status"
};

export function isCallerScoped(path) {
  return [
    LIVE_ENDPOINTS.route,
    LIVE_ENDPOINTS.matrix,
    "/api/v1/route/results/",
    "/api/v1/matrix/results/",
    "/api/v1/feedback/"
  ].some((prefix) => path.startsWith(prefix));
}
