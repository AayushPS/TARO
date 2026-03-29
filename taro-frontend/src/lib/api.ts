import type {
  ApiErrorResponse,
  HealthApiResponse,
  OperationalGovernanceResponse,
  OperationalMetricsResponse,
  PredictionFeedbackRequestPayload,
  PredictionFeedbackResponse,
  PublishedServingModelResponse,
  RetrainingJobCompletionRequestPayload,
  RetrainingJobCreateRequestPayload,
  RetrainingJobResponse,
  RetrainingTelemetryExportResponse,
  ResultKind,
  RouteApiRequestPayload,
  RouteApiResponse,
  RouteDetail,
} from './types'

type QueryValue = string | number | boolean | null | undefined

export class ApiFailure extends Error {
  readonly status?: number
  readonly code?: string
  readonly payload?: ApiErrorResponse

  constructor(message: string, payload?: ApiErrorResponse) {
    super(message)
    this.name = 'ApiFailure'
    this.status = payload?.status
    this.code = payload?.code
    this.payload = payload
  }
}

export function describeError(error: unknown): string {
  if (error instanceof ApiFailure) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Unexpected error'
}

export class TaroApiClient {
  private readonly baseUrl: string
  private readonly callerId: string

  constructor(baseUrl: string, callerId: string) {
    this.baseUrl = baseUrl.trim()
    this.callerId = callerId.trim()
  }

  health(): Promise<HealthApiResponse> {
    return this.request('/api/v1/health')
  }

  metrics(): Promise<OperationalMetricsResponse> {
    return this.request('/api/v1/metrics')
  }

  governance(): Promise<OperationalGovernanceResponse> {
    return this.request('/api/v1/governance')
  }

  exportTelemetry(filters: {
    resultKind?: ResultKind
    topologyVersionId?: string
    scenarioBundleId?: string
    traitHash?: string
    completeOnly: boolean
  }): Promise<RetrainingTelemetryExportResponse> {
    return this.request(
      `/api/v1/training/retraining/export${toQueryString({
        resultKind: filters.resultKind,
        topologyVersionId: filters.topologyVersionId,
        scenarioBundleId: filters.scenarioBundleId,
        traitHash: filters.traitHash,
        completeOnly: filters.completeOnly,
      })}`,
      {
        headers: this.callerHeaders(),
      },
    )
  }

  createRetrainingJob(
    payload: RetrainingJobCreateRequestPayload,
  ): Promise<RetrainingJobResponse> {
    return this.request('/api/v1/training/retraining/jobs', {
      method: 'POST',
      headers: this.jsonCallerHeaders(),
      body: JSON.stringify(payload),
    })
  }

  retrainingJob(jobId: string): Promise<RetrainingJobResponse> {
    return this.request(
      `/api/v1/training/retraining/jobs/${encodeURIComponent(jobId)}`,
      { headers: this.callerHeaders() },
    )
  }

  startRetrainingJob(jobId: string): Promise<RetrainingJobResponse> {
    return this.request(
      `/api/v1/training/retraining/jobs/${encodeURIComponent(jobId)}/start`,
      {
        method: 'POST',
        headers: this.callerHeaders(),
      },
    )
  }

  completeRetrainingJob(
    jobId: string,
    payload: RetrainingJobCompletionRequestPayload,
  ): Promise<RetrainingJobResponse> {
    return this.request(
      `/api/v1/training/retraining/jobs/${encodeURIComponent(jobId)}/complete`,
      {
        method: 'POST',
        headers: this.jsonCallerHeaders(),
        body: JSON.stringify(payload),
      },
    )
  }

  publishRetrainingJob(jobId: string): Promise<PublishedServingModelResponse> {
    return this.request(
      `/api/v1/training/retraining/jobs/${encodeURIComponent(jobId)}/publish`,
      {
        method: 'POST',
        headers: this.callerHeaders(),
      },
    )
  }

  activeModel(): Promise<PublishedServingModelResponse> {
    return this.request('/api/v1/training/retraining/models/active', {
      headers: this.callerHeaders(),
    })
  }

  route(payload: RouteApiRequestPayload): Promise<RouteApiResponse> {
    return this.request('/api/v1/route', {
      method: 'POST',
      headers: this.jsonCallerHeaders(),
      body: JSON.stringify(payload),
    })
  }

  routeDetail(resultSetId: string): Promise<RouteDetail> {
    return this.request(
      `/api/v1/route/results/${encodeURIComponent(resultSetId)}/detail`,
      {
        headers: this.callerHeaders(),
      },
    )
  }

  submitRouteFeedback(
    resultSetId: string,
    payload: PredictionFeedbackRequestPayload,
  ): Promise<PredictionFeedbackResponse> {
    return this.request(
      `/api/v1/feedback/route/results/${encodeURIComponent(resultSetId)}/outcome`,
      {
        method: 'POST',
        headers: this.jsonCallerHeaders(),
        body: JSON.stringify(payload),
      },
    )
  }

  private callerHeaders(): HeadersInit {
    if (!this.callerId) {
      throw new ApiFailure('Caller ID is required for caller-scoped TARO endpoints.')
    }
    return {
      'X-Taro-Caller-Id': this.callerId,
    }
  }

  private jsonCallerHeaders(): HeadersInit {
    return {
      ...this.callerHeaders(),
      'Content-Type': 'application/json',
    }
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response

    try {
      response = await fetch(this.resolveUrl(path), {
        ...init,
        headers: {
          Accept: 'application/json',
          ...(init?.headers ?? {}),
        },
      })
    } catch {
      throw new ApiFailure('Could not reach the TARO API.')
    }

    const contentType = response.headers.get('content-type') ?? ''
    const isJson = contentType.includes('application/json')
    const payload = isJson ? ((await response.json()) as T | ApiErrorResponse) : null

    if (!response.ok) {
      if (payload && isApiErrorResponse(payload)) {
        throw new ApiFailure(payload.message, payload)
      }
      throw new ApiFailure(`TARO API request failed with status ${response.status}.`)
    }

    return payload as T
  }

  private resolveUrl(path: string): string {
    if (!this.baseUrl) {
      return path
    }
    const normalizedBase = this.baseUrl.replace(/\/$/, '')
    if (normalizedBase.endsWith('/api') && path.startsWith('/api/')) {
      return `${normalizedBase}${path.slice('/api'.length)}`
    }
    return `${normalizedBase}${path}`
  }
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') {
    return false
  }
  return 'code' in value && 'message' in value
}

function toQueryString(values: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue
    }
    params.set(key, String(value))
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}
