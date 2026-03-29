# TARO Client Training and Serving Guidance

This file supersedes the frontend-only guidance.

The next TARO product is not an operator map console. It is a client-facing
train-to-serve platform with two distinct user surfaces:

- a client admin surface for data intake, trait selection, training status, and publication
- a thin end-user query surface that asks only for start and end points and returns the three route products

The three route products remain:

- Expected ETA
- Robust / P90
- Top-K materially distinct alternatives

## 1. Product Direction

The required end-to-end flow is:

1. A client submits routing data for their environment.
2. The client selects the trait/runtime options that should govern their system.
3. TARO validates the data and initializes a tenant-scoped training job.
4. The offline learning pipeline runs to completion.
5. When training passes validation, TARO publishes a serving artifact for that client.
6. The client is notified that the model is ready.
7. A tenant-scoped routing API is exposed for that client's downstream users.
8. The downstream user interface asks only for origin and destination and shows the three route products.

That lifecycle must include retraining, not just first-time training:

9. Served predictions and outcome feedback are exported as caller-scoped telemetry.
10. A retraining job is created from that telemetry, validated, and published as the next serving artifact.
11. Publication advances the caller's active model version without request-time training.

Do not optimize the repo around an ops-heavy frontend before this lifecycle exists.

## 2. System Planes

### 2.1 Control Plane

Owns:

- client / tenant registration
- dataset intake
- trait selection
- training-job creation
- retraining-job creation from served telemetry
- job status tracking
- publication approval
- active-model selection
- client notification metadata

This plane is the new primary product surface.

### 2.2 Training Plane

Owns the existing offline Python pipeline:

- `E1` ingestion
- `E2` dataset + temporal feature construction
- `E3` deterministic training / representation learning
- `E4` calibration / selection
- `E5` reproducibility pack publication

Training must remain offline and out-of-band from route serving.
No request-time training logic is allowed in the Java routing path.

### 2.3 Serving Plane

Owns:

- published tenant-scoped routing artifacts
- tenant-scoped route query API
- retained result retrieval
- feedback ingestion
- telemetry export for retraining

Serving may emit telemetry for retraining, but it may not run training inline.

Serving remains deterministic for a fixed published artifact and live snapshot.

### 2.4 User Query Surface

This is intentionally thin.

Required input:

- start point
- end point

Required output:

- expected route
- robust / P90 route
- alternatives bundle

Do not rebuild a large operator console as the next milestone.

## 3. Repository Direction

The repo already contains:

- Java serving/runtime foundations under `src/main/java`
- Python offline learning pipeline under `src/main/python`
- verification, training, and audit records under `docs/verification`, `docs/training`, and `docs/audit`

The next architectural additions should focus on:

- tenant/project domain models
- dataset upload and validation API
- training job orchestration
- retraining orchestration from caller-scoped telemetry exports
- publication lifecycle
- active-model metadata and serving activation
- tenant-scoped auth / API key model
- minimal client admin UI
- minimal end-user route query UI

## 4. Immediate Build Priorities

Implement in this order unless the user explicitly changes direction:

1. Control-plane backend contracts
2. Training job orchestration and status persistence
3. Publication + tenant-scoped serving activation
4. Retraining loop from served telemetry
5. Client notification path
6. Thin route query UI

## 5. Backend Contract Priorities

The next backend families should be centered on training and publication:

- client / tenant registration
- dataset upload / manifest creation
- trait selection / training config
- training job start / status / failure reporting
- retraining export / start / status / publish reporting
- artifact publication / active-model selection
- notification webhook or pollable completion contract
- tenant-scoped routing API

Operational APIs like traffic consoles or map-heavy tooling are secondary.

## 6. UI Rules

Two UIs are acceptable in the next phase:

### 6.1 Client Admin UI

This UI may expose:

- dataset upload
- trait selection
- training status
- publication status
- readiness notification state

### 6.2 End-User Route UI

This UI must stay minimal:

- origin
- destination
- returned route products

It should not expose training controls, topology operations, or calibration internals.

## 7. Non-Negotiable Constraints

1. Runtime determinism remains intact.
2. Training stays offline.
3. Published artifacts are versioned and tenant-scoped.
4. Feedback and telemetry remain exportable for retraining.
5. Retraining is a first-class backend lifecycle, not a frontend concern.
6. The end-user query surface stays simpler than the client admin surface.

## 8. Out of Scope For The Next Slice

Do not spend the next slice on:

- a broad ops map console
- traffic handler dashboards
- placeholder-heavy frontend shells disconnected from training/publication

The missing product is the training-to-serving lifecycle, not another visualization layer.
