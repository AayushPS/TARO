import { startTransition, useEffect, useId, useState } from 'react'
import { StatusPill } from '../components/StatusPill'
import { ApiFailure, TaroApiClient, describeError } from '../lib/api'
import { formatInstant, formatSeconds } from '../lib/format'
import {
  publicExplanationLabel,
  publicLocationSuggestions,
  publicLocationsByKind,
  publicPathLabels,
  resolvePublicLocation,
  type PublicLocationOption,
} from '../lib/public-network'
import {
  buildRouteRequestFromEndpoints,
  routeCardsFromSummary,
  type RouteCardModel,
} from '../lib/route-view'
import type {
  PublishedServingModelResponse,
  RouteApiResponse,
} from '../lib/types'

type ModelAvailability = 'loading' | 'available' | 'blocked' | 'error'

export interface RouteWorkspaceProps {
  apiBase: string
  callerId: string
  rememberRouteResult: (resultSetId: string) => void
}

export function RouteWorkspace({
  apiBase,
  callerId,
  rememberRouteResult,
}: RouteWorkspaceProps) {
  const suggestionListId = useId()
  const [originInput, setOriginInput] = useState('Old Town')
  const [destinationInput, setDestinationInput] = useState('Harbor Point')
  const [routeEnvelope, setRouteEnvelope] = useState<RouteApiResponse | null>(null)
  const [activeModel, setActiveModel] =
    useState<PublishedServingModelResponse | null>(null)
  const [modelAvailability, setModelAvailability] =
    useState<ModelAvailability>('loading')
  const [modelIssue, setModelIssue] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeTone, setNoticeTone] = useState<'good' | 'danger' | 'warn'>(
    'good',
  )
  const [busyKey, setBusyKey] = useState<string | null>(null)

  const originMatch = resolvePublicLocation(originInput)
  const destinationMatch = resolvePublicLocation(destinationInput)
  const currentSummary = routeEnvelope?.summary ?? null
  const routeCards = currentSummary ? routeCardsFromSummary(currentSummary) : []
  const hubLocations = publicLocationsByKind('hub')
  const corridorLocations = publicLocationsByKind('corridor')

  useEffect(() => {
    async function refreshActiveModel() {
      try {
        const client = new TaroApiClient(apiBase, callerId)
        const model = await client.activeModel()
        startTransition(() => {
          setActiveModel(model)
          setModelAvailability('available')
          setModelIssue(null)
        })
      } catch (error) {
        if (error instanceof ApiFailure && error.code === 'ACTIVE_MODEL_NOT_FOUND') {
          startTransition(() => {
            setActiveModel(null)
            setModelAvailability('blocked')
            setModelIssue(
              'Routing is not live for this workspace yet. An operator still needs to publish a model.',
            )
          })
          return
        }
        startTransition(() => {
          setActiveModel(null)
          setModelAvailability('error')
          setModelIssue(describeError(error))
        })
      }
    }

    void refreshActiveModel()
    const intervalId = window.setInterval(() => {
      void refreshActiveModel()
    }, 10000)
    return () => window.clearInterval(intervalId)
  }, [apiBase, callerId])

  async function submitRouteQuery() {
    if (!activeModel) {
      setNotice(
        'This public planner is waiting on a published model. Ask an operator to publish the workspace first.',
      )
      setNoticeTone('warn')
      return
    }

    if (!originMatch || !destinationMatch) {
      setNotice(
        'Choose a known city, hub, or road segment from the suggested places before planning a trip.',
      )
      setNoticeTone('warn')
      return
    }

    setBusyKey('route')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const departureTicks = Math.floor(Date.now() / 1000)
      const envelope = await client.route(
        buildRouteRequestFromEndpoints(
          originMatch.endpoint,
          destinationMatch.endpoint,
          departureTicks,
        ),
      )

      rememberRouteResult(envelope.resultSetId)
      startTransition(() => {
        setRouteEnvelope(envelope)
      })
      setNotice(`Route ready from ${originMatch.label} to ${destinationMatch.label}.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="page-stack page-stack--public">
      {notice ? (
        <div className={`notice notice--${noticeTone}`}>{notice}</div>
      ) : null}

      <section className="surface-card availability-banner">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Planner status</p>
            <h2>
              {activeModel
                ? 'This public planner is live and ready for plain-language trip requests.'
                : 'A published model is required before this planner can answer trips.'}
            </h2>
          </div>
          <StatusPill
            label={
              modelAvailability === 'available'
                ? 'Live'
                : modelAvailability === 'loading'
                  ? 'Checking'
                  : modelAvailability === 'blocked'
                    ? 'Awaiting publish'
                    : 'Planner issue'
            }
            tone={
              modelAvailability === 'available'
                ? 'good'
                : modelAvailability === 'loading'
                  ? 'neutral'
                  : 'warn'
            }
          />
        </div>

        {activeModel ? (
          <div className="public-status-grid">
            <MetaDatum label="Workspace" value={callerId} />
            <MetaDatum
              label="Published"
              value={formatInstant(activeModel.publishedAt)}
            />
            <MetaDatum
              label="Service mode"
              value="Cities, hubs, and road segments"
            />
            <MetaDatum label="Internal codes" value="Hidden from travelers" />
          </div>
        ) : (
          <p className="callout callout--warn">
            {modelIssue ??
              'This workspace has no live model yet. The admin workspace must upload data, train, and publish before travelers can plan routes.'}
          </p>
        )}
      </section>

      <section className="planner-hero">
        <div className="planner-hero__copy">
          <p className="eyebrow">Public route planner</p>
          <h2>Tell TARO where you are and where you want to go.</h2>
          <p className="hero-text">
            Type a city, hub, or road segment such as Old Town, River Market,
            Harbor Exchange, or West Connector. Internal node ids like N1 and
            N2 stay behind the scenes.
          </p>
          <div className="planner-highlights">
            <div className="metric-panel">
              <p className="eyebrow">What to enter</p>
              <h3>Cities, hubs, or road segments</h3>
              <p className="muted-text">
                Use names people actually know instead of graph internals.
              </p>
            </div>
            <div className="metric-panel">
              <p className="eyebrow">What you get</p>
              <h3>Best, reliable, and backup routes</h3>
              <p className="muted-text">
                Each route card explains the tradeoff in plain language.
              </p>
            </div>
          </div>
        </div>

        <div className="route-form route-form--public">
          <label className="field">
            <span>Start place</span>
            <input
              list={suggestionListId}
              onChange={(event) => setOriginInput(event.target.value)}
              placeholder="Old Town, River Market, or West Connector"
              value={originInput}
            />
          </label>
          <LocationPreview
            fallbackValue={originInput}
            location={originMatch}
            unresolvedCopy="Pick a known city, hub, or road segment for the start."
          />

          <label className="field">
            <span>Destination</span>
            <input
              list={suggestionListId}
              onChange={(event) => setDestinationInput(event.target.value)}
              placeholder="Harbor Point or Harbor Exchange"
              value={destinationInput}
            />
          </label>
          <LocationPreview
            fallbackValue={destinationInput}
            location={destinationMatch}
            unresolvedCopy="Pick a known city or destination hub."
          />

          <datalist id={suggestionListId}>
            {publicLocationSuggestions().map((suggestion) => (
              <option key={suggestion} value={suggestion} />
            ))}
          </datalist>

          <button
            className="button button--primary"
            disabled={!activeModel || busyKey === 'route'}
            onClick={submitRouteQuery}
            type="button"
          >
            {busyKey === 'route'
              ? 'Planning trip...'
              : activeModel
                ? 'Plan trip'
                : 'Waiting for a live model'}
          </button>
          <p className="field-hint">
            You never need to know internal graph codes to use this planner.
          </p>
        </div>
      </section>

      <section className="dashboard-grid">
        <LocationGuideCard
          title="City hubs"
          subtitle="Best for neighborhood and district-based starts or destinations."
          locations={hubLocations}
          setDestinationInput={setDestinationInput}
          setOriginInput={setOriginInput}
        />
        <LocationGuideCard
          title="Road segments"
          subtitle="Use these when the traveler is already on a specific corridor."
          locations={corridorLocations}
          setDestinationInput={setDestinationInput}
          setOriginInput={setOriginInput}
        />
      </section>

      <section className="surface-card journey-summary">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Current trip</p>
            <h2>
              {originMatch?.label ?? 'Choose a start'} to{' '}
              {destinationMatch?.label ?? 'choose a destination'}
            </h2>
          </div>
          <StatusPill
            label={currentSummary ? 'Route ready' : 'Awaiting request'}
            tone={currentSummary ? 'good' : 'neutral'}
          />
        </div>
        {currentSummary ? (
          <div className="public-status-grid">
            <MetaDatum
              label="Start"
              value={`${originMatch?.label ?? originInput} (${originMatch?.city ?? 'Custom place'})`}
            />
            <MetaDatum
              label="Destination"
              value={`${destinationMatch?.label ?? destinationInput} (${destinationMatch?.city ?? 'Custom place'})`}
            />
            <MetaDatum
              label="Result expires"
              value={formatInstant(currentSummary.expiresAt)}
            />
            <MetaDatum
              label="Route choices"
              value={String(routeCards.length)}
            />
          </div>
        ) : (
          <div className="empty-state">
            Pick places from the guide above, then TARO will render the route
            recommendations here.
          </div>
        )}
      </section>

      <section className="selection-grid">
        {routeCards.map((card) => (
          <SelectionCard card={card} key={card.key} />
        ))}
        {routeCards.length === 0 ? (
          <div className="empty-state empty-state--wide">
            Route recommendations will appear here after you plan a trip.
          </div>
        ) : null}
      </section>
    </div>
  )
}

function LocationGuideCard({
  title,
  subtitle,
  locations,
  setOriginInput,
  setDestinationInput,
}: {
  title: string
  subtitle: string
  locations: PublicLocationOption[]
  setOriginInput: (value: string) => void
  setDestinationInput: (value: string) => void
}) {
  return (
    <section className="surface-card">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Known places</p>
          <h2>{title}</h2>
        </div>
        <StatusPill label={`${locations.length} places`} tone="neutral" />
      </div>
      <p className="muted-text">{subtitle}</p>
      <div className="location-list">
        {locations.map((location) => (
          <article className="location-card" key={location.id}>
            <div>
              <h3>{location.label}</h3>
              <p className="location-card__city">{location.city}</p>
              <p className="muted-text">{location.description}</p>
            </div>
            <div className="inline-actions">
              <button
                className="button button--ghost"
                onClick={() => setOriginInput(location.label)}
                type="button"
              >
                Use as start
              </button>
              <button
                className="button button--ghost"
                onClick={() => setDestinationInput(location.label)}
                type="button"
              >
                Use as destination
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function LocationPreview({
  location,
  fallbackValue,
  unresolvedCopy,
}: {
  location: PublicLocationOption | null
  fallbackValue: string
  unresolvedCopy: string
}) {
  if (!fallbackValue.trim()) {
    return <p className="field-hint">Choose from the known place list or start typing.</p>
  }

  if (!location) {
    return <p className="field-hint field-hint--warn">{unresolvedCopy}</p>
  }

  return (
    <div className="location-preview">
      <StatusPill
        label={location.kind === 'corridor' ? 'Road segment' : 'Hub'}
        tone="neutral"
      />
      <div>
        <strong>{location.label}</strong>
        <p className="muted-text">
          {location.city}. {location.description}
        </p>
      </div>
    </div>
  )
}

function SelectionCard({ card }: { card: RouteCardModel }) {
  const { description, label, selection, accent } = card

  return (
    <article className={`selection-card selection-card--${accent}`}>
      <div className="selection-card__header">
        <div>
          <p className="eyebrow">{label}</p>
          <h3>
            {selection.route.reachable ? formatSeconds(selection.expectedCost) : 'Unavailable'}
          </h3>
          <p className="selection-card__description">{description}</p>
        </div>
        <StatusPill
          label={`${(selection.optimalityProbability * 100).toFixed(0)}% confidence`}
          tone="neutral"
        />
      </div>

      <div className="selection-card__stats">
        <MetaDatum label="High-traffic ETA" value={formatSeconds(selection.p90Cost)} />
        <MetaDatum
          label="Typical range"
          value={`${formatSeconds(selection.minCost)} to ${formatSeconds(selection.maxCost)}`}
        />
        <MetaDatum
          label="Expected regret"
          value={formatSeconds(selection.expectedRegret)}
        />
        <MetaDatum
          label="Scenario weight"
          value={`${(selection.dominantScenarioProbability * 100).toFixed(0)}%`}
        />
      </div>

      <PathTrail nodes={selection.route.pathExternalNodeIds} />

      {selection.explanationTags.length > 0 ? (
        <div className="tag-row">
          {selection.explanationTags.map((tag) => (
            <span className="tag" key={tag}>
              {publicExplanationLabel(tag)}
            </span>
          ))}
        </div>
      ) : null}
    </article>
  )
}

function MetaDatum({ label, value }: { label: string; value: string }) {
  return (
    <div className="job-datum">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function PathTrail({ nodes }: { nodes: string[] }) {
  const labels = publicPathLabels(nodes)

  return (
    <div className="path-trail">
      {labels.length > 0 ? (
        labels.map((node, index) => (
          <span className="path-node" key={`${node}-${index}`}>
            {node}
          </span>
        ))
      ) : (
        <span className="muted-text">No path details available.</span>
      )}
    </div>
  )
}
