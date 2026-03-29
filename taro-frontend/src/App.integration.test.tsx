import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

interface DatasetRecord {
  datasetId: string
  fileName: string
  uploadedAt: string
  rowCount: number
  columnCount: number
  headers: string[]
  sampleRows: string[][]
  sha256: string
}

interface JobRecord {
  jobId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
  startedAt: string | null
  completedAt: string | null
  publishedAt: string | null
  trainingWindowLabel: string
  selectedTraits: string[]
  resultKind: 'ROUTE'
  datasetId: string | null
  datasetFileName: string | null
  datasetRowCount: number | null
  targetColumn: string | null
  featureColumns: string[]
  notifyOnCompletion: boolean
  topologyVersionId: string | null
  scenarioBundleId: string | null
  traitHash: string | null
  completeOnly: boolean
  exportRowCount: number
  completeExportRowCount: number
  basePublishedModelId: string | null
  releaseArtifactId: string | null
  validationSummary: string | null
  failureReason: string | null
  publishedModelId: string | null
}

interface NotificationRecord {
  notificationId: string
  type: string
  title: string
  detail: string
  createdAt: string
  relatedDatasetId: string | null
  relatedJobId: string | null
}

interface ActiveModelRecord {
  activeModelId: string
  sourceTrainingJobId: string
  releaseArtifactId: string
  publishedAt: string
  trainingWindowLabel: string
  selectedTraits: string[]
  resultKind: 'ROUTE'
  datasetId: string | null
  datasetFileName: string | null
  targetColumn: string | null
  featureColumns: string[]
  exportRowCount: number
  completeExportRowCount: number
}

interface ApiState {
  datasets: DatasetRecord[]
  jobs: Record<string, JobRecord>
  notifications: NotificationRecord[]
  activeModel: ActiveModelRecord | null
  nextDatasetId: number
  nextJobId: number
  nextNotificationId: number
}

const baseNow = '2026-03-29T12:00:00Z'

describe('TARO app integration flows', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.pushState({}, '', '/admin')
  })

  afterEach(() => {
    cleanup()
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('lets an admin upload a dataset, create a training job, complete it, and publish a model', async () => {
    const user = userEvent.setup()
    installMockApi(createEmptyState())
    render(<App />)

    await screen.findByText(/upload caller-scoped training data/i)

    const file = new File(
      [
        'region_index,region_label,time,speed_kmh,free_flow_speed_kmh,congestion_level_pct,travel_time_per_10km_min\n' +
          '0,Bronx County,2025-01-01T00:00,35.3,40.3,13.9,16.9\n',
      ],
      'tomtom-demo.csv',
      { type: 'text/csv' },
    )
    await user.upload(screen.getByLabelText(/csv file/i), file)
    await user.click(screen.getByRole('button', { name: /upload dataset/i }))

    await screen.findByText(/uploaded tomtom-demo\.csv with 3 rows\./i)
    await screen.findByRole('option', { name: 'tomtom-demo.csv' })

    await user.click(screen.getByRole('button', { name: /create training job/i }))

    await screen.findByText(/created training job retrain-1 for tomtom-demo\.csv\./i)
    await screen.findByRole('heading', { name: 'retrain-1' })

    await user.click(await screen.findByRole('button', { name: /^start$/i }))
    await screen.findByText(/job retrain-1 is now running\./i)

    const releaseArtifactInput = await screen.findByLabelText(/release artifact/i)
    await user.type(releaseArtifactInput, 'release-manual-demo-v1')
    await user.type(
      screen.getByLabelText(/validation summary/i),
      'validation probes passed',
    )
    await user.click(screen.getByRole('button', { name: /^complete$/i }))

    await screen.findByText(/job retrain-1 completed successfully\./i)

    await user.click(await screen.findByRole('button', { name: /^publish$/i }))

    await screen.findByText(/published published-1 from retrain-1\./i)
    await screen.findByRole('heading', { name: 'published-1' })
    await screen.findByRole('heading', { name: 'Model published' })
    expect(
      (await screen.findAllByText(/travel_time_per_10km_min/i)).length,
    ).toBeGreaterThan(0)
  })

  it('blocks the query UI when no published model exists for the caller', async () => {
    window.history.pushState({}, '', '/query')
    installMockApi(createEmptyState())
    render(<App />)

    await screen.findByText(/a published model is required before the thin query ui can serve\./i)
    const button = await screen.findByRole('button', {
      name: /awaiting published model/i,
    })
    expect(button).toBeDisabled()
    expect(
      screen.getByText(
        /no published model exists for this caller yet\. the admin workspace must upload, train, and publish one first\./i,
      ),
    ).toBeInTheDocument()
  })

  it('unlocks the query UI and renders route products when an active model exists', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/query')
    installMockApi(createPublishedState())
    render(<App />)

    await screen.findByText(/this caller is ready for routing queries\./i)
    const button = await screen.findByRole('button', { name: /route now/i })
    expect(button).toBeEnabled()

    await user.click(button)

    await screen.findByText(/computed route result result-1\./i)
    await screen.findByText(/^Expected ETA$/i)
    await screen.findByText(/^Robust \/ P90$/i)
    await screen.findByText(/^Alternative 1$/i)
    await screen.findByText(/scenario-level route outputs/i)
  })
})

function installMockApi(state: ApiState) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const requestUrl = new URL(url, 'http://localhost')
    const path = requestUrl.pathname
    const method = (init?.method ?? 'GET').toUpperCase()

    if (path === '/api/v1/training/datasets' && method === 'GET') {
      return jsonResponse(state.datasets)
    }
    if (path === '/api/v1/training/datasets' && method === 'POST') {
      const body = init?.body
      const file = body instanceof FormData ? body.get('file') : null
      const fileName = file instanceof File ? file.name : 'uploaded.csv'
      const dataset = createDataset(fileName, state.nextDatasetId++)
      state.datasets = [dataset, ...state.datasets]
      prependNotification(state, {
        type: 'DATASET_UPLOADED',
        title: 'Dataset uploaded',
        detail: `${fileName} with ${dataset.rowCount} rows is ready for training.`,
        relatedDatasetId: dataset.datasetId,
        relatedJobId: null,
      })
      return jsonResponse(dataset)
    }
    if (path === '/api/v1/training/notifications' && method === 'GET') {
      return jsonResponse(state.notifications)
    }
    if (path === '/api/v1/training/retraining/models/active' && method === 'GET') {
      if (state.activeModel) {
        return jsonResponse(state.activeModel)
      }
      return errorResponse(404, 'ACTIVE_MODEL_NOT_FOUND', 'active model not found')
    }
    if (path === '/api/v1/training/retraining/jobs' && method === 'POST') {
      const payload = parseJsonBody(init)
      const dataset =
        state.datasets.find((candidate) => candidate.datasetId === payload.datasetId) ??
        null
      const job = createJob(payload, dataset, state.nextJobId++)
      state.jobs[job.jobId] = job
      prependNotification(state, {
        type: 'TRAINING_JOB_CREATED',
        title: 'Training job created',
        detail: `${job.jobId} was created for ${job.trainingWindowLabel}.`,
        relatedDatasetId: job.datasetId,
        relatedJobId: job.jobId,
      })
      return jsonResponse(job)
    }
    if (path.startsWith('/api/v1/training/retraining/jobs/') && method === 'GET') {
      const jobId = path.split('/').at(-1) ?? ''
      return jsonResponse(state.jobs[jobId])
    }
    if (path.endsWith('/start') && method === 'POST') {
      const jobId = path.split('/')[6] ?? ''
      const current = state.jobs[jobId]
      state.jobs[jobId] = {
        ...current,
        status: 'RUNNING',
        startedAt: baseNow,
        updatedAt: baseNow,
      }
      return jsonResponse(state.jobs[jobId])
    }
    if (path.endsWith('/complete') && method === 'POST') {
      const jobId = path.split('/')[6] ?? ''
      const current = state.jobs[jobId]
      const payload = parseJsonBody(init)
      state.jobs[jobId] = {
        ...current,
        status: payload.succeeded ? 'SUCCEEDED' : 'FAILED',
        startedAt: current.startedAt ?? baseNow,
        completedAt: baseNow,
        updatedAt: baseNow,
        releaseArtifactId: asOptionalString(payload.releaseArtifactId),
        validationSummary: asOptionalString(payload.validationSummary),
        failureReason: asOptionalString(payload.failureReason),
      }
      if (state.jobs[jobId].notifyOnCompletion) {
        prependNotification(state, {
          type: 'TRAINING_JOB_COMPLETED',
          title: 'Training job finished',
          detail: payload.succeeded
            ? `${jobId} completed successfully.`
            : `${jobId} failed: ${payload.failureReason}`,
          relatedDatasetId: state.jobs[jobId].datasetId,
          relatedJobId: jobId,
        })
      }
      return jsonResponse(state.jobs[jobId])
    }
    if (path.endsWith('/publish') && method === 'POST') {
      const jobId = path.split('/')[6] ?? ''
      const current = state.jobs[jobId]
      const activeModel: ActiveModelRecord = {
        activeModelId: `published-${state.nextJobId - 1}`,
        sourceTrainingJobId: jobId,
        releaseArtifactId: current.releaseArtifactId ?? 'release-manual-demo-v1',
        publishedAt: baseNow,
        trainingWindowLabel: current.trainingWindowLabel,
        selectedTraits: current.selectedTraits,
        resultKind: 'ROUTE',
        datasetId: current.datasetId,
        datasetFileName: current.datasetFileName,
        targetColumn: current.targetColumn,
        featureColumns: current.featureColumns,
        exportRowCount: current.exportRowCount,
        completeExportRowCount: current.completeExportRowCount,
      }
      state.activeModel = activeModel
      state.jobs[jobId] = {
        ...current,
        status: 'PUBLISHED',
        publishedAt: baseNow,
        updatedAt: baseNow,
        publishedModelId: activeModel.activeModelId,
      }
      prependNotification(state, {
        type: 'MODEL_PUBLISHED',
        title: 'Model published',
        detail: `${activeModel.activeModelId} is now active for routing queries.`,
        relatedDatasetId: current.datasetId,
        relatedJobId: jobId,
      })
      return jsonResponse(activeModel)
    }
    if (path === '/api/v1/health' && method === 'GET') {
      return jsonResponse({
        status: 'UP',
        observedAt: baseNow,
        routeServiceConfigured: true,
        matrixServiceConfigured: true,
        activeTopologyVersion: 'topo-demo',
        quarantineSnapshotId: 'quarantine-topo-demo:0',
        callerScopedResultCount: 0,
        reloadHealth: 'HEALTHY',
        alertStatus: 'HEALTHY',
      })
    }
    if (path === '/api/v1/metrics' && method === 'GET') {
      return jsonResponse({
        status: 'UP',
        observedAt: baseNow,
        activeTopologyVersion: 'topo-demo',
        callerScopedResultCount: 0,
        routeEvaluations: {
          requestCount: 1,
          errorCount: 0,
          averageLatencyMillis: 12.3,
          maxLatencyMillis: 18.2,
          lastObservedAt: baseNow,
        },
        routeFeedback: {
          requestCount: 0,
          errorCount: 0,
          averageLatencyMillis: 0,
          maxLatencyMillis: 0,
          lastObservedAt: null,
        },
        reload: {
          validationSuccessCount: 1,
          validationFailureCount: 0,
          appliedReloadCount: 1,
          lastSuccessfulReloadAt: baseNow,
          lastSuccessfulTopologyVersion: 'topo-demo',
          lastFailureReason: null,
        },
        alerts: {
          overallStatus: 'HEALTHY',
          reloadStatus: 'HEALTHY',
          retainedResultPressureStatus: 'HEALTHY',
          routeLatencyStatus: 'HEALTHY',
          matrixLatencyStatus: 'HEALTHY',
          parityStatus: 'HEALTHY',
        },
      })
    }
    if (path === '/api/v1/governance' && method === 'GET') {
      return jsonResponse({
        observedAt: baseNow,
        activeTopologyVersion: 'topo-demo',
        currentOverallAlertStatus: 'HEALTHY',
        builderGovernance: {
          rolloutSequence: 'upload -> validate -> publish',
          rollbackTriggers: ['validation_failure'],
          rollbackAction: 'rollback builder state',
        },
        servingGovernance: {
          rolloutSequence: 'publish -> query',
          rollbackTriggers: ['health_regression'],
          rollbackAction: 'restore previous active model',
        },
      })
    }
    if (path === '/api/v1/route' && method === 'POST') {
      return jsonResponse(buildRouteEnvelope())
    }
    if (path === '/api/v1/route/results/result-1/detail' && method === 'GET') {
      return jsonResponse(buildRouteDetail())
    }
    if (
      path === '/api/v1/feedback/route/results/result-1/outcome' &&
      method === 'POST'
    ) {
      return jsonResponse({
        resultKind: 'ROUTE',
        resultSetId: 'result-1',
        outcomeStatus: 'COMPLETE',
        complete: true,
        feedbackRecordedAt: baseNow,
        topologyVersionId: 'topo-demo',
        scenarioBundleId: 'bundle-demo',
      })
    }

    throw new Error(`Unhandled request: ${method} ${path}`)
  })

  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function createEmptyState(): ApiState {
  return {
    datasets: [],
    jobs: {},
    notifications: [],
    activeModel: null,
    nextDatasetId: 1,
    nextJobId: 1,
    nextNotificationId: 1,
  }
}

function createPublishedState(): ApiState {
  const dataset = createDataset('tomtom_nyc_travel_time_taro_demo.csv', 1)
  const activeModel: ActiveModelRecord = {
    activeModelId: 'published-1',
    sourceTrainingJobId: 'retrain-1',
    releaseArtifactId: 'release-manual-demo-v1',
    publishedAt: baseNow,
    trainingWindowLabel: 'bootstrap_90d',
    selectedTraits: ['recency', 'periodicity', 'persistence'],
    resultKind: 'ROUTE',
    datasetId: dataset.datasetId,
    datasetFileName: dataset.fileName,
    targetColumn: 'travel_time_per_10km_min',
    featureColumns: ['speed_kmh', 'free_flow_speed_kmh', 'congestion_level_pct'],
    exportRowCount: 0,
    completeExportRowCount: 0,
  }
  return {
    datasets: [dataset],
    jobs: {},
    notifications: [],
    activeModel,
    nextDatasetId: 2,
    nextJobId: 2,
    nextNotificationId: 1,
  }
}

function createDataset(fileName: string, index: number): DatasetRecord {
  return {
    datasetId: `dataset-${index}`,
    fileName,
    uploadedAt: baseNow,
    rowCount: 3,
    columnCount: 7,
    headers: [
      'region_index',
      'region_label',
      'time',
      'speed_kmh',
      'free_flow_speed_kmh',
      'congestion_level_pct',
      'travel_time_per_10km_min',
    ],
    sampleRows: [
      ['0', 'Bronx County', '2025-01-01T00:00', '35.3', '40.3', '13.9', '16.9'],
      ['0', 'Bronx County', '2025-01-01T01:00', '36.2', '42.2', '16.6', '16.5'],
      ['0', 'Bronx County', '2025-01-01T02:00', '37.3', '42.1', '12.7', '16.0'],
    ],
    sha256: `sha256-${index}`,
  }
}

function createJob(
  payload: Record<string, unknown>,
  dataset: DatasetRecord | null,
  index: number,
): JobRecord {
  return {
    jobId: `retrain-${index}`,
    status: 'QUEUED',
    createdAt: baseNow,
    updatedAt: baseNow,
    startedAt: null,
    completedAt: null,
    publishedAt: null,
    trainingWindowLabel: String(payload.trainingWindowLabel),
    selectedTraits: (payload.selectedTraits as string[]) ?? [],
    resultKind: 'ROUTE',
    datasetId: dataset?.datasetId ?? null,
    datasetFileName: dataset?.fileName ?? null,
    datasetRowCount: dataset?.rowCount ?? null,
    targetColumn: String(payload.targetColumn ?? ''),
    featureColumns: (payload.featureColumns as string[]) ?? [],
    notifyOnCompletion: Boolean(payload.notifyOnCompletion),
    topologyVersionId: null,
    scenarioBundleId: null,
    traitHash: null,
    completeOnly: true,
    exportRowCount: 0,
    completeExportRowCount: 0,
    basePublishedModelId: null,
    releaseArtifactId: null,
    validationSummary: null,
    failureReason: null,
    publishedModelId: null,
  }
}

function prependNotification(
  state: ApiState,
  value: Omit<NotificationRecord, 'notificationId' | 'createdAt'>,
) {
  state.notifications = [
    {
      notificationId: `notification-${state.nextNotificationId++}`,
      createdAt: baseNow,
      ...value,
    },
    ...state.notifications,
  ]
}

function parseJsonBody(init?: RequestInit): Record<string, unknown> {
  if (!init?.body || typeof init.body !== 'string') {
    return {}
  }
  return JSON.parse(init.body) as Record<string, unknown>
}

function asOptionalString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

function errorResponse(status: number, code: string, message: string) {
  return jsonResponse(
    {
      code,
      status,
      message,
      path: '/api/mock',
      timestamp: baseNow,
    },
    status,
  )
}

function buildRouteEnvelope() {
  return {
    resultSetId: 'result-1',
    retained: true,
    expiresAt: '2026-03-29T12:10:00Z',
    topologyVersion: {
      modelVersion: 'model-demo',
      topologyVersion: 'topo-demo',
      generatedAt: baseNow,
      sourceDataLineageHash: 'lineage-topo-demo',
      changeSetHash: 'change-topo-demo',
    },
    summary: buildRouteSummary(),
  }
}

function buildRouteDetail() {
  return {
    summary: buildRouteSummary(),
    scenarioBundleGeneratedAt: baseNow,
    scenarioBundleValidUntil: '2026-03-29T12:10:00Z',
    scenarioBundleHorizonTicks: 3600,
    candidateDensityCalibrationReport: {
      coverageFloorApplied: false,
      selectedCandidateCount: 3,
      coverageFloorMinimumK: 1,
    },
    scenarioResults: [
      {
        scenarioId: 'baseline',
        label: 'baseline',
        probability: 0.6,
        route: {
          reachable: true,
          departureTicks: 0,
          algorithm: 'DIJKSTRA',
          heuristicType: 'NONE',
          pathExternalNodeIds: ['N0', 'N1', 'N3'],
        },
        explanationTags: [],
      },
      {
        scenarioId: 'incident_persists',
        label: 'incident_persists',
        probability: 0.4,
        route: {
          reachable: true,
          departureTicks: 0,
          algorithm: 'DIJKSTRA',
          heuristicType: 'NONE',
          pathExternalNodeIds: ['N0', 'N2', 'N3'],
        },
        explanationTags: ['incident_persists'],
      },
    ],
  }
}

function buildRouteSummary() {
  return {
    resultSetId: 'result-1',
    createdAt: baseNow,
    expiresAt: '2026-03-29T12:10:00Z',
    topologyVersion: {
      modelVersion: 'model-demo',
      topologyVersion: 'topo-demo',
      generatedAt: baseNow,
      sourceDataLineageHash: 'lineage-topo-demo',
      changeSetHash: 'change-topo-demo',
    },
    quarantineSnapshotId: 'quarantine-topo-demo:0',
    scenarioBundleId: 'bundle-demo',
    scenarioCount: 2,
    expectedRoute: buildSelection(['N0', 'N1', 'N3'], 'baseline', 120, 132),
    robustRoute: buildSelection(
      ['N0', 'N2', 'N3'],
      'incident_persists',
      128,
      140,
    ),
    alternatives: [buildSelection(['N0', 'N1', 'N2', 'N3'], 'baseline', 134, 145)],
  }
}

function buildSelection(
  pathExternalNodeIds: string[],
  dominantScenarioLabel: string,
  expectedCost: number,
  p90Cost: number,
) {
  return {
    route: {
      reachable: true,
      departureTicks: 0,
      algorithm: 'DIJKSTRA',
      heuristicType: 'NONE',
      pathExternalNodeIds,
    },
    expectedCost,
    p50Cost: expectedCost - 2,
    p90Cost,
    minCost: expectedCost - 10,
    maxCost: p90Cost + 8,
    minArrivalTicks: expectedCost,
    maxArrivalTicks: p90Cost,
    optimalityProbability: 0.6,
    expectedRegret: 8,
    etaBandLowerArrivalTicks: expectedCost,
    etaBandUpperArrivalTicks: p90Cost,
    dominantScenarioId: dominantScenarioLabel,
    dominantScenarioProbability: 0.6,
    dominantScenarioLabel,
    routeSelectionProvenance: 'SCENARIO_OPTIMAL',
    explanationTags: dominantScenarioLabel === 'baseline' ? [] : ['incident_persists'],
  }
}
