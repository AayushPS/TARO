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
  description: string
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
  return buildRouteRequestFromEndpoints(
    buildEndpointPayload(start),
    buildEndpointPayload(end),
    departureTicks,
  )
}

export function buildRouteRequestFromEndpoints(
  source: RouteEndpointPayload,
  target: RouteEndpointPayload,
  departureTicks: number,
): RouteApiRequestPayload {
  const sourceUsesCoordinates = endpointUsesCoordinates(source)
  const targetUsesCoordinates = endpointUsesCoordinates(target)
  const usesCoordinates = sourceUsesCoordinates || targetUsesCoordinates

  return {
    source,
    target,
    departureTicks,
    horizonTicks: 3600,
    preferredObjective: 'EXPECTED_ETA',
    topKAlternatives: 3,
    resultTtlSeconds: 600,
    allowMixedAddressing:
      sourceUsesCoordinates !== targetUsesCoordinates ? true : undefined,
    maxSnapDistance: usesCoordinates ? 0.75 : undefined,
  }
}

export function routeCardsFromSummary(summary: RouteSummary): RouteCardModel[] {
  const cards: RouteCardModel[] = [
    {
      key: 'expected',
      label: 'Best overall',
      accent: 'ember',
      description: 'Balanced for the lowest expected trip time right now.',
      selection: summary.expectedRoute,
    },
    {
      key: 'robust',
      label: 'Most reliable',
      accent: 'teal',
      description: 'Keeps extra buffer when disruptions are more likely.',
      selection: summary.robustRoute,
    },
  ]

  summary.alternatives.forEach((selection, index) => {
    cards.push({
      key: `alternative-${index + 1}`,
      label: `Backup option ${index + 1}`,
      accent: 'gold',
      description: 'A fallback if the first recommendation does not fit.',
      selection,
    })
  })

  return cards
}

function endpointUsesCoordinates(endpoint: RouteEndpointPayload): boolean {
  return endpoint.externalId === undefined || endpoint.externalId.trim() === ''
}
