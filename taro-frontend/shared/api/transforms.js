function ensureArray(value) {
  return Array.isArray(value) ? value : [];
}

function buildEnvelopeFromSummary(summary) {
  return {
    resultSetId: summary?.resultSetId || null,
    retained: Boolean(summary?.resultSetId),
    expiresAt: summary?.expiresAt || null,
    topologyVersion: summary?.topologyVersion || null,
    summary: summary || null
  };
}

function normalizePathPoints(pathPoints) {
  return ensureArray(pathPoints)
    .filter((point) => point && Number.isFinite(point.x) && Number.isFinite(point.y))
    .map((point) => ({ x: point.x, y: point.y }));
}

function normalizeRouteShape(route) {
  if (!route) {
    return null;
  }
  return {
    reachable: Boolean(route.reachable),
    departureTicks: route.departureTicks ?? null,
    algorithm: route.algorithm || null,
    heuristicType: route.heuristicType || null,
    sourceResolvedAddress: route.sourceResolvedAddress || null,
    targetResolvedAddress: route.targetResolvedAddress || null,
    pathExternalNodeIds: ensureArray(route.pathExternalNodeIds),
    pathPoints: normalizePathPoints(route.pathPoints)
  };
}

function normalizeSelection(selection, kind) {
  if (!selection) {
    return null;
  }
  return {
    id: `${kind}:${selection.dominantScenarioId || "route"}`,
    kind,
    route: normalizeRouteShape(selection.route),
    expectedCost: selection.expectedCost ?? null,
    p50Cost: selection.p50Cost ?? null,
    p90Cost: selection.p90Cost ?? null,
    minCost: selection.minCost ?? null,
    maxCost: selection.maxCost ?? null,
    minArrivalTicks: selection.minArrivalTicks ?? null,
    maxArrivalTicks: selection.maxArrivalTicks ?? null,
    optimalityProbability: selection.optimalityProbability ?? null,
    expectedRegret: selection.expectedRegret ?? null,
    etaBandLowerArrivalTicks: selection.etaBandLowerArrivalTicks ?? null,
    etaBandUpperArrivalTicks: selection.etaBandUpperArrivalTicks ?? null,
    dominantScenarioId: selection.dominantScenarioId || null,
    dominantScenarioProbability: selection.dominantScenarioProbability ?? null,
    dominantScenarioLabel: selection.dominantScenarioLabel || null,
    routeSelectionProvenance: selection.routeSelectionProvenance || null,
    explanationTags: ensureArray(selection.explanationTags)
  };
}

function normalizeScenarioResult(result) {
  if (!result) {
    return null;
  }
  return {
    scenarioId: result.scenarioId || null,
    label: result.label || null,
    probability: result.probability ?? null,
    route: {
      reachable: Boolean(result.route?.reachable),
      departureTicks: result.route?.departureTicks ?? null,
      arrivalTicks: result.route?.arrivalTicks ?? null,
      totalCost: result.route?.totalCost ?? null,
      algorithm: result.route?.algorithm || null,
      heuristicType: result.route?.heuristicType || null,
      pathExternalNodeIds: ensureArray(result.route?.pathExternalNodeIds),
      pathPoints: normalizePathPoints(result.pathPoints)
    },
    explanationTags: ensureArray(result.explanationTags)
  };
}

export function buildRouteResultViewModel(routeEnvelope, routeDetail) {
  const envelope = routeEnvelope?.summary ? routeEnvelope : buildEnvelopeFromSummary(routeEnvelope);
  const summary = envelope?.summary || null;
  const detail = routeDetail || null;
  const expectedRoute = normalizeSelection(summary?.expectedRoute, "expected");
  const robustRoute = normalizeSelection(summary?.robustRoute, "robust");
  const alternatives = ensureArray(summary?.alternatives)
    .map((selection, index) => ({
      ...normalizeSelection(selection, "alternative"),
      id: selection?.dominantScenarioId ? `alternative:${selection.dominantScenarioId}:${index}` : `alternative:${index}`
    }))
    .filter(Boolean);
  const scenarios = ensureArray(detail?.scenarioResults).map(normalizeScenarioResult).filter(Boolean);

  return {
    resultSetId: envelope?.resultSetId || summary?.resultSetId || null,
    retained: Boolean(envelope?.retained),
    expiresAt: envelope?.expiresAt || summary?.expiresAt || null,
    createdAt: summary?.createdAt || null,
    topologyVersionId: summary?.topologyVersion?.topologyVersion || envelope?.topologyVersion?.topologyVersion || null,
    quarantineSnapshotId: summary?.quarantineSnapshotId || null,
    scenarioBundleId: summary?.scenarioBundleId || null,
    scenarioCount: summary?.scenarioCount ?? 0,
    scenarioBundleGeneratedAt: detail?.scenarioBundleGeneratedAt || null,
    scenarioBundleValidUntil: detail?.scenarioBundleValidUntil || null,
    scenarioBundleHorizonTicks: detail?.scenarioBundleHorizonTicks ?? null,
    request: summary?.request || null,
    expectedRoute,
    robustRoute,
    alternatives,
    scenarios,
    candidateDensityCalibrationReport: detail?.candidateDensityCalibrationReport || null,
    optimalityProbability: expectedRoute?.optimalityProbability ?? null,
    quarantineActive: null,
    asymmetricSegments: [],
    quarantineZones: []
  };
}

export function normalizeHealthResponse(health) {
  if (!health) {
    return null;
  }
  return {
    status: health.status || "UNKNOWN",
    observedAt: health.observedAt || null,
    routeServiceConfigured: Boolean(health.routeServiceConfigured),
    matrixServiceConfigured: Boolean(health.matrixServiceConfigured),
    activeTopologyVersion: health.activeTopologyVersion || null,
    quarantineSnapshotId: health.quarantineSnapshotId || null,
    callerScopedResultCount: health.callerScopedResultCount ?? 0,
    reloadHealth: health.reloadHealth || "UNKNOWN",
    alertStatus: health.alertStatus || "UNKNOWN"
  };
}

export function normalizeMetricsResponse(metrics) {
  if (!metrics) {
    return null;
  }
  return {
    status: metrics.status || "UNKNOWN",
    observedAt: metrics.observedAt || null,
    activeTopologyVersion: metrics.activeTopologyVersion || null,
    callerScopedResultCount: metrics.callerScopedResultCount ?? 0,
    routeEvaluations: metrics.routeEvaluations || null,
    matrixEvaluations: metrics.matrixEvaluations || null,
    routeLookups: metrics.routeLookups || null,
    matrixLookups: metrics.matrixLookups || null,
    routeFeedback: metrics.routeFeedback || null,
    matrixFeedback: metrics.matrixFeedback || null,
    retainedResultPurge: metrics.retainedResultPurge || null,
    routeStore: metrics.routeStore || null,
    matrixStore: metrics.matrixStore || null,
    reload: metrics.reload || null,
    alerts: metrics.alerts || null
  };
}

export function normalizeGovernanceResponse(governance) {
  if (!governance) {
    return null;
  }
  return {
    observedAt: governance.observedAt || null,
    activeTopologyVersion: governance.activeTopologyVersion || null,
    currentOverallAlertStatus: governance.currentOverallAlertStatus || null,
    builderGovernance: governance.builderGovernance || null,
    servingGovernance: governance.servingGovernance || null
  };
}

export function normalizeFeedbackResponse(payload) {
  if (!payload) {
    return null;
  }
  return {
    resultKind: payload.resultKind || null,
    resultSetId: payload.resultSetId || null,
    outcomeStatus: payload.outcomeStatus || null,
    complete: Boolean(payload.complete),
    feedbackRecordedAt: payload.feedbackRecordedAt || null,
    topologyVersionId: payload.topologyVersionId || null,
    scenarioBundleId: payload.scenarioBundleId || null
  };
}
