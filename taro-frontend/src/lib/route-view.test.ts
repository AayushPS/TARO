import { describe, expect, it } from 'vitest'
import {
  buildEndpointPayload,
  buildRouteRequest,
  buildRouteRequestFromEndpoints,
  routeCardsFromSummary,
} from './route-view'
import type { RouteSummary } from './types'

describe('buildEndpointPayload', () => {
  it('treats plain values as external ids', () => {
    expect(buildEndpointPayload('N42')).toEqual({ externalId: 'N42' })
  })

  it('parses coordinate pairs without extra UI hints', () => {
    expect(buildEndpointPayload('12.34, 56.78')).toEqual({
      coordinateFirst: 12.34,
      coordinateSecond: 56.78,
    })
  })
})

describe('buildRouteRequest', () => {
  it('fills the fixed serving defaults behind the thin query UI', () => {
    expect(buildRouteRequest('N0', 'N3', 900)).toMatchObject({
      departureTicks: 900,
      horizonTicks: 3600,
      preferredObjective: 'EXPECTED_ETA',
      topKAlternatives: 3,
      resultTtlSeconds: 600,
    })
  })

  it('enables mixed addressing when one side resolves from coordinates', () => {
    expect(
      buildRouteRequestFromEndpoints(
        {
          coordinateFirst: 0.5,
          coordinateSecond: 0,
          coordinateStrategyHintId: 'xy',
        },
        { externalId: 'N3' },
        900,
      ),
    ).toMatchObject({
      allowMixedAddressing: true,
      maxSnapDistance: 0.75,
    })
  })
})

describe('routeCardsFromSummary', () => {
  it('always returns the three route product groups', () => {
    const summary = sampleSummary()
    const cards = routeCardsFromSummary(summary)

    expect(cards.map((card) => card.label)).toEqual([
      'Best overall',
      'Most reliable',
      'Backup option 1',
    ])
  })
})

function sampleSummary(): RouteSummary {
  return {
    resultSetId: 'result-1',
    createdAt: '2026-03-29T00:00:00Z',
    expiresAt: '2026-03-29T00:10:00Z',
    topologyVersion: {
      modelVersion: 'model-api',
      topologyVersion: 'topo-api',
      generatedAt: '2026-03-29T00:00:00Z',
      sourceDataLineageHash: 'lineage',
      changeSetHash: 'change',
    },
    quarantineSnapshotId: 'quarantine-topo-api:0',
    scenarioBundleId: 'bundle-api',
    scenarioCount: 2,
    expectedRoute: sampleSelection(['N0', 'N1', 'N3']),
    robustRoute: sampleSelection(['N0', 'N2', 'N3']),
    alternatives: [sampleSelection(['N0', 'N1', 'N2', 'N3'])],
  }
}

function sampleSelection(pathExternalNodeIds: string[]) {
  return {
    route: {
      reachable: true,
      departureTicks: 0,
      algorithm: 'DIJKSTRA',
      heuristicType: 'NONE',
      pathExternalNodeIds,
    },
    expectedCost: 120,
    p50Cost: 118,
    p90Cost: 132,
    minCost: 110,
    maxCost: 140,
    minArrivalTicks: 120,
    maxArrivalTicks: 140,
    optimalityProbability: 0.6,
    expectedRegret: 8,
    etaBandLowerArrivalTicks: 120,
    etaBandUpperArrivalTicks: 140,
    dominantScenarioId: 'baseline',
    dominantScenarioProbability: 0.6,
    dominantScenarioLabel: 'baseline',
    routeSelectionProvenance: 'SCENARIO_OPTIMAL',
    explanationTags: [],
  }
}
