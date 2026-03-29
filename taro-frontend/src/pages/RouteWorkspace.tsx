import { startTransition, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import type { ShellContextValue } from '../App'
import { StatusPill } from '../components/StatusPill'
import { ApiFailure, TaroApiClient, describeError } from '../lib/api'
import { formatInstant, formatSeconds, formatTicks } from '../lib/format'
import { buildRouteRequest, routeCardsFromSummary } from '../lib/route-view'
import type {
  OutcomeStatus,
  PredictionFeedbackRequestPayload,
  PublishedServingModelResponse,
  RouteApiResponse,
  RouteDetail,
  RouteSelection,
} from '../lib/types'

interface FeedbackDraft {
  outcomeStatus: OutcomeStatus
  observedAtTicks: string
  observedArrivalTicks: string
  observedCostSeconds: string
  observationCount: string
}

type ModelAvailability = 'loading' | 'available' | 'blocked' | 'error'

const initialFeedback: FeedbackDraft = {
  outcomeStatus: 'COMPLETE',
  observedAtTicks: '',
  observedArrivalTicks: '',
  observedCostSeconds: '',
  observationCount: '1',
}

export function RouteWorkspace() {
  const { apiBase, callerId, recentRouteIds, rememberRouteResult } =
    useOutletContext<ShellContextValue>()
  const [startPoint, setStartPoint] = useState('N0')
  const [endPoint, setEndPoint] = useState('N3')
  const [lookupResultId, setLookupResultId] = useState('')
  const [routeEnvelope, setRouteEnvelope] = useState<RouteApiResponse | null>(null)
  const [routeDetail, setRouteDetail] = useState<RouteDetail | null>(null)
  const [feedbackDraft, setFeedbackDraft] = useState<FeedbackDraft>(initialFeedback)
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

  const currentSummary = routeDetail?.summary ?? routeEnvelope?.summary ?? null
  const currentResultSetId = currentSummary?.resultSetId ?? null
  const routeCards = currentSummary ? routeCardsFromSummary(currentSummary) : []

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
              'No published model exists for this caller yet. The admin workspace must upload, train, and publish one first.',
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
        'This caller has no published model yet. Use the admin workspace to publish one before routing.',
      )
      setNoticeTone('warn')
      return
    }

    setBusyKey('route')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const departureTicks = Math.floor(Date.now() / 1000)
      const envelope = await client.route(
        buildRouteRequest(startPoint, endPoint, departureTicks),
      )
      const detail = envelope.retained
        ? await client.routeDetail(envelope.resultSetId)
        : null

      rememberRouteResult(envelope.resultSetId)
      startTransition(() => {
        setRouteEnvelope(envelope)
        setRouteDetail(detail)
        setLookupResultId(envelope.resultSetId)
      })
      setNotice(`Computed route result ${envelope.resultSetId}.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function loadRetainedResult() {
    setBusyKey('lookup')
    try {
      const resultId = lookupResultId.trim()
      const client = new TaroApiClient(apiBase, callerId)
      const detail = await client.routeDetail(resultId)
      rememberRouteResult(detail.summary.resultSetId)
      startTransition(() => {
        setRouteEnvelope(null)
        setRouteDetail(detail)
      })
      setNotice(`Loaded retained detail for ${detail.summary.resultSetId}.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function submitFeedback() {
    if (!currentResultSetId) {
      setNotice('Run or load a route result before recording feedback.')
      setNoticeTone('warn')
      return
    }

    setBusyKey('feedback')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const payload: PredictionFeedbackRequestPayload = {
        outcomeStatus: feedbackDraft.outcomeStatus,
        observedAtTicks: toOptionalNumber(feedbackDraft.observedAtTicks),
        observedArrivalTicks: toOptionalNumber(feedbackDraft.observedArrivalTicks),
        observedCostSeconds: toOptionalNumber(feedbackDraft.observedCostSeconds),
        observationCount: toOptionalNumber(feedbackDraft.observationCount),
      }
      const response = await client.submitRouteFeedback(currentResultSetId, payload)
      setNotice(
        `Recorded ${response.outcomeStatus.toLowerCase()} feedback for ${response.resultSetId}.`,
      )
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="page-stack">
      {notice ? (
        <div className={`notice notice--${noticeTone}`}>{notice}</div>
      ) : null}

      <section className="surface-card availability-banner">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Published model availability</p>
            <h2>
              {activeModel
                ? 'This caller is ready for routing queries.'
                : 'A published model is required before the thin query UI can serve.'}
            </h2>
          </div>
          <StatusPill
            label={
              modelAvailability === 'available'
                ? activeModel?.activeModelId ?? 'Available'
                : modelAvailability === 'loading'
                  ? 'Loading'
                  : modelAvailability === 'blocked'
                    ? 'Training required'
                    : 'Model lookup failed'
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
          <div className="detail-list">
            <MetaDatum label="Dataset" value={activeModel.datasetFileName ?? 'N/A'} />
            <MetaDatum label="Target column" value={activeModel.targetColumn ?? 'N/A'} />
            <MetaDatum
              label="Feature columns"
              value={activeModel.featureColumns.join(', ') || 'N/A'}
            />
            <MetaDatum
              label="Training window"
              value={activeModel.trainingWindowLabel}
            />
            <MetaDatum
              label="Traits"
              value={activeModel.selectedTraits.join(', ')}
            />
            <MetaDatum
              label="Published at"
              value={formatInstant(activeModel.publishedAt)}
            />
          </div>
        ) : (
          <p className="callout callout--warn">
            {modelIssue ??
              'Move to the admin workspace, upload a dataset, complete training, and publish the model for this caller.'}
          </p>
        )}
      </section>

      <section className="route-hero">
        <div className="route-hero__copy">
          <p className="eyebrow">Thin end-user query</p>
          <h2>Ask for start and end. Use the published model behind the scenes.</h2>
          <p className="hero-text">
            The user surface stays intentionally small: start point, end point,
            and three route products. TARO returns Expected ETA, Robust / P90,
            and alternatives from the caller’s active published model.
          </p>
        </div>
        <div className="route-form surface-card">
          <label className="field">
            <span>Start</span>
            <input
              onChange={(event) => setStartPoint(event.target.value)}
              placeholder="N0 or 12.34, 56.78"
              value={startPoint}
            />
          </label>
          <label className="field">
            <span>End</span>
            <input
              onChange={(event) => setEndPoint(event.target.value)}
              placeholder="N3 or 13.02, 57.11"
              value={endPoint}
            />
          </label>
          <button
            className="button button--primary"
            disabled={!activeModel || busyKey === 'route'}
            onClick={submitRouteQuery}
            type="button"
          >
            {busyKey === 'route'
              ? 'Routing…'
              : activeModel
                ? 'Route now'
                : 'Awaiting published model'}
          </button>
        </div>
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Retained lookup</p>
              <h2>Load a result by ID</h2>
            </div>
            <StatusPill label={currentResultSetId ?? 'No active result'} tone="neutral" />
          </div>
          <div className="inline-actions inline-actions--stretch">
            <label className="field field--wide">
              <span>Result set ID</span>
              <input
                onChange={(event) => setLookupResultId(event.target.value)}
                placeholder="Paste a retained result ID"
                value={lookupResultId}
              />
            </label>
            <button className="button button--ghost" onClick={loadRetainedResult} type="button">
              {busyKey === 'lookup' ? 'Loading…' : 'Load retained result'}
            </button>
          </div>
          <div className="history-strip">
            {recentRouteIds.length > 0 ? (
              recentRouteIds.map((resultId) => (
                <button
                  key={resultId}
                  className="history-pill"
                  onClick={() => setLookupResultId(resultId)}
                  type="button"
                >
                  {resultId}
                </button>
              ))
            ) : (
              <p className="muted-text">Recent route IDs will appear here after successful queries.</p>
            )}
          </div>
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Result metadata</p>
              <h2>Current retained state</h2>
            </div>
            <StatusPill label={currentSummary ? 'Loaded' : 'Idle'} />
          </div>
          {currentSummary ? (
            <div className="detail-list">
              <MetaDatum label="Result set" value={currentSummary.resultSetId} />
              <MetaDatum label="Created" value={formatInstant(currentSummary.createdAt)} />
              <MetaDatum label="Expires" value={formatInstant(currentSummary.expiresAt)} />
              <MetaDatum label="Topology" value={currentSummary.topologyVersion.topologyVersion} />
              <MetaDatum label="Scenario bundle" value={currentSummary.scenarioBundleId} />
              <MetaDatum
                label="Quarantine snapshot"
                value={currentSummary.quarantineSnapshotId}
              />
            </div>
          ) : (
            <div className="empty-state">
              No route result loaded yet. Run a query or pull a retained result ID.
            </div>
          )}
        </div>
      </section>

      <section className="selection-grid">
        {routeCards.map((card) => (
          <SelectionCard key={card.key} accent={card.accent} label={card.label} selection={card.selection} />
        ))}
        {routeCards.length === 0 ? (
          <div className="empty-state empty-state--wide">
            Query results will render here as soon as the backend returns a
            route summary.
          </div>
        ) : null}
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Scenario inspection</p>
              <h2>Scenario-level route outputs</h2>
            </div>
            <StatusPill
              label={routeDetail ? `${routeDetail.scenarioResults.length} scenarios` : 'Summary only'}
              tone="neutral"
            />
          </div>
          {routeDetail ? (
            <div className="scenario-list">
              {routeDetail.scenarioResults.map((scenario) => (
                <article className="scenario-card" key={scenario.scenarioId}>
                  <div className="scenario-card__header">
                    <div>
                      <h3>{scenario.label}</h3>
                      <p className="muted-text">{scenario.scenarioId}</p>
                    </div>
                    <StatusPill
                      label={`${(scenario.probability * 100).toFixed(0)}%`}
                      tone="neutral"
                    />
                  </div>
                  <PathTrail nodes={scenario.route.pathExternalNodeIds} />
                  {scenario.explanationTags.length > 0 ? (
                    <div className="tag-row">
                      {scenario.explanationTags.map((tag) => (
                        <span className="tag" key={tag}>
                          {tag}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </article>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              Detail retrieval is required for scenario-level inspection.
            </div>
          )}
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Outcome capture</p>
              <h2>Record route feedback</h2>
            </div>
            <StatusPill label={currentResultSetId ? 'Ready' : 'Blocked'} />
          </div>
          <div className="form-grid">
            <label className="field">
              <span>Outcome status</span>
              <select
                onChange={(event) =>
                  setFeedbackDraft((current) => ({
                    ...current,
                    outcomeStatus: event.target.value as OutcomeStatus,
                  }))
                }
                value={feedbackDraft.outcomeStatus}
              >
                <option value="COMPLETE">COMPLETE</option>
                <option value="PARTIAL">PARTIAL</option>
              </select>
            </label>
            <label className="field">
              <span>Observed at ticks</span>
              <input
                onChange={(event) =>
                  setFeedbackDraft((current) => ({
                    ...current,
                    observedAtTicks: event.target.value,
                  }))
                }
                placeholder="480"
                value={feedbackDraft.observedAtTicks}
              />
            </label>
            <label className="field">
              <span>Observed arrival ticks</span>
              <input
                onChange={(event) =>
                  setFeedbackDraft((current) => ({
                    ...current,
                    observedArrivalTicks: event.target.value,
                  }))
                }
                placeholder="540"
                value={feedbackDraft.observedArrivalTicks}
              />
            </label>
            <label className="field">
              <span>Observed cost seconds</span>
              <input
                onChange={(event) =>
                  setFeedbackDraft((current) => ({
                    ...current,
                    observedCostSeconds: event.target.value,
                  }))
                }
                placeholder="120.5"
                value={feedbackDraft.observedCostSeconds}
              />
            </label>
            <label className="field">
              <span>Observation count</span>
              <input
                onChange={(event) =>
                  setFeedbackDraft((current) => ({
                    ...current,
                    observationCount: event.target.value,
                  }))
                }
                placeholder="1"
                value={feedbackDraft.observationCount}
              />
            </label>
          </div>
          <button className="button button--primary" onClick={submitFeedback} type="button">
            {busyKey === 'feedback' ? 'Submitting…' : 'Submit feedback'}
          </button>
        </div>
      </section>
    </div>
  )
}

function SelectionCard({
  label,
  selection,
  accent,
}: {
  label: string
  selection: RouteSelection
  accent: 'ember' | 'teal' | 'gold'
}) {
  return (
    <article className={`selection-card selection-card--${accent}`}>
      <div className="selection-card__header">
        <div>
          <p className="eyebrow">{label}</p>
          <h3>
            {selection.route.reachable ? formatSeconds(selection.expectedCost) : 'Unreachable'}
          </h3>
        </div>
        <StatusPill label={selection.dominantScenarioLabel || 'No label'} tone="neutral" />
      </div>
      <div className="selection-card__stats">
        <MetaDatum label="P90" value={formatSeconds(selection.p90Cost)} />
        <MetaDatum
          label="Arrival band"
          value={`${formatTicks(selection.etaBandLowerArrivalTicks)} → ${formatTicks(selection.etaBandUpperArrivalTicks)}`}
        />
        <MetaDatum
          label="Optimality"
          value={`${(selection.optimalityProbability * 100).toFixed(0)}%`}
        />
        <MetaDatum
          label="Expected regret"
          value={formatSeconds(selection.expectedRegret)}
        />
      </div>
      <PathTrail nodes={selection.route.pathExternalNodeIds} />
      {selection.explanationTags.length > 0 ? (
        <div className="tag-row">
          {selection.explanationTags.map((tag) => (
            <span className="tag" key={tag}>
              {tag}
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
  return (
    <div className="path-trail">
      {nodes.length > 0 ? (
        nodes.map((node, index) => (
          <span className="path-node" key={`${node}-${index}`}>
            {node}
          </span>
        ))
      ) : (
        <span className="muted-text">No path nodes available.</span>
      )}
    </div>
  )
}

function toOptionalNumber(value: string): number | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  const parsed = Number(trimmed)
  return Number.isNaN(parsed) ? undefined : parsed
}
