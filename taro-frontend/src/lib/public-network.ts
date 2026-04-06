import type { RouteEndpointPayload } from './types'

export type PublicLocationKind = 'hub' | 'corridor'

export interface PublicLocationOption {
  id: string
  label: string
  city: string
  kind: PublicLocationKind
  description: string
  endpoint: RouteEndpointPayload
  aliases: string[]
}

const HUB_LOCATIONS: PublicLocationOption[] = [
  {
    id: 'old-town-gate',
    label: 'Old Town Gate',
    city: 'Old Town',
    kind: 'hub',
    description: 'Historic core pickup point for Old Town departures.',
    endpoint: { externalId: 'N0' },
    aliases: ['old town', 'oldtown', 'gate', 'n0'],
  },
  {
    id: 'river-market',
    label: 'River Market',
    city: 'Riverside',
    kind: 'hub',
    description: 'Busy market district on the Riverside side of the network.',
    endpoint: { externalId: 'N1' },
    aliases: ['riverside', 'market', 'market district', 'n1'],
  },
  {
    id: 'hill-junction',
    label: 'Hill Junction',
    city: 'North Heights',
    kind: 'hub',
    description: 'North Heights interchange used for uphill and commuter trips.',
    endpoint: { externalId: 'N2' },
    aliases: ['north heights', 'northheight', 'hill', 'junction', 'n2'],
  },
  {
    id: 'harbor-exchange',
    label: 'Harbor Exchange',
    city: 'Harbor Point',
    kind: 'hub',
    description: 'Harbor Point arrival hub close to the waterfront transfer area.',
    endpoint: { externalId: 'N3' },
    aliases: ['harbor point', 'harbor', 'exchange', 'waterfront', 'n3'],
  },
]

const CORRIDOR_LOCATIONS: PublicLocationOption[] = [
  {
    id: 'west-connector',
    label: 'West Connector',
    city: 'Old Town to Riverside',
    kind: 'corridor',
    description: 'Road segment between Old Town Gate and River Market.',
    endpoint: {
      coordinateFirst: 0.5,
      coordinateSecond: 0.0,
      coordinateStrategyHintId: 'xy',
    },
    aliases: ['connector', 'west road', 'old town to riverside', 'edge n0 n1'],
  },
  {
    id: 'north-rise',
    label: 'North Rise',
    city: 'Old Town to North Heights',
    kind: 'corridor',
    description: 'Climbing corridor from Old Town Gate toward Hill Junction.',
    endpoint: {
      coordinateFirst: 0.0,
      coordinateSecond: 0.5,
      coordinateStrategyHintId: 'xy',
    },
    aliases: ['rise', 'north road', 'old town to north heights', 'edge n0 n2'],
  },
  {
    id: 'market-flyover',
    label: 'Market Flyover',
    city: 'Riverside to Harbor Point',
    kind: 'corridor',
    description: 'Fast elevated segment linking River Market to Harbor Exchange.',
    endpoint: {
      coordinateFirst: 1.0,
      coordinateSecond: 0.5,
      coordinateStrategyHintId: 'xy',
    },
    aliases: ['flyover', 'riverside to harbor point', 'edge n1 n3'],
  },
  {
    id: 'harbor-spine',
    label: 'Harbor Spine',
    city: 'North Heights to Harbor Point',
    kind: 'corridor',
    description: 'Main spine road feeding Harbor Exchange from Hill Junction.',
    endpoint: {
      coordinateFirst: 0.5,
      coordinateSecond: 1.0,
      coordinateStrategyHintId: 'xy',
    },
    aliases: ['spine', 'north heights to harbor point', 'edge n2 n3'],
  },
]

const EXPLANATION_LABELS: Record<string, string> = {
  incident_persists: 'Safer if a slowdown keeps spreading through the corridor.',
}

const NODE_LABELS: Record<string, string> = HUB_LOCATIONS.reduce<Record<string, string>>(
  (labels, location) => {
    const nodeId = location.endpoint.externalId
    if (nodeId) {
      labels[nodeId] = location.label
    }
    return labels
  },
  {},
)

export const publicLocationCatalog: PublicLocationOption[] = [
  ...HUB_LOCATIONS,
  ...CORRIDOR_LOCATIONS,
]

export function publicLocationsByKind(kind: PublicLocationKind): PublicLocationOption[] {
  return publicLocationCatalog.filter((location) => location.kind === kind)
}

export function resolvePublicLocation(input: string): PublicLocationOption | null {
  const normalized = normalizeSearchText(input)
  if (!normalized) {
    return null
  }

  const exact = publicLocationCatalog.find((location) =>
    searchTermsForLocation(location).some((term) => term === normalized),
  )
  if (exact) {
    return exact
  }

  const partial = publicLocationCatalog.find((location) =>
    searchTermsForLocation(location).some(
      (term) => term.includes(normalized) || normalized.includes(term),
    ),
  )
  return partial ?? null
}

export function publicLocationSuggestions(): string[] {
  return publicLocationCatalog.map((location) => location.label)
}

export function publicPathLabels(nodeIds: string[]): string[] {
  return nodeIds.map((nodeId) => NODE_LABELS[nodeId] ?? nodeId)
}

export function publicExplanationLabel(tag: string): string {
  return EXPLANATION_LABELS[tag] ?? humanizeToken(tag)
}

function searchTermsForLocation(location: PublicLocationOption): string[] {
  return [
    location.id,
    location.label,
    location.city,
    ...location.aliases,
  ].map(normalizeSearchText)
}

function normalizeSearchText(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
}

function humanizeToken(value: string): string {
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (match) => match.toUpperCase())
}
