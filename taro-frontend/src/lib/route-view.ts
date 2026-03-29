import type {
  RouteApiRequestPayload,
  RouteEndpointPayload,
  RouteSelection,
  RouteSummary,
} from './types'

const coordinatePattern =
  /^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$/

export interface RouteCardModel {
  key: string
  label: string
  accent: 'ember' | 'teal' | 'gold'
  selection: RouteSelection
}

export function buildEndpointPayload(input: string): RouteEndpointPayload {
  const trimmed = input.trim()
  const coordinateMatch = trimmed.match(coordinatePattern)
  if (coordinateMatch) {
    return {
      coordinateFirst: Number(coordinateMatch[1]),
      coordinateSecond: Number(coordinateMatch[2]),
    }
  }
  return { externalId: trimmed }
}

export function buildRouteRequest(
  start: string,
  end: string,
  departureTicks: number,
): RouteApiRequestPayload {
  return {
    source: buildEndpointPayload(start),
    target: buildEndpointPayload(end),
    departureTicks,
    horizonTicks: 3600,
    preferredObjective: 'EXPECTED_ETA',
    topKAlternatives: 3,
    resultTtlSeconds: 600,
  }
}

export function routeCardsFromSummary(summary: RouteSummary): RouteCardModel[] {
  const cards: RouteCardModel[] = [
    {
      key: 'expected',
      label: 'Expected ETA',
      accent: 'ember',
      selection: summary.expectedRoute,
    },
    {
      key: 'robust',
      label: 'Robust / P90',
      accent: 'teal',
      selection: summary.robustRoute,
    },
  ]

  summary.alternatives.forEach((selection, index) => {
    cards.push({
      key: `alternative-${index + 1}`,
      label: `Alternative ${index + 1}`,
      accent: 'gold',
      selection,
    })
  })

  return cards
}
