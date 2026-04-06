import {
  startTransition,
  useDeferredValue,
  useEffect,
  useState,
} from 'react'
import { StatusPill } from '../components/StatusPill'
import { ApiFailure, TaroApiClient, describeError } from '../lib/api'
import {
  formatCount,
  formatInstant,
  formatMillis,
  toneForStatus,
} from '../lib/format'
import type {
  AdminNotificationResponse,
  HealthApiResponse,
  OperationalGovernanceResponse,
  OperationalMetricsResponse,
  PublishedServingModelResponse,
  RetrainingJobResponse,
  TrainingDatasetResponse,
} from '../lib/types'

interface ComposerState {
  trainingWindowLabel: string
  selectedTraits: string[]
  datasetId: string
  targetColumn: string
  featureColumns: string[]
  notifyOnCompletion: boolean
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
  datasets: TrainingDatasetResponse[]
  notifications: AdminNotificationResponse[]
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

const initialComposer: ComposerState = {
  trainingWindowLabel: 'bootstrap_90d',
  selectedTraits: ['recency', 'periodicity', 'persistence'],
  datasetId: '',
  targetColumn: '',
  featureColumns: [],
  notifyOnCompletion: true,
}

function blankCompletionDraft(): CompletionDraft {
  return {
    succeeded: true,
    releaseArtifactId: '',
    validationSummary: '',
    failureReason: '',
  }
}

export interface AdminDashboardProps {
  apiBase: string
  callerId: string
  trackedJobIds: string[]
  trackJob: (jobId: string) => void
  untrackJob: (jobId: string) => void
}

export function AdminDashboard({
  apiBase,
  callerId,
  trackedJobIds,
  trackJob,
  untrackJob,
}: AdminDashboardProps) {

  const [composer, setComposer] = useState<ComposerState>(initialComposer)
  const [datasets, setDatasets] = useState<TrainingDatasetResponse[]>([])
  const [notifications, setNotifications] = useState<AdminNotificationResponse[]>([])
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
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeTone, setNoticeTone] = useState<'good' | 'danger' | 'warn'>(
    'good',
  )
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [attachJobId, setAttachJobId] = useState('')
  const [jobSearch, setJobSearch] = useState('')
  const deferredJobSearch = useDeferredValue(jobSearch)

  const selectedDataset =
    datasets.find((dataset) => dataset.datasetId === composer.datasetId) ?? null

  function applyDashboardSnapshot(snapshot: DashboardSnapshot) {
    startTransition(() => {
      setActiveModel(snapshot.activeModel)
      setDatasets(snapshot.datasets)
      setNotifications(snapshot.notifications)
      setJobs((current) => ({ ...current, ...snapshot.jobs }))
      setJobIssues(snapshot.jobIssues)
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

  useEffect(() => {
    setComposer((current) => reconcileComposer(current, datasets))
  }, [datasets])

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
      job.datasetFileName ?? '',
      job.targetColumn ?? '',
      job.releaseArtifactId ?? '',
      ...job.selectedTraits,
      ...job.featureColumns,
    ]
      .join(' ')
      .toLowerCase()
    return haystack.includes(searchText)
  })

  async function uploadDataset() {
    if (!pendingFile) {
      setNotice('Choose a CSV file before uploading.')
      setNoticeTone('warn')
      return
    }

    setBusyKey('upload-dataset')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const dataset = await client.uploadDataset(pendingFile)
      startTransition(() => {
        setPendingFile(null)
        setComposer((current) => composerForDataset(dataset, current))
      })
      setNotice(`Uploaded ${dataset.fileName} with ${dataset.rowCount} rows.`)
      setNoticeTone('good')
      await refreshDashboardNow()
    } catch (error) {
      setNotice(describeError(error))
      setNoticeTone('danger')
    } finally {
      setBusyKey(null)
    }
  }

  async function createTrainingJob() {
    if (!selectedDataset) {
      setNotice('Upload or select a dataset before creating a training job.')
      setNoticeTone('warn')
      return
    }
    if (!composer.targetColumn.trim()) {
      setNotice('Choose a target column for training.')
      setNoticeTone('warn')
      return
    }
    if (composer.featureColumns.length === 0) {
      setNotice('Choose at least one feature column for training.')
      setNoticeTone('warn')
      return
    }

    setBusyKey('create-job')
    try {
      const client = new TaroApiClient(apiBase, callerId)
      const job = await client.createRetrainingJob({
        trainingWindowLabel: composer.trainingWindowLabel.trim(),
        selectedTraits: composer.selectedTraits,
        resultKind: 'ROUTE',
        datasetId: selectedDataset.datasetId,
        targetColumn: composer.targetColumn.trim(),
        featureColumns: composer.featureColumns,
        notifyOnCompletion: composer.notifyOnCompletion,
        completeOnly: true,
      })
      trackJob(job.jobId)
      startTransition(() => {
        setJobs((current) => ({ ...current, [job.jobId]: job }))
        setCompletionDrafts((current) => ({
          ...current,
          [job.jobId]: blankCompletionDraft(),
        }))
      })
      setNotice(`Created training job ${job.jobId} for ${selectedDataset.fileName}.`)
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
      await refreshDashboardNow()
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
      await refreshDashboardNow()
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

  return (
    <div className="page-stack">
      {notice ? (
        <div className={`notice notice--${noticeTone}`}>{notice}</div>
      ) : null}

      <section className="hero-grid">
        <MetricPanel
          title="Active model"
          value={activeModel?.activeModelId ?? 'Awaiting publish'}
          detail={
            activeModel
              ? `${activeModel.datasetFileName ?? 'Dataset-backed'} · ${activeModel.releaseArtifactId}`
              : 'Upload a CSV, pick the training columns, and publish the successful model.'
          }
          status={activeModel ? 'PUBLISHED' : 'WAITING'}
        />
        <MetricPanel
          title="Uploaded datasets"
          value={formatCount(datasets.length)}
          detail={
            selectedDataset
              ? `${selectedDataset.fileName} selected for the next training run`
              : 'No caller-scoped dataset selected yet'
          }
          status={datasets.length > 0 ? 'READY' : 'IDLE'}
        />
        <MetricPanel
          title="Admin notifications"
          value={formatCount(notifications.length)}
          detail={
            notifications[0]
              ? `${notifications[0].title} at ${formatInstant(notifications[0].createdAt)}`
              : 'Completion and publish notices will appear here'
          }
          status={notifications.length > 0 ? 'LIVE' : 'QUIET'}
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
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Dataset intake</p>
              <h2>Upload caller-scoped training data</h2>
            </div>
            <StatusPill
              label={selectedDataset ? selectedDataset.fileName : 'No dataset selected'}
              tone="neutral"
            />
          </div>

          <div className="inline-actions inline-actions--stretch">
            <label className="field field--wide">
              <span>CSV file</span>
              <input
                accept=".csv,text/csv"
                onChange={(event) =>
                  setPendingFile(event.target.files?.[0] ?? null)
                }
                type="file"
              />
            </label>
            <button className="button button--primary" onClick={uploadDataset} type="button">
              {busyKey === 'upload-dataset' ? 'Uploading…' : 'Upload dataset'}
            </button>
          </div>

          <div className="history-strip">
            {datasets.length > 0 ? (
              datasets.map((dataset) => (
                <button
                  key={dataset.datasetId}
                  className={
                    dataset.datasetId === composer.datasetId
                      ? 'history-pill history-pill--selected'
                      : 'history-pill'
                  }
                  onClick={() =>
                    setComposer((current) => composerForDataset(dataset, current))
                  }
                  type="button"
                >
                  {dataset.fileName}
                </button>
              ))
            ) : (
              <p className="muted-text">
                Uploaded datasets will appear here so you can switch the active
                training configuration per caller.
              </p>
            )}
          </div>

          {selectedDataset ? (
            <>
              <div className="detail-list">
                <JobDatum label="Dataset ID" value={selectedDataset.datasetId} />
                <JobDatum
                  label="Uploaded"
                  value={formatInstant(selectedDataset.uploadedAt)}
                />
                <JobDatum
                  label="Rows"
                  value={formatCount(selectedDataset.rowCount)}
                />
                <JobDatum
                  label="Columns"
                  value={formatCount(selectedDataset.columnCount)}
                />
                <JobDatum label="SHA-256" value={selectedDataset.sha256} />
                <JobDatum
                  label="Headers"
                  value={selectedDataset.headers.join(', ')}
                />
              </div>

              <div className="table-shell">
                <table className="data-table">
                  <thead>
                    <tr>
                      {selectedDataset.headers.map((header) => (
                        <th key={header}>{header}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {selectedDataset.sampleRows.map((row, index) => (
                      <tr key={`${selectedDataset.datasetId}-sample-${index}`}>
                        {selectedDataset.headers.map((header, columnIndex) => (
                          <td key={`${header}-${columnIndex}`}>
                            {row[columnIndex] ?? ''}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            <div className="empty-state">
              Upload a CSV to inspect its headers and sample rows before you
              create the training job.
            </div>
          )}
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Training plan</p>
              <h2>Define model parameters before training</h2>
            </div>
            <StatusPill
              label={
                selectedDataset
                  ? `${composer.featureColumns.length} features`
                  : 'Dataset required'
              }
            />
          </div>

          <div className="form-grid">
            <label className="field">
              <span>Dataset</span>
              <select
                onChange={(event) => {
                  const dataset =
                    datasets.find(
                      (candidate) => candidate.datasetId === event.target.value,
                    ) ?? null
                  if (dataset) {
                    setComposer((current) => composerForDataset(dataset, current))
                  }
                }}
                value={composer.datasetId}
              >
                <option value="">Select a caller dataset</option>
                {datasets.map((dataset) => (
                  <option key={dataset.datasetId} value={dataset.datasetId}>
                    {dataset.fileName}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Training window label</span>
              <input
                onChange={(event) =>
                  setComposer((current) => ({
                    ...current,
                    trainingWindowLabel: event.target.value,
                  }))
                }
                placeholder="bootstrap_90d"
                value={composer.trainingWindowLabel}
              />
            </label>
            <label className="field">
              <span>Target column</span>
              <select
                onChange={(event) =>
                  setComposer((current) => ({
                    ...current,
                    targetColumn: event.target.value,
                    featureColumns: current.featureColumns.filter(
                      (column) => column !== event.target.value,
                    ),
                  }))
                }
                value={composer.targetColumn}
              >
                <option value="">Select target column</option>
                {selectedDataset?.headers.map((header) => (
                  <option key={header} value={header}>
                    {header}
                  </option>
                ))}
              </select>
            </label>
            <label className="checkbox">
              <input
                checked={composer.notifyOnCompletion}
                onChange={(event) =>
                  setComposer((current) => ({
                    ...current,
                    notifyOnCompletion: event.target.checked,
                  }))
                }
                type="checkbox"
              />
              <span>Notify this admin when training completes</span>
            </label>
          </div>

          <div className="section-heading section-heading--stack">
            <div>
              <p className="eyebrow">Feature columns</p>
              <h2>Choose what the model trains on</h2>
            </div>
          </div>
          <div className="chip-grid">
            {selectedDataset?.headers
              .filter((header) => header !== composer.targetColumn)
              .map((header) => {
                const selected = composer.featureColumns.includes(header)
                return (
                  <button
                    key={header}
                    className={selected ? 'trait-chip is-selected' : 'trait-chip'}
                    onClick={() =>
                      setComposer((current) => ({
                        ...current,
                        featureColumns: selected
                          ? current.featureColumns.filter((value) => value !== header)
                          : [...current.featureColumns, header],
                      }))
                    }
                    type="button"
                  >
                    {header}
                  </button>
                )
              })}
          </div>

          <div className="section-heading section-heading--stack">
            <div>
              <p className="eyebrow">Temporal traits</p>
              <h2>Select the training posture</h2>
            </div>
          </div>
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
              onClick={createTrainingJob}
              type="button"
            >
              {busyKey === 'create-job' ? 'Creating…' : 'Create training job'}
            </button>
          </div>
          <p className="callout">
            The uploaded CSV defines the training corpus. The selected target
            and feature columns are stored with the training job and the
            published active model.
          </p>
        </div>
      </section>

      <section className="dashboard-grid">
        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Published serving state</p>
              <h2>Caller-scoped active model</h2>
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
                label="Published at"
                value={formatInstant(activeModel.publishedAt)}
              />
              <JobDatum
                label="Release artifact"
                value={activeModel.releaseArtifactId}
              />
              <JobDatum
                label="Dataset"
                value={activeModel.datasetFileName ?? 'Telemetry-backed'}
              />
              <JobDatum
                label="Target column"
                value={activeModel.targetColumn ?? 'N/A'}
              />
              <JobDatum
                label="Feature columns"
                value={activeModel.featureColumns.join(', ') || 'N/A'}
              />
              <JobDatum
                label="Traits"
                value={activeModel.selectedTraits.join(', ')}
              />
            </div>
          ) : (
            <div className="empty-state">
              No caller-scoped model is published yet. The user workspace will
              stay blocked until a model is promoted here.
            </div>
          )}
        </div>

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Notifications</p>
              <h2>Training and publish events</h2>
            </div>
            <StatusPill
              label={notifications.length > 0 ? 'LIVE FEED' : 'QUIET'}
              tone={notifications.length > 0 ? 'good' : 'neutral'}
            />
          </div>
          <div className="job-list">
            {notifications.map((notification) => (
              <article className="job-card" key={notification.notificationId}>
                <div className="job-card__header">
                  <div>
                    <h3>{notification.title}</h3>
                    <p className="muted-text">{notification.detail}</p>
                  </div>
                  <StatusPill label={notification.type} tone="neutral" />
                </div>
                <div className="job-card__grid">
                  <JobDatum
                    label="Created"
                    value={formatInstant(notification.createdAt)}
                  />
                  <JobDatum
                    label="Dataset"
                    value={notification.relatedDatasetId ?? 'N/A'}
                  />
                  <JobDatum
                    label="Job"
                    value={notification.relatedJobId ?? 'N/A'}
                  />
                </div>
              </article>
            ))}
            {notifications.length === 0 ? (
              <div className="empty-state">
                Dataset uploads, completion notices, and publish events will
                appear here for this caller.
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section className="surface-card">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Tracked lifecycle</p>
            <h2>Monitor and act on training jobs</h2>
          </div>
          <div className="inline-actions">
            <label className="field field--compact">
              <span>Find tracked job</span>
              <input
                onChange={(event) => setJobSearch(event.target.value)}
                placeholder="Search by job, dataset, target, or status"
                value={jobSearch}
              />
            </label>
            <label className="field field--compact">
              <span>Attach existing job</span>
              <input
                onChange={(event) => setAttachJobId(event.target.value)}
                placeholder="retrain-…"
                value={attachJobId}
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
                      {job.datasetFileName
                        ? `${job.datasetFileName} → ${job.targetColumn ?? 'target'}`
                        : 'Telemetry-backed training'}
                    </p>
                  </div>
                  <StatusPill label={job.status} />
                </div>

                <div className="job-card__grid">
                  <JobDatum label="Window" value={job.trainingWindowLabel} />
                  <JobDatum
                    label="Traits"
                    value={job.selectedTraits.join(', ')}
                  />
                  <JobDatum
                    label="Features"
                    value={job.featureColumns.join(', ') || 'N/A'}
                  />
                  <JobDatum
                    label="Dataset rows"
                    value={formatCount(job.datasetRowCount)}
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
                          onChange={(event) =>
                            setCompletionDrafts((current) => ({
                              ...current,
                              [job.jobId]: {
                                ...completionDraft,
                                succeeded: event.target.value === 'success',
                              },
                            }))
                          }
                          value={completionDraft.succeeded ? 'success' : 'failure'}
                        >
                          <option value="success">Success</option>
                          <option value="failure">Failure</option>
                        </select>
                      </label>
                      {completionDraft.succeeded ? (
                        <label className="field field--compact">
                          <span>Release artifact</span>
                          <input
                            onChange={(event) =>
                              setCompletionDrafts((current) => ({
                                ...current,
                                [job.jobId]: {
                                  ...completionDraft,
                                  releaseArtifactId: event.target.value,
                                },
                              }))
                            }
                            placeholder="release-caller-a-v1"
                            value={completionDraft.releaseArtifactId}
                          />
                        </label>
                      ) : (
                        <label className="field field--compact">
                          <span>Failure reason</span>
                          <input
                            onChange={(event) =>
                              setCompletionDrafts((current) => ({
                                ...current,
                                [job.jobId]: {
                                  ...completionDraft,
                                  failureReason: event.target.value,
                                },
                              }))
                            }
                            placeholder="validation gate failed"
                            value={completionDraft.failureReason}
                          />
                        </label>
                      )}
                      <label className="field field--compact field--wide">
                        <span>Validation summary</span>
                        <input
                          onChange={(event) =>
                            setCompletionDrafts((current) => ({
                              ...current,
                              [job.jobId]: {
                                ...completionDraft,
                                validationSummary: event.target.value,
                              },
                            }))
                          }
                          placeholder="training completed and validation probes passed"
                          value={completionDraft.validationSummary}
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
              No tracked jobs for this caller yet. Upload a dataset and create a
              training job to begin the lifecycle.
            </div>
          ) : null}
        </div>
      </section>

      <section className="dashboard-grid">
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
              label="Route feedback count"
              value={formatCount(operations.metrics?.routeFeedback.requestCount)}
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

        <div className="surface-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Admin posture</p>
              <h2>What happens next</h2>
            </div>
            <StatusPill label="Embedded flow" tone="neutral" />
          </div>
          <p className="callout">
            After a publish, the same caller can move to the user workspace and
            query routes with only a start and end point. The query workspace
            blocks requests until an active model exists.
          </p>
          <div className="detail-list">
            <JobDatum
              label="Step 1"
              value="Upload CSV dataset and inspect headers"
            />
            <JobDatum
              label="Step 2"
              value="Choose target column, feature columns, and traits"
            />
            <JobDatum
              label="Step 3"
              value="Run, complete, and publish the training job"
            />
            <JobDatum
              label="Step 4"
              value="Use the published model in the user route workspace"
            />
          </div>
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

function pickTargetColumn(headers: string[]): string {
  if (headers.length === 0) {
    return ''
  }
  const targetMatcher =
    /(^travel[_-]?time$|^duration$|^cost$|^eta$|^label$|^target$)/i
  const matchingHeader =
    headers.find((header) => targetMatcher.test(header)) ?? headers.at(-1)
  return matchingHeader ?? ''
}

function composerForDataset(
  dataset: TrainingDatasetResponse,
  current: ComposerState,
): ComposerState {
  const targetColumn = pickTargetColumn(dataset.headers)
  const featureColumns = dataset.headers.filter((header) => header !== targetColumn)
  return {
    ...current,
    datasetId: dataset.datasetId,
    targetColumn,
    featureColumns,
  }
}

function reconcileComposer(
  current: ComposerState,
  datasets: TrainingDatasetResponse[],
): ComposerState {
  if (datasets.length === 0) {
    if (
      !current.datasetId &&
      !current.targetColumn &&
      current.featureColumns.length === 0
    ) {
      return current
    }
    return {
      ...current,
      datasetId: '',
      targetColumn: '',
      featureColumns: [],
    }
  }

  const selectedDataset =
    datasets.find((dataset) => dataset.datasetId === current.datasetId) ??
    datasets[0]
  const sameDataset = selectedDataset.datasetId === current.datasetId
  const targetColumn = selectedDataset.headers.includes(current.targetColumn)
    ? current.targetColumn
    : pickTargetColumn(selectedDataset.headers)
  const featureColumns = current.featureColumns.filter(
    (column) =>
      column !== targetColumn && selectedDataset.headers.includes(column),
  )
  const nextFeatureColumns =
    featureColumns.length > 0
      ? featureColumns
      : selectedDataset.headers.filter((header) => header !== targetColumn)

  if (
    sameDataset &&
    targetColumn === current.targetColumn &&
    sameStringList(nextFeatureColumns, current.featureColumns)
  ) {
    return current
  }

  return {
    ...current,
    datasetId: selectedDataset.datasetId,
    targetColumn,
    featureColumns: nextFeatureColumns,
  }
}

function sameStringList(left: string[], right: string[]): boolean {
  if (left.length !== right.length) {
    return false
  }
  return left.every((value, index) => value === right[index])
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

  const [
    datasetsResult,
    notificationsResult,
    healthResult,
    metricsResult,
    governanceResult,
  ] = await Promise.allSettled([
    client.datasets(12),
    client.notifications(12),
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
    datasets: datasetsResult.status === 'fulfilled' ? datasetsResult.value : [],
    notifications:
      notificationsResult.status === 'fulfilled' ? notificationsResult.value : [],
    jobs: nextJobs,
    jobIssues: nextJobIssues,
    operations: {
      health: healthResult.status === 'fulfilled' ? healthResult.value : null,
      metrics: metricsResult.status === 'fulfilled' ? metricsResult.value : null,
      governance:
        governanceResult.status === 'fulfilled' ? governanceResult.value : null,
      error:
        datasetsResult.status === 'rejected' ||
        notificationsResult.status === 'rejected' ||
        healthResult.status === 'rejected' ||
        metricsResult.status === 'rejected' ||
        governanceResult.status === 'rejected'
          ? 'One or more admin or operations endpoints are unavailable.'
          : null,
      refreshedAt: new Date().toISOString(),
    },
  }
}
