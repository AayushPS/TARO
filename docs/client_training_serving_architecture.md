# TARO Client Training to Serving Architecture

Date: 2026-03-29
Status: active direction

## Objective

The next TARO build must support the full client lifecycle:

1. client submits routing data
2. client selects trait/runtime options
3. TARO trains and validates a tenant-scoped model offline
4. TARO publishes the serving artifact
5. client is notified when training completes
6. client's downstream users query routing through a thin API/UI

This replaces the previous frontend-first direction.

## Core Planes

### Control Plane

Purpose:

- onboard client datasets
- capture training configuration
- create and track training jobs
- manage publication state

Primary entities:

- `ClientAccount`
- `TenantProject`
- `DatasetSubmission`
- `TraitSelection`
- `TrainingJob`
- `PublishedModel`

### Training Plane

Purpose:

- execute the existing Python `E1 -> E5` pipeline for one tenant/project

Execution stages:

- `E1` validate and manifest raw input data
- `E2` build sequence and corridor-bucket artifacts
- `E3` train deterministic forecast/representation artifacts
- `E4` calibrate confidence-qualified priors
- `E5` emit reproducibility and publication evidence

### Serving Plane

Purpose:

- activate one published model per tenant/project
- answer future-aware route queries deterministically
- retain result sets
- capture prediction feedback

### User Query Plane

Purpose:

- provide a minimal interface for downstream users

Input:

- origin
- destination

Output:

- expected ETA route
- robust / P90 route
- alternative routes

## Required Workflow

### 1. Client Intake

The client submits:

- topology or corridor data
- temporal telemetry / incident history
- any required profile metadata
- desired trait/runtime posture

Validation must occur before any training job starts.

### 2. Training Job Creation

Once intake passes validation:

- create a `TrainingJob`
- pin lineage, config, and trait selection
- dispatch offline training

The Java serving thread must never execute this training inline.

### 3. Training Completion

Training ends in one of:

- `SUCCEEDED`
- `FAILED`
- `REJECTED`

Successful jobs produce:

- reproducibility pack
- publishable serving artifact reference
- validation evidence

### 4. Publication

Publication promotes a successful training output into the tenant's active serving model.

Only published models may back the tenant-scoped routing API.

### 5. Notification

When job state changes, the client must be able to learn that through:

- polling status API
- webhook callback

Either is acceptable initially; polling is the simpler first implementation.

### 6. Downstream Routing

After publication:

- tenant-scoped route API is live
- downstream user UI asks only for start and end
- results show expected, robust/P90, and alternatives

## Initial API Families To Build

### Control Plane APIs

- create tenant/project
- upload dataset
- submit trait selection
- create training job
- fetch training job status
- publish successful model
- fetch active model metadata

### Serving APIs

- tenant-scoped route query
- retained result summary/detail
- feedback ingestion

## What Not To Build First

Avoid spending the next implementation slice on:

- map-heavy operator tooling
- traffic operations dashboards
- placeholder UIs for missing infra APIs

Those are secondary to the core client training-to-serving loop.
