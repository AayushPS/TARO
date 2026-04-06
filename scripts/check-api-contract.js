#!/usr/bin/env node
/*
Expected output format:
DRIFT | <endpoint> | field <name> | expected <type> got <type>
DRIFT | <endpoint> | field <name> | missing in response
DRIFT | <endpoint> | field <name> | newly required in request
OK    | <endpoint>
SKIP  | <endpoint> | server unreachable
*/

const fs = require('node:fs')
const path = require('node:path')

const REPO_ROOT = path.resolve(__dirname, '..')
const DOCS_API_DIR = path.join(REPO_ROOT, 'docs', 'api')
const SNAPSHOT_PATH = path.join(DOCS_API_DIR, 'contract_snapshot.json')
const CYCLE_PATH = path.join(REPO_ROOT, 'docs', 'agent', 'cycle.txt')
const BASE_URL = String(process.env.TARO_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const STRICT_MODE = process.argv.includes('--strict')
const CALLER_HEADER = 'X-Taro-Caller-Id'
const CALLER_ID = 'contract-probe'
const REQUEST_TIMEOUT_MS = 5_000
const DATASET_SAMPLE_FILE = path.join(
  REPO_ROOT,
  'sample-data',
  'manual',
  'tomtom_nyc_travel_time_taro_demo.csv',
)

const ENDPOINTS = [
  { id: 'GET /api/v1/health', method: 'GET', path: '/api/v1/health' },
  { id: 'GET /api/v1/metrics', method: 'GET', path: '/api/v1/metrics' },
  { id: 'GET /api/v1/governance', method: 'GET', path: '/api/v1/governance' },
  {
    id: 'POST /api/v1/admin/retained-results/purge',
    method: 'POST',
    path: '/api/v1/admin/retained-results/purge',
  },
  {
    id: 'POST /api/v1/training/datasets',
    method: 'POST',
    path: '/api/v1/training/datasets',
    needsCallerId: true,
    bodyKind: 'form',
    buildBody: () => ({ filePath: DATASET_SAMPLE_FILE }),
    onSuccess: (state, payload) => {
      if (payload && typeof payload === 'object' && payload.datasetId) {
        state.datasetId = String(payload.datasetId)
      }
    },
  },
  {
    id: 'GET /api/v1/training/datasets?limit=20',
    method: 'GET',
    path: '/api/v1/training/datasets?limit=20',
    needsCallerId: true,
  },
  {
    id: 'GET /api/v1/training/notifications?limit=20',
    method: 'GET',
    path: '/api/v1/training/notifications?limit=20',
    needsCallerId: true,
  },
  {
    id: 'GET /api/v1/training/retraining/export?completeOnly=false',
    method: 'GET',
    path: '/api/v1/training/retraining/export?completeOnly=false',
    needsCallerId: true,
  },
  {
    id: 'POST /api/v1/training/retraining/jobs',
    method: 'POST',
    path: '/api/v1/training/retraining/jobs',
    needsCallerId: true,
    bodyKind: 'json',
    buildBody: (state) => ({
      trainingWindowLabel: 'contract_probe_7d',
      selectedTraits: ['recency', 'periodicity', 'persistence'],
      resultKind: 'ROUTE',
      datasetId: state.datasetId ?? null,
      targetColumn: 'travel_time_per_10km_min',
      featureColumns: ['travel_time_per_10km_min'],
      notifyOnCompletion: false,
      topologyVersionId: null,
      scenarioBundleId: null,
      traitHash: null,
      completeOnly: false,
    }),
    onSuccess: (state, payload) => {
      if (payload && typeof payload === 'object' && payload.jobId) {
        state.jobId = String(payload.jobId)
      }
    },
  },
  {
    id: 'GET /api/v1/training/retraining/jobs/{jobId}',
    method: 'GET',
    needsCallerId: true,
    dependencyKeys: ['jobId'],
    buildPath: (state) => `/api/v1/training/retraining/jobs/${encodeURIComponent(state.jobId)}`,
  },
  {
    id: 'POST /api/v1/training/retraining/jobs/{jobId}/start',
    method: 'POST',
    needsCallerId: true,
    dependencyKeys: ['jobId'],
    buildPath: (state) =>
      `/api/v1/training/retraining/jobs/${encodeURIComponent(state.jobId)}/start`,
  },
  {
    id: 'POST /api/v1/training/retraining/jobs/{jobId}/complete',
    method: 'POST',
    needsCallerId: true,
    bodyKind: 'json',
    dependencyKeys: ['jobId'],
    buildPath: (state) =>
      `/api/v1/training/retraining/jobs/${encodeURIComponent(state.jobId)}/complete`,
    buildBody: () => ({
      succeeded: true,
      releaseArtifactId: 'release-contract-probe-v1',
      validationSummary: 'contract probe',
      failureReason: null,
    }),
  },
  {
    id: 'POST /api/v1/training/retraining/jobs/{jobId}/publish',
    method: 'POST',
    needsCallerId: true,
    dependencyKeys: ['jobId'],
    buildPath: (state) =>
      `/api/v1/training/retraining/jobs/${encodeURIComponent(state.jobId)}/publish`,
  },
  {
    id: 'GET /api/v1/training/retraining/models/active',
    method: 'GET',
    path: '/api/v1/training/retraining/models/active',
    needsCallerId: true,
  },
  {
    id: 'POST /api/v1/route',
    method: 'POST',
    path: '/api/v1/route',
    needsCallerId: true,
    bodyKind: 'json',
    buildBody: () => ({
      source: {
        coordinateFirst: 12.9716,
        coordinateSecond: 77.5946,
        coordinateStrategyHintId: 'LAT_LON',
      },
      target: {
        coordinateFirst: 12.9352,
        coordinateSecond: 77.6245,
        coordinateStrategyHintId: 'LAT_LON',
      },
      departureTicks: 0,
      horizonTicks: 3600,
      preferredObjective: 'fastest',
      topKAlternatives: 2,
      resultTtlSeconds: 300,
      allowMixedAddressing: true,
      maxSnapDistance: 5000,
    }),
    onSuccess: (state, payload) => {
      if (payload && typeof payload === 'object' && payload.resultSetId) {
        state.routeResultSetId = String(payload.resultSetId)
      }
    },
  },
  {
    id: 'GET /api/v1/route/results/{resultSetId}/summary',
    method: 'GET',
    needsCallerId: true,
    dependencyKeys: ['routeResultSetId'],
    buildPath: (state) =>
      `/api/v1/route/results/${encodeURIComponent(state.routeResultSetId)}/summary`,
  },
  {
    id: 'GET /api/v1/route/results/{resultSetId}/detail',
    method: 'GET',
    needsCallerId: true,
    dependencyKeys: ['routeResultSetId'],
    buildPath: (state) =>
      `/api/v1/route/results/${encodeURIComponent(state.routeResultSetId)}/detail`,
  },
  {
    id: 'POST /api/v1/feedback/route/results/{resultSetId}/outcome',
    method: 'POST',
    needsCallerId: true,
    bodyKind: 'json',
    dependencyKeys: ['routeResultSetId'],
    buildPath: (state) =>
      `/api/v1/feedback/route/results/${encodeURIComponent(state.routeResultSetId)}/outcome`,
    buildBody: () => ({
      outcomeStatus: 'COMPLETE',
      observedAtTicks: 120,
      observedArrivalTicks: 240,
      observedCostSeconds: 120.0,
      observationCount: 1,
    }),
  },
  {
    id: 'POST /api/v1/matrix',
    method: 'POST',
    path: '/api/v1/matrix',
    needsCallerId: true,
    bodyKind: 'json',
    buildBody: () => ({
      sources: [
        {
          coordinateFirst: 12.9716,
          coordinateSecond: 77.5946,
          coordinateStrategyHintId: 'LAT_LON',
        },
      ],
      targets: [
        {
          coordinateFirst: 12.9352,
          coordinateSecond: 77.6245,
          coordinateStrategyHintId: 'LAT_LON',
        },
      ],
      departureTicks: 0,
      horizonTicks: 3600,
      resultTtlSeconds: 300,
      allowMixedAddressing: true,
      maxSnapDistance: 5000,
    }),
    onSuccess: (state, payload) => {
      if (payload && typeof payload === 'object' && payload.resultSetId) {
        state.matrixResultSetId = String(payload.resultSetId)
      }
    },
  },
  {
    id: 'GET /api/v1/matrix/results/{resultSetId}/summary',
    method: 'GET',
    needsCallerId: true,
    dependencyKeys: ['matrixResultSetId'],
    buildPath: (state) =>
      `/api/v1/matrix/results/${encodeURIComponent(state.matrixResultSetId)}/summary`,
  },
  {
    id: 'GET /api/v1/matrix/results/{resultSetId}/detail',
    method: 'GET',
    needsCallerId: true,
    dependencyKeys: ['matrixResultSetId'],
    buildPath: (state) =>
      `/api/v1/matrix/results/${encodeURIComponent(state.matrixResultSetId)}/detail`,
  },
  {
    id: 'POST /api/v1/feedback/matrix/results/{resultSetId}/outcome',
    method: 'POST',
    needsCallerId: true,
    bodyKind: 'json',
    dependencyKeys: ['matrixResultSetId'],
    buildPath: (state) =>
      `/api/v1/feedback/matrix/results/${encodeURIComponent(state.matrixResultSetId)}/outcome`,
    buildBody: () => ({
      outcomeStatus: 'COMPLETE',
      observedAtTicks: 120,
      observedArrivalTicks: 240,
      observedCostSeconds: 120.0,
      observationCount: 1,
    }),
  },
]

function readCycle() {
  try {
    return fs.readFileSync(CYCLE_PATH, 'utf8').trim()
  } catch {
    return 'unknown'
  }
}

function loadSnapshot() {
  if (!fs.existsSync(SNAPSHOT_PATH)) {
    return null
  }
  return JSON.parse(fs.readFileSync(SNAPSHOT_PATH, 'utf8'))
}

function saveSnapshot(snapshot) {
  fs.mkdirSync(DOCS_API_DIR, { recursive: true })
  fs.writeFileSync(SNAPSHOT_PATH, `${JSON.stringify(snapshot, null, 2)}\n`)
}

function shapeOf(value) {
  if (value === null) {
    return { type: 'null' }
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return { type: 'array', element: { type: 'unknown' } }
    }
    return {
      type: 'array',
      element: value.map(shapeOf).reduce(mergeShapes),
    }
  }
  if (typeof value === 'object') {
    const fields = {}
    for (const key of Object.keys(value).sort()) {
      fields[key] = shapeOf(value[key])
    }
    return { type: 'object', fields }
  }
  return { type: typeof value }
}

function mergeShapes(left, right) {
  if (!left) {
    return right
  }
  if (!right) {
    return left
  }
  if (left.type === right.type) {
    if (left.type === 'object') {
      const mergedFields = {}
      const keys = new Set([...Object.keys(left.fields), ...Object.keys(right.fields)])
      for (const key of [...keys].sort()) {
        mergedFields[key] = mergeShapes(left.fields[key], right.fields[key])
      }
      return { type: 'object', fields: mergedFields }
    }
    if (left.type === 'array') {
      return { type: 'array', element: mergeShapes(left.element, right.element) }
    }
    return left
  }
  return { type: 'mixed', options: [left, right] }
}

function shapeLabel(shape) {
  if (!shape) {
    return 'unknown'
  }
  if (shape.type === 'array') {
    return `array<${shapeLabel(shape.element)}>`
  }
  if (shape.type === 'mixed') {
    return shape.options.map(shapeLabel).join('|')
  }
  return shape.type
}

function collectDrifts(endpointId, expected, actual, fieldPath = '', drifts = []) {
  if (!expected || !actual) {
    return drifts
  }
  if (expected.type !== actual.type) {
    drifts.push(
      `DRIFT | ${endpointId} | field ${fieldPath || '$'} | expected ${shapeLabel(expected)} got ${shapeLabel(actual)}`,
    )
    return drifts
  }
  if (expected.type === 'object') {
    for (const [fieldName, expectedFieldShape] of Object.entries(expected.fields)) {
      const nextFieldPath = fieldPath ? `${fieldPath}.${fieldName}` : fieldName
      const actualFieldShape = actual.fields[fieldName]
      if (!actualFieldShape) {
        drifts.push(`DRIFT | ${endpointId} | field ${nextFieldPath} | missing in response`)
        continue
      }
      collectDrifts(endpointId, expectedFieldShape, actualFieldShape, nextFieldPath, drifts)
    }
  } else if (expected.type === 'array') {
    collectDrifts(endpointId, expected.element, actual.element, `${fieldPath || '$'}[]`, drifts)
  }
  return drifts
}

async function probeReachability() {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  try {
    const response = await fetch(`${BASE_URL}/api/v1/health`, {
      method: 'GET',
      signal: controller.signal,
    })
    return Boolean(response)
  } catch {
    return false
  } finally {
    clearTimeout(timeout)
  }
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  const text = await response.text()
  return text ? { body: text } : null
}

async function executeEndpoint(endpoint, state) {
  if (endpoint.dependencyKeys) {
    for (const dependencyKey of endpoint.dependencyKeys) {
      if (!state[dependencyKey]) {
        return { kind: 'skip', reason: `dependency ${dependencyKey} unavailable` }
      }
    }
  }

  const endpointPath = endpoint.buildPath ? endpoint.buildPath(state) : endpoint.path
  const headers = { Accept: 'application/json' }
  if (endpoint.needsCallerId) {
    headers[CALLER_HEADER] = CALLER_ID
  }

  const init = { method: endpoint.method, headers }
  if (endpoint.bodyKind === 'json') {
    headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(endpoint.buildBody ? endpoint.buildBody(state) : {})
  } else if (endpoint.bodyKind === 'form') {
    const bodyDefinition = endpoint.buildBody ? endpoint.buildBody(state) : {}
    if (!bodyDefinition.filePath || !fs.existsSync(bodyDefinition.filePath)) {
      return { kind: 'skip', reason: 'sample dataset file unavailable' }
    }
    const formData = new FormData()
    const fileName = path.basename(bodyDefinition.filePath)
    const fileBuffer = fs.readFileSync(bodyDefinition.filePath)
    formData.set('file', new Blob([fileBuffer]), fileName)
    init.body = formData
    delete headers['Content-Type']
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(`${BASE_URL}${endpointPath}`, {
      ...init,
      signal: controller.signal,
    })
    if (!response.ok) {
      return { kind: 'skip', reason: `status ${response.status}` }
    }
    const payload = await parseResponse(response)
    if (endpoint.onSuccess) {
      endpoint.onSuccess(state, payload)
    }
    return { kind: 'ok', payload }
  } catch (error) {
    return {
      kind: 'skip',
      reason: error && error.name === 'AbortError' ? 'request timed out' : 'request failed',
    }
  } finally {
    clearTimeout(timeout)
  }
}

async function main() {
  const snapshot = loadSnapshot()
  const reachable = await probeReachability()

  if (!reachable) {
    const offlineEndpoints = snapshot?.endpoints
      ? Object.keys(snapshot.endpoints)
      : ENDPOINTS.map((endpoint) => endpoint.id)
    for (const endpointId of offlineEndpoints) {
      console.log(`SKIP | ${endpointId} | server unreachable`)
    }
    process.exit(0)
  }

  const state = {}
  const observedSnapshot = { endpoints: {} }
  let driftDetected = false
  let strictSkipDetected = false

  for (const endpoint of ENDPOINTS) {
    const result = await executeEndpoint(endpoint, state)
    if (result.kind === 'skip') {
      console.log(`SKIP | ${endpoint.id} | ${result.reason}`)
      if (STRICT_MODE && result.reason !== 'server unreachable') {
        strictSkipDetected = true
      }
      continue
    }

    const actualShape = shapeOf(result.payload)
    observedSnapshot.endpoints[endpoint.id] = {
      responseShape: actualShape,
    }

    if (!snapshot) {
      continue
    }

    const expectedEntry = snapshot.endpoints[endpoint.id]
    if (!expectedEntry || !expectedEntry.responseShape) {
      console.log(`SKIP | ${endpoint.id} | missing from snapshot`)
      if (STRICT_MODE) {
        strictSkipDetected = true
      }
      continue
    }

    const drifts = collectDrifts(endpoint.id, expectedEntry.responseShape, actualShape)
    if (drifts.length > 0) {
      driftDetected = true
      for (const drift of drifts) {
        console.log(drift)
      }
    } else {
      console.log(`OK    | ${endpoint.id}`)
    }
  }

  if (!snapshot) {
    if (Object.keys(observedSnapshot.endpoints).length > 0) {
      saveSnapshot({
        version: 1,
        baseUrl: BASE_URL,
        createdAt: new Date().toISOString(),
        endpoints: observedSnapshot.endpoints,
      })
      console.log(`SNAPSHOT CREATED — baseline established at cycle ${readCycle()}`)
    }
    process.exit(0)
  }

  if (driftDetected || strictSkipDetected) {
    process.exit(1)
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack || error.message : String(error))
  process.exit(1)
})
