import { describe, expect, it } from 'vitest'
import {
  publicExplanationLabel,
  publicPathLabels,
  resolvePublicLocation,
} from './public-network'

describe('resolvePublicLocation', () => {
  it('maps human city names to the public hub catalog', () => {
    expect(resolvePublicLocation('Harbor Point')?.label).toBe('Harbor Exchange')
  })

  it('maps road-segment names to coordinate endpoints', () => {
    expect(resolvePublicLocation('West Connector')?.endpoint).toEqual({
      coordinateFirst: 0.5,
      coordinateSecond: 0,
      coordinateStrategyHintId: 'xy',
    })
  })
})

describe('publicPathLabels', () => {
  it('replaces internal node ids with traveler-facing names', () => {
    expect(publicPathLabels(['N0', 'N1', 'N3'])).toEqual([
      'Old Town Gate',
      'River Market',
      'Harbor Exchange',
    ])
  })
})

describe('publicExplanationLabel', () => {
  it('turns operator tags into plain-language hints', () => {
    expect(publicExplanationLabel('incident_persists')).toMatch(/slowdown/i)
  })
})
