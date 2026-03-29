export type ResultKind = 'ROUTE' | 'MATRIX'

export type RetrainingJobStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'PUBLISHED'

export type OutcomeStatus = 'PARTIAL' | 'COMPLETE'

export interface ApiErrorResponse {
  code: string
  status: number
  message: string
  path: string
  timestamp: string
}

export interface TopologyVersion {
  modelVersion: string
  topologyVersion: string
  generatedAt: string
  sourceDataLineageHash: string
  changeSetHash: string
}

export interface RouteEndpointPayload {
  externalId?: string
  coordinateFirst?: number
  coordinateSecond?: number
  coordinateStrategyHintId?: string
}

export interface RouteApiRequestPayload {
  source: RouteEndpointPayload
  target: RouteEndpointPayload
  departureTicks: number
  horizonTicks: number
  preferredObjective: string
  topKAlternatives: number
  resultTtlSeconds: number
}

export interface RouteShape {
  reachable: boolean
  departureTicks: number
  algorithm: string
  heuristicType: string
  pathExternalNodeIds: string[]
}

export interface RouteSelection {
  route: RouteShape
  expectedCost: number
  p50Cost: number
  p90Cost: number
  minCost: number
  maxCost: number
  minArrivalTicks: number
  maxArrivalTicks: number
  optimalityProbability: number
  expectedRegret: number
  etaBandLowerArrivalTicks: number
  etaBandUpperArrivalTicks: number
  dominantScenarioId: string
  dominantScenarioProbability: number
  dominantScenarioLabel: string
  routeSelectionProvenance: string
  explanationTags: string[]
}

export interface RouteSummary {
  resultSetId: string
  createdAt: string
  expiresAt: string
  topologyVersion: TopologyVersion
  quarantineSnapshotId: string
  scenarioBundleId: string
  scenarioCount: number
  expectedRoute: RouteSelection
  robustRoute: RouteSelection
  alternatives: RouteSelection[]
}

export interface RouteScenarioResult {
  scenarioId: string
  label: string
  probability: number
  route: RouteShape
  explanationTags: string[]
}

export interface CandidateDensityCalibrationReport {
  coverageFloorApplied?: boolean
  selectedCandidateCount?: number
  coverageFloorMinimumK?: number
}

export interface RouteDetail {
  summary: RouteSummary
  scenarioBundleGeneratedAt: string
  scenarioBundleValidUntil: string
  scenarioBundleHorizonTicks: number
  candidateDensityCalibrationReport?: CandidateDensityCalibrationReport | null
  scenarioResults: RouteScenarioResult[]
}

export interface RouteApiResponse {
  resultSetId: string
  retained: boolean
  expiresAt: string
  topologyVersion: TopologyVersion
  summary: RouteSummary
}

export interface PredictionFeedbackRequestPayload {
  outcomeStatus: OutcomeStatus
  observedAtTicks?: number
  observedArrivalTicks?: number
  observedCostSeconds?: number
  observationCount?: number
}

export interface PredictionFeedbackResponse {
  resultKind: ResultKind
  resultSetId: string
  outcomeStatus: OutcomeStatus
  complete: boolean
  feedbackRecordedAt: string
  topologyVersionId: string
  scenarioBundleId: string
}

export interface TelemetryRow {
  resultKind: ResultKind
  predictionId: string
  resultSetId: string
  servedAt: string
  feedbackRecordedAt?: string | null
  outcomeStatus?: string | null
  complete: boolean
  callerHash: string
  topologyVersionId: string
  modelVersion: string
  sourceDataLineageHash: string
  changeSetHash: string
  traitBundleId?: string | null
  traitHash?: string | null
  executionProfileId?: string | null
  quarantineSnapshotId?: string | null
  scenarioBundleId?: string | null
  scenarioCount: number
  scenarioIds: string[]
  scenarioLabels: string[]
  scenarioProbabilities: number[]
  departureTicks?: number | null
  horizonTicks?: number | null
  preferredObjective?: string | null
  topKAlternatives?: number | null
  predictedExpectedCostSeconds?: number | null
  predictedRobustCostSeconds?: number | null
  observedAtTicks?: number | null
  observedArrivalTicks?: number | null
  observedCostSeconds?: number | null
  observationCount?: number | null
  partitionDate: string
}

export interface RetrainingTelemetryExportResponse {
  rowCount: number
  completeRowCount: number
  rows: TelemetryRow[]
}

export interface RetrainingJobCreateRequestPayload {
  trainingWindowLabel: string
  selectedTraits: string[]
  resultKind?: ResultKind
  datasetId?: string
  targetColumn?: string
  featureColumns?: string[]
  notifyOnCompletion?: boolean
  topologyVersionId?: string
  scenarioBundleId?: string
  traitHash?: string
  completeOnly: boolean
}

export interface RetrainingJobCompletionRequestPayload {
  succeeded: boolean
  releaseArtifactId?: string
  validationSummary?: string
  failureReason?: string
}

export interface RetrainingJobResponse {
  jobId: string
  status: RetrainingJobStatus
  createdAt: string
  updatedAt: string
  startedAt?: string | null
  completedAt?: string | null
  publishedAt?: string | null
  trainingWindowLabel: string
  selectedTraits: string[]
  resultKind?: ResultKind | null
  datasetId?: string | null
  datasetFileName?: string | null
  datasetRowCount?: number | null
  targetColumn?: string | null
  featureColumns: string[]
  notifyOnCompletion: boolean
  topologyVersionId?: string | null
  scenarioBundleId?: string | null
  traitHash?: string | null
  completeOnly: boolean
  exportRowCount: number
  completeExportRowCount: number
  basePublishedModelId?: string | null
  releaseArtifactId?: string | null
  validationSummary?: string | null
  failureReason?: string | null
  publishedModelId?: string | null
}

export interface PublishedServingModelResponse {
  activeModelId: string
  sourceTrainingJobId: string
  releaseArtifactId: string
  publishedAt: string
  trainingWindowLabel: string
  selectedTraits: string[]
  resultKind: ResultKind
  datasetId?: string | null
  datasetFileName?: string | null
  targetColumn?: string | null
  featureColumns: string[]
  exportRowCount: number
  completeExportRowCount: number
}

export interface TrainingDatasetResponse {
  datasetId: string
  fileName: string
  uploadedAt: string
  rowCount: number
  columnCount: number
  headers: string[]
  sampleRows: string[][]
  sha256: string
}

export interface AdminNotificationResponse {
  notificationId: string
  type: string
  title: string
  detail: string
  createdAt: string
  relatedDatasetId?: string | null
  relatedJobId?: string | null
}

export interface HealthApiResponse {
  status: string
  observedAt: string
  routeServiceConfigured: boolean
  matrixServiceConfigured: boolean
  activeTopologyVersion: string
  quarantineSnapshotId: string
  callerScopedResultCount: number
  reloadHealth: string
  alertStatus: string
}

export interface OperationalMetricsResponse {
  status: string
  observedAt: string
  activeTopologyVersion: string
  callerScopedResultCount: number
  routeEvaluations: OperationMetrics
  routeFeedback: OperationMetrics
  reload: ReloadMetrics
  alerts: AlertSummary
}

export interface OperationMetrics {
  requestCount: number
  errorCount: number
  averageLatencyMillis: number
  maxLatencyMillis: number
  lastObservedAt?: string | null
}

export interface ReloadMetrics {
  validationSuccessCount: number
  validationFailureCount: number
  appliedReloadCount: number
  lastSuccessfulReloadAt?: string | null
  lastSuccessfulTopologyVersion?: string | null
  lastFailureReason?: string | null
}

export interface AlertSummary {
  overallStatus: string
  reloadStatus: string
  retainedResultPressureStatus: string
  routeLatencyStatus: string
  matrixLatencyStatus: string
  parityStatus: string
}

export interface OperationalGovernanceResponse {
  observedAt: string
  activeTopologyVersion: string
  currentOverallAlertStatus: string
  builderGovernance: GovernancePlan
  servingGovernance: GovernancePlan
}

export interface GovernancePlan {
  rolloutSequence: string
  rollbackTriggers: string[]
  rollbackAction: string
}
