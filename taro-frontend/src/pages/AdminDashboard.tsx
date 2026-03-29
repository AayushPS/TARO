import {
  startTransition,
  useDeferredValue,
  useEffect,
  useState,
} from 'react'
import { useOutletContext } from 'react-router-dom'
import type { ShellContextValue } from '../App'
import { StatusPill } from '../components/StatusPill'
import { ApiFailure, TaroApiClient, describeError } from '../lib/api'
import {
  formatCount,
  formatInstant,
  formatMillis,
  formatSeconds,
  toneForStatus,
} from '../lib/format'
import type {
  HealthApiResponse,
  OperationalGovernanceResponse,
  OperationalMetricsResponse,
  PublishedServingModelResponse,
  RetrainingJobResponse,
  RetrainingTelemetryExportResponse,
  ResultKind,
} from '../lib/types'

interface TelemetryFilters {
  resultKind: ResultKind
  topologyVersionId: string
  scenarioBundleId: string
  traitHash: string
  completeOnly: boolean
}

interface ComposerState {
  trainingWindowLabel: string
  selectedTraits: string[]
}

interface CompletionDraft {
  succeeded: boolean
  releaseArtifactId: string
  validationSummary: string
  failureReason: string
}

interface OperationsSnapshot {
  health: HealthApiResponse | null
  metrics: OperationalMetricsResponse | null
  governance: OperationalGovernanceResponse | null
  error: string | null
  refreshedAt: string | null
}

interface DashboardSnapshot {
  activeModel: PublishedServingModelResponse | null
  jobs: Record<string, RetrainingJobResponse>
  jobIssues: Record<string, string>
  operations: OperationsSnapshot
}

const traitCatalog = [
  'recency',
  'persistence',
  'periodicity',
  'direction',
  'density',
  'homophily',
  'preferential_attachment',
] as const

const initialFilters: TelemetryFilters = {
  resultKind: 'ROUTE',
  topologyVersionId: '',
  scenarioBundleId: '',
  traitHash: '',
  completeOnly: true,
}

const initialComposer: ComposerState = {
  trainingWindowLabel: 'rolling_30d',
  selectedTraits: ['recency', 'periodicity', 'persistence'],
}

function blankCompletionDraft(): CompletionDraft {
  return {
    succeeded: true,
    releaseArtifactId: '',
    validationSummary: '',
    failureReason: '',
  }
}

export function AdminDashboard() {
  const { apiBase, callerId, trackedJobIds, trackJob, untrackJob } =
    useOutletContext<ShellContextValue>()

  const [filters, setFilters] = useState<TelemetryFilters>(initialFilters)
  const [composer, setComposer] = useState<ComposerState>(initialComposer)
  const [telemetry, setTelemetry] =
    useState<RetrainingTelemetryExportResponse | null>(null)
  const [jobs, setJobs] = useState<Record<string, RetrainingJobResponse>>({})
  const [jobIssues, setJobIssues] = useState<Record<string, string>>({})
  const [completionDrafts, setCompletionDrafts] = useState<
    Record<string, CompletionDraft>
  >({})
  const [activeModel, setActiveModel] =
    useState<PublishedServingModelResponse | null>(null)
  const [operations, setOperations] = useState<OperationsSnapshot>({
    health: null,
    metrics: null,
    governance: null,
    error: null,
    refreshedAt: null,
  })
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeTone, setNoticeTone] = useState<'good' | 'danger' | 'warn'>(
    'good',
  )
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [attachJobId, setAttachJobId] = useState('')
  const [jobSearch, setJobSearch] = useState('')
  const deferredJobSearch = useDeferredValue(jobSearch)

  function applyDashboardSnapshot(snapshot: DashboardSnapshot) {
    startTransition(() => {
      setJobs((current) => ({ ...current, ...snapshot.jobs }))
      setJobIssues(snapshot.jobIssues)
      setActiveModel(snapshot.activeModel)
      setOperations(snapshot.operations)
    })
  }

  async function refreshDashboardNow() {
    const snapshot = await fetchDashboardSnapshot(apiBase, callerId, trackedJobIds)
    applyDashboardSnapshot(snapshot)
  }

  useEffect(() => {
    async function pollDashboard() {
      const snapshot = await fetchDashboardSnapshot(apiBase, callerId, trackedJobIds)
      applyDashboardSnapshot(snapshot)
    }

    void pollDashboard()
    const intervalId = window.setInterval(() => {
      void pollDashboard()
    }, 10000)
    return () => window.clearInterval(intervalId)
  }, [trackedJobIds, apiBase, callerId])

  const filteredJobIds = trackedJobIds.filter((jobId) => {
    const searchText = deferredJobSearch.trim().toLowerCase()
    if (!searchText) {
      return true
    }
    const job = jobs[jobId]
    if (!job) {
      return jobId.toLowerCase().includes(searchText)
    }
    const haystack = [
      job.jobId,
      job.status,
      job.trainingWindowLabel,
      job.releaseArtifactId ?? '',
      ...job.selectedTraits,
    ]
      .join(' ')
      .toLowerCase()
    return haystack.includes(searchText)
  })

  async function previewTelemetry() {
    setBusyKey('telemetry')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const preview = await client.exportTelemetry(filters)
      startTransition(() => setTelemetry(preview))
      setNotice(`Loaded ${preview.rowCount} telemetry rows for preview.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function createRetrainingJob() {
    setBusyKey('create-job')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const job = await client.createRetrainingJob({
        trainingWindowLabel: composer.trainingWindowLabel.trim(),
        selectedTraits: composer.selectedTraits,
        resultKind: filters.resultKind,
        topologyVersionId: toOptionalText(filters.topologyVersionId),
        scenarioBundleId: toOptionalText(filters.scenarioBundleId),
        traitHash: toOptionalText(filters.traitHash),
        completeOnly: filters.completeOnly,
      })
      trackJob(job.jobId)
      startTransition(() => {
        setJobs((current) => ({ ...current, [job.jobId]: job }))
        setCompletionDrafts((current) => ({
          ...current,
          [job.jobId]: blankCompletionDraft(),
        }))
      })
      setNotice(`Created retraining job ${job.jobId}.`)
      setNoticeTone('good')
      await refreshDashboardNow()
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function attachTrackedJob() {
    setBusyKey('attach-job')
    try {
      const jobId = attachJobId.trim()
      if (!jobId) {
        throw new ApiFailure('Enter a job ID to attach it to this caller.')
      }
      const client = new TaroApiClient(apiBase, callerId)
      const job = await client.retrainingJob(jobId)
      trackJob(job.jobId)
      startTransition(() => {
        setJobs((current) => ({ ...current, [job.jobId]: job }))
        setAttachJobId('')
      })
      setNotice(`Attached job ${job.jobId} to this workspace.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function startJob(jobId: string) {
    setBusyKey(`start-${jobId}`)
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const job = await client.startRetrainingJob(jobId)
      startTransition(() => {
        setJobs((current) => ({ ...current, [job.jobId]: job }))
      })
      setNotice(`Job ${jobId} is now running.`)
      setNoticeTone('good')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function completeJob(jobId: string) {
    setBusyKey(`complete-${jobId}`)
    try {
      const draft = completionDrafts[jobId] ?? blankCompletionDraft()
      const client = new TaroApiClient(apiBase, callerId)
      const job = await client.completeRetrainingJob(jobId, {
        succeeded: draft.succeeded,
        releaseArtifactId: draft.succeeded
          ? toOptionalText(draft.releaseArtifactId)
          : undefined,
        validationSummary: toOptionalText(draft.validationSummary),
        failureReason: draft.succeeded
          ? undefined
          : toOptionalText(draft.failureReason),
      })
      startTransition(() => {
        setJobs((current) => ({ ...current, [job.jobId]: job }))
      })
      setNotice(
        draft.succeeded
          ? `Job ${jobId} completed successfully.`
          : `Job ${jobId} completed with failure.`,
      )
      setNoticeTone(draft.succeeded ? 'good' : 'warn')
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function publishJob(jobId: string) {
    setBusyKey(`publish-${jobId}`)
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const model = await client.publishRetrainingJob(jobId)
      startTransition(() => {
        setActiveModel(model)
      })
      setNotice(`Published ${model.activeModelId} from ${jobId}.`)
      setNoticeTone('good')
      await refreshDashboardNow()
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  const healthStatus = operations.health?.status ?? 'Unknown'
  const routeEvaluations =
    operations.metrics?.routeEvaluations.requestCount ?? 0
  const routeFeedback = operations.metrics?.routeFeedback.requestCount ?? 0

  return (
    <div className="page-stack">
      {notice ? (
        <div className={`notice notice--${noticeTone}`}>{notice}</div>
      ) : null}

      <section className="hero-grid">
        <MetricPanel
          title="Active model"
          value={activeModel?.activeModelId ?? 'None published'}
          detail={
            activeModel
              ? `Artifact ${activeModel.releaseArtifactId}`
              : 'Publish a successful job to promote the next serving artifact.'
          }
          status={activeModel ? 'PUBLISHED' : 'WAITING'}
        />
        <MetricPanel
          title="Telemetry preview"
          value={telemetry ? formatCount(telemetry.rowCount) : 'Preview pending'}
          detail={
            telemetry
              ? `${telemetry.completeRowCount} rows have complete outcomes`
              : 'Use the preview action to inspect retraining input before creating a job.'
          }
          status={telemetry ? 'READY' : 'IDLE'}
        />
        <MetricPanel
          title="Serving health"
          value={healthStatus}
          detail={
            operations.health
              ? `Topology ${operations.health.activeTopologyVersion}`
              : 'Polling /health, /metrics, and /governance'
          }
          status={operations.health?.alertStatus ?? healthStatus}
        />
        <MetricPanel
          title="Tracked jobs"
          value={formatCount(trackedJobIds.length)}
          detail={`${formatCount(routeEvaluations)} route evals and ${formatCount(routeFeedback)} feedback joins`}
          status={trackedJobIds.length > 0 ? 'RUNNING' : 'IDLE'}
        />
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Retraining input</p>
              <h2>Preview caller-scoped telemetry</h2>
            </div>
            <StatusPill
              label={
                telemetry
                  ? `${telemetry.completeRowCount}/${telemetry.rowCount} complete`
                  : 'Not loaded'
              }
            />
          </div>
          <div className="form-grid">
            <label className="field">
              <span>Result kind</span>
              <select
                value={filters.resultKind}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    resultKind: event.target.value as ResultKind,
                  }))
                }
              >
                <option value="ROUTE">ROUTE</option>
                <option value="MATRIX">MATRIX</option>
              </select>
            </label>
            <label className="field">
              <span>Topology version</span>
              <input
                value={filters.topologyVersionId}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    topologyVersionId: event.target.value,
                  }))
                }
                placeholder="topo-api"
              />
            </label>
            <label className="field">
              <span>Scenario bundle</span>
              <input
                value={filters.scenarioBundleId}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    scenarioBundleId: event.target.value,
                  }))
                }
                placeholder="bundle-api"
              />
            </label>
            <label className="field">
              <span>Trait hash</span>
              <input
                value={filters.traitHash}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    traitHash: event.target.value,
                  }))
                }
                placeholder="optional lineage pin"
              />
            </label>
          </div>
          <label className="checkbox">
            <input
              checked={filters.completeOnly}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  completeOnly: event.target.checked,
                }))
              }
              type="checkbox"
            />
            <span>Only include complete outcome rows</span>
          </label>
          <div className="action-row">
            <button
              className="button button--primary"
              onClick={previewTelemetry}
              type="button"
            >
              {busyKey === 'telemetry' ? 'Loading…' : 'Preview telemetry'}
            </button>
          </div>

          <div className="table-shell">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Result set</th>
                  <th>Topology</th>
                  <th>Bundle</th>
                  <th>Complete</th>
                  <th>Observed cost</th>
                </tr>
              </thead>
              <tbody>
                {telemetry?.rows.slice(0, 8).map((row) => (
                  <tr key={row.resultSetId}>
                    <td>{row.resultSetId}</td>
                    <td>{row.topologyVersionId}</td>
                    <td>{row.scenarioBundleId ?? 'N/A'}</td>
                    <td>
                      <StatusPill label={row.complete ? 'COMPLETE' : 'PARTIAL'} />
                    </td>
                    <td>{formatSeconds(row.observedCostSeconds)}</td>
                  </tr>
                ))}
                {!telemetry ? (
                  <tr>
                    <td colSpan={5}>
                      No preview loaded yet. Query and feedback data will appear
                      here after you create route results for this caller.
                    </td>
                  </tr>
                ) : telemetry.rows.length === 0 ? (
                  <tr>
                    <td colSpan={5}>No rows matched the current filter.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Training control</p>
              <h2>Create a retraining job</h2>
            </div>
            <StatusPill
              label={`${composer.selectedTraits.length} traits selected`}
              tone="neutral"
            />
          </div>
          <label className="field">
            <span>Training window label</span>
            <input
              value={composer.trainingWindowLabel}
              onChange={(event) =>
                setComposer((current) => ({
                  ...current,
                  trainingWindowLabel: event.target.value,
                }))
              }
              placeholder="rolling_30d"
            />
          </label>
          <div className="chip-grid">
            {traitCatalog.map((trait) => {
              const selected = composer.selectedTraits.includes(trait)
              return (
                <button
                  key={trait}
                  className={selected ? 'trait-chip is-selected' : 'trait-chip'}
                  onClick={() =>
                    setComposer((current) => ({
                      ...current,
                      selectedTraits: selected
                        ? current.selectedTraits.filter((value) => value !== trait)
                        : [...current.selectedTraits, trait],
                    }))
                  }
                  type="button"
                >
                  {trait}
                </button>
              )
            })}
          </div>
          <div className="action-row">
            <button
              className="button button--primary"
              onClick={createRetrainingJob}
              type="button"
            >
              {busyKey === 'create-job' ? 'Creating…' : 'Create retraining job'}
            </button>
          </div>
          <p className="callout">
            This frontend only uses backend capabilities that already exist:
            telemetry export, caller-scoped retraining lifecycle, publish, and
            active-model lookup.
          </p>
        </div>
      </section>

      <section className="surface-card">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Tracked lifecycle</p>
            <h2>Monitor and act on retraining jobs</h2>
          </div>
          <div className="inline-actions">
            <label className="field field--compact">
              <span>Find tracked job</span>
              <input
                value={jobSearch}
                onChange={(event) => setJobSearch(event.target.value)}
                placeholder="Search by job, trait, or status"
              />
            </label>
            <label className="field field--compact">
              <span>Attach existing job</span>
              <input
                value={attachJobId}
                onChange={(event) => setAttachJobId(event.target.value)}
                placeholder="retrain-…"
              />
            </label>
            <button className="button button--ghost" onClick={attachTrackedJob} type="button">
              {busyKey === 'attach-job' ? 'Attaching…' : 'Attach'}
            </button>
          </div>
        </div>

        <div className="job-list">
          {filteredJobIds.map((jobId) => {
            const job = jobs[jobId]
            const issue = jobIssues[jobId]
            if (!job) {
              return (
                <article className="job-card" key={jobId}>
                  <div className="job-card__header">
                    <div>
                      <h3>{jobId}</h3>
                      <p className="muted-text">No live status available for this tracked ID.</p>
                    </div>
                    <StatusPill label="Unknown" tone="warn" />
                  </div>
                  {issue ? <p className="callout callout--warn">{issue}</p> : null}
                  <div className="action-row">
                    <button
                      className="button button--ghost"
                      onClick={() => untrackJob(jobId)}
                      type="button"
                    >
                      Remove from workspace
                    </button>
                  </div>
                </article>
              )
            }

            const completionDraft = completionDrafts[job.jobId] ?? blankCompletionDraft()

            return (
              <article className="job-card" key={job.jobId}>
                <div className="job-card__header">
                  <div>
                    <h3>{job.jobId}</h3>
                    <p className="muted-text">
                      {job.trainingWindowLabel} · {job.selectedTraits.join(', ')}
                    </p>
                  </div>
                  <StatusPill label={job.status} />
                </div>

                <div className="job-card__grid">
                  <JobDatum label="Export rows" value={formatCount(job.exportRowCount)} />
                  <JobDatum
                    label="Complete rows"
                    value={formatCount(job.completeExportRowCount)}
                  />
                  <JobDatum label="Created" value={formatInstant(job.createdAt)} />
                  <JobDatum label="Updated" value={formatInstant(job.updatedAt)} />
                  <JobDatum
                    label="Base published model"
                    value={job.basePublishedModelId ?? 'None'}
                  />
                  <JobDatum
                    label="Artifact"
                    value={job.releaseArtifactId ?? 'Not assigned'}
                  />
                </div>

                {job.validationSummary ? (
                  <p className="callout">{job.validationSummary}</p>
                ) : null}
                {job.failureReason ? (
                  <p className="callout callout--warn">{job.failureReason}</p>
                ) : null}

                <div className="job-card__actions">
                  {job.status === 'QUEUED' ? (
                    <button
                      className="button button--primary"
                      onClick={() => startJob(job.jobId)}
                      type="button"
                    >
                      {busyKey === `start-${job.jobId}` ? 'Starting…' : 'Start'}
                    </button>
                  ) : null}

                  {job.status === 'QUEUED' || job.status === 'RUNNING' ? (
                    <div className="completion-editor">
                      <label className="field field--compact">
                        <span>Outcome</span>
                        <select
                          value={completionDraft.succeeded ? 'success' : 'failure'}
                          onChange={(event) =>
                            setCompletionDrafts((current) => ({
                              ...current,
                              [job.jobId]: {
                                ...completionDraft,
                                succeeded: event.target.value === 'success',
                              },
                            }))
                          }
                        >
                          <option value="success">Success</option>
                          <option value="failure">Failure</option>
                        </select>
                      </label>
                      {completionDraft.succeeded ? (
                        <label className="field field--compact">
                          <span>Release artifact</span>
                          <input
                            value={completionDraft.releaseArtifactId}
                            onChange={(event) =>
                              setCompletionDrafts((current) => ({
                                ...current,
                                [job.jobId]: {
                                  ...completionDraft,
                                  releaseArtifactId: event.target.value,
                                },
                              }))
                            }
                            placeholder="release-caller-a-v2"
                          />
                        </label>
                      ) : (
                        <label className="field field--compact">
                          <span>Failure reason</span>
                          <input
                            value={completionDraft.failureReason}
                            onChange={(event) =>
                              setCompletionDrafts((current) => ({
                                ...current,
                                [job.jobId]: {
                                  ...completionDraft,
                                  failureReason: event.target.value,
                                },
                              }))
                            }
                            placeholder="calibration gate failed"
                          />
                        </label>
                      )}
                      <label className="field field--compact field--wide">
                        <span>Validation summary</span>
                        <input
                          value={completionDraft.validationSummary}
                          onChange={(event) =>
                            setCompletionDrafts((current) => ({
                              ...current,
                              [job.jobId]: {
                                ...completionDraft,
                                validationSummary: event.target.value,
                              },
                            }))
                          }
                          placeholder="temporal probes and calibration gates passed"
                        />
                      </label>
                      <button
                        className="button button--ghost"
                        onClick={() => completeJob(job.jobId)}
                        type="button"
                      >
                        {busyKey === `complete-${job.jobId}` ? 'Completing…' : 'Complete'}
                      </button>
                    </div>
                  ) : null}

                  {job.status === 'SUCCEEDED' ? (
                    <button
                      className="button button--primary"
                      onClick={() => publishJob(job.jobId)}
                      type="button"
                    >
                      {busyKey === `publish-${job.jobId}` ? 'Publishing…' : 'Publish'}
                    </button>
                  ) : null}

                  <button
                    className="button button--ghost"
                    onClick={() => untrackJob(job.jobId)}
                    type="button"
                  >
                    Remove
                  </button>
                </div>
              </article>
            )
          })}

          {filteredJobIds.length === 0 ? (
            <div className="empty-state">
              No tracked jobs for this caller yet. Create one from telemetry or
              attach an existing job ID.
            </div>
          ) : null}
        </div>
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Published serving state</p>
              <h2>Active model</h2>
            </div>
            <StatusPill label={activeModel ? 'PUBLISHED' : 'NONE'} />
          </div>
          {activeModel ? (
            <div className="detail-list">
              <JobDatum label="Active model ID" value={activeModel.activeModelId} />
              <JobDatum
                label="Source training job"
                value={activeModel.sourceTrainingJobId}
              />
              <JobDatum
                label="Release artifact"
                value={activeModel.releaseArtifactId}
              />
              <JobDatum
                label="Published at"
                value={formatInstant(activeModel.publishedAt)}
              />
              <JobDatum
                label="Training window"
                value={activeModel.trainingWindowLabel}
              />
              <JobDatum
                label="Traits"
                value={activeModel.selectedTraits.join(', ')}
              />
            </div>
          ) : (
            <div className="empty-state">
              No caller-scoped model is published yet. Complete and publish a
              retraining job to populate this panel.
            </div>
          )}
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Serving observability</p>
              <h2>Health, metrics, governance</h2>
            </div>
            <StatusPill
              label={operations.health?.alertStatus ?? operations.error ?? 'Polling'}
              tone={
                operations.error
                  ? 'warn'
                  : toneForStatus(operations.health?.alertStatus)
              }
            />
          </div>
          <div className="detail-list">
            <JobDatum
              label="Health status"
              value={operations.health?.status ?? 'Unavailable'}
            />
            <JobDatum
              label="Active topology"
              value={operations.health?.activeTopologyVersion ?? 'Unavailable'}
            />
            <JobDatum
              label="Quarantine snapshot"
              value={operations.health?.quarantineSnapshotId ?? 'Unavailable'}
            />
            <JobDatum
              label="Route avg latency"
              value={formatMillis(operations.metrics?.routeEvaluations.averageLatencyMillis)}
            />
            <JobDatum
              label="Reload success count"
              value={formatCount(operations.metrics?.reload.appliedReloadCount)}
            />
            <JobDatum
              label="Governance alert"
              value={
                operations.governance?.currentOverallAlertStatus ?? 'Unavailable'
              }
            />
            <JobDatum
              label="Last refresh"
              value={formatInstant(operations.refreshedAt)}
            />
          </div>
          {operations.error ? (
            <p className="callout callout--warn">{operations.error}</p>
          ) : null}
        </div>
      </section>
    </div>
  )
}

function MetricPanel({
  title,
  value,
  detail,
  status,
}: {
  title: string
  value: string
  detail: string
  status: string
}) {
  return (
    <article className="metric-panel">
      <p className="eyebrow">{title}</p>
      <h2>{value}</h2>
      <p className="muted-text">{detail}</p>
      <StatusPill label={status} />
    </article>
  )
}

function JobDatum({ label, value }: { label: string; value: string }) {
  return (
    <div className="job-datum">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function toOptionalText(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed ? trimmed : undefined
}

async function fetchDashboardSnapshot(
  apiBase: string,
  callerId: string,
  trackedJobIds: string[],
): Promise<DashboardSnapshot> {
  const client = new TaroApiClient(apiBase, callerId)

  const [healthResult, metricsResult, governanceResult] =
    await Promise.allSettled([
      client.health(),
      client.metrics(),
      client.governance(),
    ])

  let nextActiveModel: PublishedServingModelResponse | null = null
  try {
    nextActiveModel = await client.activeModel()
  } catch (error) {
    if (!(error instanceof ApiFailure) || error.code !== 'ACTIVE_MODEL_NOT_FOUND') {
      nextActiveModel = null
    }
  }

  const nextJobs: Record<string, RetrainingJobResponse> = {}
  const nextJobIssues: Record<string, string> = {}
  await Promise.all(
    trackedJobIds.map(async (jobId) => {
      try {
        nextJobs[jobId] = await client.retrainingJob(jobId)
      } catch (error) {
        nextJobIssues[jobId] = describeError(error)
      }
    }),
  )

  return {
    activeModel: nextActiveModel,
    jobs: nextJobs,
    jobIssues: nextJobIssues,
    operations: {
      health: healthResult.status === 'fulfilled' ? healthResult.value : null,
      metrics: metricsResult.status === 'fulfilled' ? metricsResult.value : null,
      governance:
        governanceResult.status === 'fulfilled' ? governanceResult.value : null,
      error:
        healthResult.status === 'rejected' ||
        metricsResult.status === 'rejected' ||
        governanceResult.status === 'rejected'
          ? 'One or more operations endpoints are unavailable.'
          : null,
      refreshedAt: new Date().toISOString(),
    },
  }
}
