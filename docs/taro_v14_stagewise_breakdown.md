# TARO v14 Canonical Temporal-Aware Stage Breakdown

Status: Proposed canonical staged roadmap
Date: 2026-03-22
Companion Documents:
- `docs/taro_v13_architecture_plan.md`
- `docs/taro_v12_architecture_plan.md`
- `docs/taro_v11_architecture_plan.md`
- `docs/taro_v12_v13_migration_report.md`
- `ResearchData/2510.09416v1.pdf`

## 1. Objective

This document replaces the old deterministic-first stage framing with a canonical staged roadmap for TARO that integrates:

- the v11 offline-learning and compile-time refinement direction,
- the v12 future-aware serving model,
- the v13 topology evolution and atomic reload model,
- and interpretability-driven temporal competency lessons from `ResearchData/2510.09416v1.pdf`.

The goal is not to erase v11-v13. The goal is to create one decision-complete staging reference that:

- reflects the codebase as it exists now,
- names what is already implemented, partially implemented, or still planned,
- promotes temporal competency to an explicit gated subsystem,
- and prevents builder-time temporal correctness from silently regressing in serving or reload flows.

## 2. Why The Older v11 Breakdown Is No Longer Sufficient

The old `docs/taro_v11_ssot_stagewise_breakdown.md` remains useful as a historical v11-only baseline, but it is no longer adequate as TARO's primary staged roadmap.

It is insufficient because it predates:

- v12 scenario-aware future routing, robust/P90 routing, and retained-result serving,
- v13 failure quarantine, topology publication, and atomic reload continuity,
- direct multi-scenario objective search for expected and robust winners,
- and paper-driven temporal competency concerns around recency, directionality, density, persistence, and periodicity.

Most importantly, the older breakdown assumes that temporal correctness is mostly covered by profile storage, FIFO checks, and deterministic runtime contracts. The paper shows that this assumption is unsafe:

- direction and density can fail universally while benchmarked models still appear strong,
- recency can fail universally while future prediction still looks plausible,
- persistence and periodicity can partially fail in exactly the traffic regimes TARO needs for robust/P90 routing,
- and builder-time temporal quality can degrade silently before any runtime determinism guarantee has a chance to help.

The v14 breakdown therefore makes temporal competency an explicit phase with blocker-level gates and cross-phase contract tests.

## 3. Design Axioms

The staged roadmap in this document follows these axioms.

1. Runtime determinism remains non-negotiable.
2. Temporal competency is validated separately from deterministic runtime correctness.
3. Future-aware routing must expose uncertainty honestly rather than hide it in one implicit forecast.
4. Structural change is handled by quarantine plus batched rebuild and atomic reload, not in-place mutation.
5. Offline learning remains outside the runtime hot path.
6. Paper-driven temporal attributes are used as competency and calibration lenses, not as product contracts by themselves.
7. Cross-phase contract tests are required wherever a temporal property could pass locally and regress later in the pipeline.

## 4. Gate Severity Definitions

Each stage has one gate severity.

- `hard blocker`: cannot be claimed green for release scope until all closure criteria pass
- `release-critical`: must pass for releases that depend on that feature family; may remain incomplete only if the dependent milestone is explicitly out of scope
- `calibration gate`: may ship only with bounded uncertainty semantics and documented calibration posture
- `advisory`: informative and tracked, but not a release gate

Paper-driven severity posture:

| Temporal attribute from paper | TARO posture | Reason |
|---|---|---|
| temporal granularity | release-critical | discretization drift can quietly flatten time behavior |
| direction | hard blocker | one-way roads, asymmetric corridors, and turn-sensitive costs are semantically directional |
| density | hard blocker | sparse candidate or prior collapse can hide aggregate-best routes and distort scenario coverage |
| persistence | release-critical | always-on or long-lived congestion must remain representable |
| periodicity | release-critical | rush-hour and recurring-incident behavior directly affect expected and robust outputs |
| recency | hard blocker | near-horizon forecasting is invalid if fresh evidence is not weighted above stale evidence |
| homophily | calibration gate / modeling signal | useful for modeling context but not a release blocker by default |
| preferential attachment | positive prior signal in `E4` | high-degree or high-traffic corridors should influence priors when evidence supports it |

## 5. Stage Entry Schema

Every stage in this document contains the following sections:

- core purpose
- current repo status
- gate severity
- functional requirements
- non-functional requirements
- named test suites
- equivalence classes
- dependencies
- shared contract dependencies
- closure criteria
- repo anchors

Rule:

- if a stage depends on a cross-phase contract, that contract suite must appear directly in the stage's `named test suites` and `closure criteria`

## 6. Phase Overview

| Phase | Scope | Why it exists now |
|---|---|---|
| Phase A | deterministic runtime bedrock | preserves fixed-snapshot correctness and startup-bound semantics |
| Phase B | temporal competency gates | validates that temporal meaning survives beyond simple deterministic execution |
| Phase C | future-aware serving | materializes scenarios, computes aggregate winners, and serves retained results |
| Phase D | topology evolution and snapshot continuity | preserves correctness across failures, rebuilds, and reloads |
| Phase E | offline learning and forecast intelligence | builds the evidence and priors that future-aware serving consumes |
| Phase F | product surface and production loop | exposes retained results, telemetry, and operations posture |

## 7. Current Repo Status Snapshot

| Phase | Current posture in this repo | Main gap |
|---|---|---|
| Phase A | implemented foundation | continue maintainability and non-regression work only |
| Phase B | partially implemented at the primitive/runtime level, but not closed as competency gates | missing explicit recency, directionality, density, persistence, and periodicity closure suites |
| Phase C | partially implemented foundation | API retrieval and richer scenario generation remain open |
| Phase D | partially implemented foundation | temporal continuity across reload is not yet fully expressed as contract tests |
| Phase E | planned with only early helper scaffolding | no canonical ingestion, learning, calibration, or prior-publication pipeline |
| Phase F | planned | no HTTP/API layer, telemetry loop, or observability stack |

## 8. Crosswalk To Legacy Stages And Current Migration Steps

| New stage | Legacy v11 stage(s) | Current v12/v13 migration step(s) | Paper lens | Current repo status |
|---|---|---|---|---|
| `A1` | 1, 2, 3 | pre-step foundation | temporal granularity (contract level) | implemented |
| `A2` | 4, 5, 9 | pre-step foundation | none directly | implemented |
| `A3` | 6, 7, 10 | pre-step foundation | none directly | implemented |
| `A4` | 8, 15 | pre-step foundation | temporal granularity (spatial anchoring drift) | implemented |
| `A5` | 15, 16, 17, 18 | pre-step foundation | none directly | implemented |
| `A6` | 11, 12, 13, 14 | pre-step foundation | indirect support for all route-serving attributes | implemented |
| `B1` | 2, 16 | pre-step foundation, Step 4 | temporal granularity | partially implemented |
| `B2` | 9, 10, 21 | pre-step foundation, Step 4 | persistence | partially implemented |
| `B3` | 9, 21 | Step 4 | persistence, periodicity | planned |
| `B4` | 2, 9, 21 | Step 4 | recency, temporal granularity | planned |
| `B5` | 4, 5, 9, 10, 21 | Step 2A, Step 4 | direction, density | planned |
| `B6` | 19, 20, 21 | Step 4 | homophily, scenario calibration | partially implemented |
| `C1` | no direct v11 equivalent | Step 1, Step 4 | recency, temporal granularity | partially implemented |
| `C2` | 13, 14 extended | Step 1, Step 2A | indirect support | partially implemented |
| `C3` | 13, 14 extended | Step 2A | density-sensitive candidate coverage | implemented |
| `C4` | 13, 14 extended | Step 2A, Step 3 | indirect support | partially implemented |
| `C5` | 25, 26 extended | Step 1, Step 3 | none directly | partially implemented |
| `D1` | 7, 25 extended | Step 2, Step 2A | recency override under incidents | partially implemented |
| `D2` | 19, 23, 25 extended | Step 2 | none directly | partially implemented |
| `D3` | 23, 24, 25 | Step 2, Step 2A | direction preservation across rebuild | partially implemented |
| `D4` | 25 | Step 1, Step 2, Step 2A | continuity of temporal semantics | partially implemented |
| `D5` | 25, 28 | Step 2, Step 2A | continuity and performance under reload | partially implemented |
| `E1` | 19 | Step 4 precursor | none directly | planned |
| `E2` | 19, 20, 21 | Step 4 precursor | temporal granularity, recency | planned |
| `E3` | 19-22 plus v11 architecture learning loop | Step 4 | persistence, periodicity, recency | planned |
| `E4` | 21-23 plus v11 architecture calibration loop | Step 4 | preferential attachment, scenario prior calibration | planned |
| `E5` | 23, 27, 28 | Step 4, future productionization | evidence and reproducibility | planned |
| `F1` | 26 | Step 3 | none directly | planned |
| `F2` | 27 | post-Step 3 | calibration feedback | planned |
| `F3` | 28 | post-Step 3 | operational observability | planned |

## 9. Paper Attribute Coverage Table

| Paper attribute | Primary stage(s) | Secondary stage(s) | Required evidence posture |
|---|---|---|---|
| temporal granularity | `B1` | `B4`, `C1` | release-critical calibration and drift bounds |
| direction | `B5` | `B2`, `C3`, `D3` | hard blocker; asymmetry must survive compile, serve, and reload |
| density | `B5` | `C3`, `C4` | hard blocker; candidate and prior coverage must not collapse |
| persistence | `B2`, `B3` | `E3` | release-critical; durable congestion patterns must remain representable |
| periodicity | `B3` | `E3`, `C3` | release-critical; recurring patterns must survive to ETA/P90 behavior |
| recency | `B4` | `C1`, `D1`, `E4` | hard blocker; near-horizon outputs must respond to fresh evidence |
| homophily | `B6` | `E3` | optional modeling signal, not a default release blocker |
| preferential attachment | `E4` | `B6`, `C1` | positive prior-shaping signal with evidence-responsive calibration |

## 10. Cross-Phase Contract Tests

These contracts exist because local stage closure is not enough. Temporal properties can pass at the artifact layer and fail later in scenario serving or reload.

### 10.1 `B→C` Temporal Fidelity Contract

Required suite:

- `TemporalFidelityContractTest`

Purpose:

- prove that temporal properties validated in Phase B survive into scenario materialization, aggregate planning, and served route outputs

Required acceptance:

- recency ordering survives from profile input to scenario probabilities
- directional asymmetry survives from profile input to selected route output
- periodic pattern recovery survives from learned artifact to ETA/P90 behavior
- density and candidate coverage do not eliminate an aggregate-best compromise route

Local stage inclusion required for:

- `B3`, `B4`, `B5`, `C1`, `C2`, `C3`, `C4`

### 10.2 `C→D` Snapshot Temporal Continuity Contract

Required suites:

- `ReloadTemporalContinuityContractTest` (planned)
- `AsymmetricCorridorReloadTest` (planned)
- `QuarantineRecencyOverrideTest` (planned)

Purpose:

- prove that rebuilds, publications, and quarantines preserve temporal semantics instead of flattening or silently dropping them

Required acceptance:

- inject a known asymmetric corridor with different `A→B` and `B→A` temporal profiles
- rebuild and reload the topology
- verify the asymmetry survives when the corridor still exists
- verify removed subjects are explicitly dropped or invalidated rather than collapsed into symmetric fallback behavior
- verify fresh quarantine overrides stale throughput history in near-horizon scenario generation

Local stage inclusion required for:

- `D1`, `D3`, `D4`, `D5`

### 10.3 `E→C` Scenario Prior Consistency Contract

Required suites:

- `ScenarioPriorConsistencyContractTest` (planned)
- `DegreeAwareScenarioPriorTest` (planned)

Purpose:

- prove that serving-time scenario masses remain responsive to evidence rather than becoming uncalibrated degree bias

Required acceptance:

Evidence-present case:

- choose a held-out high-degree arterial and a low-degree alternative under comparable recent incident evidence
- `incident_persists` prior mass for the arterial bucket must be at least `historical bucket frequency - 5 percentage points`
- and at least `10 percentage points` higher than the competing low-degree alternative

No-recent-evidence baseline case:

- choose the same arterial and low-degree alternative with no recent incident evidence
- define `deviation = predicted incident_persists prior - historical bucket frequency for that corridor bucket`
- arterial deviation must remain within `±5 percentage points`
- arterial deviation may not exceed low-degree deviation by more than `3 percentage points`

Local stage inclusion required for:

- `B6`, `C1`, `C4`, `E4`

## 11. Detailed Stage Breakdown

### Phase A: Deterministic Runtime Bedrock

#### `A1` Identity, Time, and Schema Contracts

Core purpose:
- freeze the basic identity, tick, and schema contracts so later temporal and future-aware stages reason over one stable foundation

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- maintain deterministic external/internal id translation
- normalize request timestamps to runtime ticks and day/bucket views
- validate model schema, metadata, and file-identifier contracts before runtime use

Non-functional requirements:
- O(1)-style id and time conversions in the hot path
- zero-copy-safe schema access
- no ambiguous timestamp or metadata interpretation

Named test suites:
- `FastUtilIDMapperTest`
- `TimeUtilsTest`
- `ModelContractValidatorTest`
- `TaroModelTest`

Equivalence classes:
- known vs unknown ids
- negative, boundary, and wrapped timestamps
- minimal valid model vs invalid identifier vs evolved optional metadata

Dependencies:
- none

Shared contract dependencies:
- none

Closure criteria:
- id, time, and schema contracts are deterministic and validated before any higher-order runtime or builder stage executes
- metadata required by temporal and reload-aware stages is available or rejected explicitly

Repo anchors:
- `src/main/java/org/Aayush/core/id/FastUtilIDMapper.java`
- `src/main/java/org/Aayush/core/time/TimeUtils.java`
- `src/main/java/org/Aayush/serialization/flatbuffers/ModelContractValidator.java`

#### `A2` Graph, Turn, and Profile Runtime Stores

Core purpose:
- provide immutable runtime storage for topology, turn penalties, and temporal profiles

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- load directed graph topology with stable edge and node access
- resolve turn penalties and forbidden transitions
- store temporal profiles and interpolation metadata for runtime lookup

Non-functional requirements:
- cache-friendly primitive-array access
- concurrent-read safety
- predictable memory footprint on large graphs

Named test suites:
- `EdgeGraphTest`
- `TurnCostMapTest`
- `ProfileStoreTest`

Equivalence classes:
- minimal graph vs sparse/dense graph
- known turn vs missing turn vs forbidden turn
- active day vs inactive day vs missing profile fallback

Dependencies:
- `A1`

Shared contract dependencies:
- none

Closure criteria:
- graph, turn, and profile stores are immutable, validated, and reusable across route, matrix, and future-aware workloads
- failures in one store do not produce silent fallback semantics in another

Repo anchors:
- `src/main/java/org/Aayush/routing/graph/EdgeGraph.java`
- `src/main/java/org/Aayush/routing/graph/TurnCostMap.java`
- `src/main/java/org/Aayush/routing/profile/ProfileStore.java`

#### `A3` Cost Engine and Search Infrastructure

Core purpose:
- turn immutable graph and profile data into deterministic edge expansion behavior

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- compute final edge cost from base weight, temporal profile, live overlay, and transition penalty
- provide reusable search queues, state pools, and visited structures
- enforce budget and termination policies for route and matrix search

Non-functional requirements:
- no allocation-heavy hot-path behavior
- bounded search memory
- deterministic numeric behavior under fixed input

Named test suites:
- `CostEngineTest`
- `SearchInfrastructureTest`
- `Stage13PrimitiveGuardrailTest`
- `Stage14PrimitiveGuardrailTest`

Equivalence classes:
- base-only path vs temporal+live path vs blocked-edge path
- empty queue vs tie-break ordering vs large-state reuse
- budget hit vs successful termination

Dependencies:
- `A2`

Shared contract dependencies:
- none

Closure criteria:
- cost composition remains deterministic and explainable
- search infrastructure remains reusable, bounded, and parity-safe for both route and matrix execution

Repo anchors:
- `src/main/java/org/Aayush/routing/cost/CostEngine.java`
- `src/main/java/org/Aayush/routing/search/SearchQueue.java`
- `src/main/java/org/Aayush/routing/core/SearchBudget.java`

#### `A4` Spatial Indexing and Address Resolution

Core purpose:
- convert external ids and coordinates into internal graph anchors without introducing endpoint ambiguity

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- resolve nearest graph anchors from coordinates
- preserve deterministic tie-break and snap-threshold behavior
- support id- and coordinate-based request normalization paths

Non-functional requirements:
- sublinear nearest-neighbor lookup for the spatial runtime
- deterministic snapping across repeated queries
- concurrency-safe immutable read path

Named test suites:
- `SpatialRuntimeTest`
- `Stage15AddressingTraitTest`
- `CoordinateDistanceStrategyTest`

Equivalence classes:
- valid coordinate vs out-of-bounds coordinate
- id-only vs coordinate-only vs mixed-mode requests
- nearest-node tie cases and disabled spatial mode

Dependencies:
- `A1`
- `A2`

Shared contract dependencies:
- none

Closure criteria:
- address normalization is deterministic and precise enough to avoid endpoint drift between route, matrix, and future-aware calls
- spatial runtime preserves sublinear behavior and deterministic parity vs brute force

Repo anchors:
- `src/main/java/org/Aayush/routing/spatial/SpatialRuntime.java`
- `src/main/java/org/Aayush/routing/traits/addressing/AddressingTraitEngine.java`
- `src/main/java/org/Aayush/routing/traits/addressing/AddressingPolicy.java`

#### `A5` Trait/Execution Binding and Startup Lock

Core purpose:
- bind addressing, temporal, transition, bundle, and execution-profile choices once at startup so request-time behavior remains deterministic

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- resolve and validate startup trait bundles and execution profiles
- reject incompatible runtime configuration combinations
- expose startup-locked routing behavior without request-time switching

Non-functional requirements:
- fast startup validation
- stable trait-hash and profile selection metadata
- zero request-time ambiguity about active runtime mode

Named test suites:
- `Stage15AddressingTraitTest`
- `Stage16TemporalTraitTest`
- `Stage17TransitionTraitTest`
- `Stage18TraitRegistryTest`
- `ExecutionProfileRegistryTest`
- `ExecutionProfileRouteCoreTest`

Equivalence classes:
- valid bundle vs missing dependency vs incompatible trait bundle
- valid execution profile vs selector mismatch
- built-in vs custom strategy bindings

Dependencies:
- `A1`
- `A4`

Shared contract dependencies:
- none

Closure criteria:
- all active runtime modes are fully bound at startup with stable diagnostics
- request-time selector drift cannot bypass startup contracts

Repo anchors:
- `src/main/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeBinder.java`
- `src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java`
- `src/main/java/org/Aayush/routing/core/DefaultRequestNormalizer.java`

#### `A6` Deterministic Route and Matrix Runtime

Core purpose:
- execute deterministic route and matrix queries over one fixed runtime snapshot

Current repo status:
- implemented

Gate severity:
- hard blocker

Functional requirements:
- provide optimal point-to-point routing under fixed cost surfaces
- provide deterministic one-to-many matrix execution with compatible fallback behavior
- preserve A*/Dijkstra parity under admissible heuristic use

Non-functional requirements:
- bounded runtime latency and memory
- deterministic outputs under repeated execution
- maintainability guardrails for core orchestration

Named test suites:
- `RouteCoreTest`
- `BidirectionalTdAStarPlannerTest`
- `NativeOneToManyMatrixPlannerTest`
- `RouteCoreStressPerfTest`
- `MaintainabilityGuardrailTest`

Equivalence classes:
- trivial, long, and unreachable routes
- matrix route-equivalence cases and oversized target fallback
- A* vs Dijkstra parity and repeated deterministic execution

Dependencies:
- `A3`
- `A4`
- `A5`

Shared contract dependencies:
- none

Closure criteria:
- route and matrix runtime remain deterministic for a fixed snapshot
- all later future-aware and topology stages build on these primitives rather than replacing them

Repo anchors:
- `src/main/java/org/Aayush/routing/core/RouteCore.java`
- `src/main/java/org/Aayush/routing/core/BidirectionalTdAStarPlanner.java`
- `src/main/java/org/Aayush/routing/core/NativeOneToManyMatrixPlanner.java`

### Phase B: Temporal Competency Gates

#### `B1` Temporal Semantics and Granularity Contract

Core purpose:
- define what temporal meaning TARO is actually preserving across ticks, timezones, buckets, and discretization choices

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- define the allowed continuous-time vs bucketed-time postures
- preserve explicit timezone and tick-normalization contracts across builder and runtime
- document acceptable drift when coarsening temporal granularity for learning or scenario generation

Non-functional requirements:
- deterministic time interpretation
- no silent granularity loss
- bounded and measurable discretization drift

Named test suites:
- `TimeUtilsTest`
- `Stage16TemporalTraitTest`
- `TemporalContextResolverTest`
- `TemporalGranularityCompetencyTest` (planned)

Equivalence classes:
- continuous timestamps vs discretized buckets
- midnight, week-boundary, and DST transitions
- same request evaluated at two granularities with bounded drift

Dependencies:
- `A1`
- `A5`

Shared contract dependencies:
- none

Closure criteria:
- TARO has one documented temporal-semantics contract that applies from dataset ingestion through served route output
- any allowed discretization posture includes explicit drift tolerances and rejection conditions

Repo anchors:
- `src/main/java/org/Aayush/core/time/TimeUtils.java`
- `src/main/java/org/Aayush/routing/traits/temporal/TemporalContextResolver.java`
- `docs/taro_v12_architecture_plan.md`

#### `B2` Direction-Preserving Profile Integrity, FIFO, and Persistent Baseline Assurance

Core purpose:
- ensure compiled temporal profiles remain physically valid, direction-preserving, and able to represent durable baseline traffic behavior

Current repo status:
- partially implemented

Gate severity:
- hard blocker

Functional requirements:
- preserve per-directed-edge profile meaning instead of collapsing `A→B` and `B→A`
- validate FIFO on the final directed compiled representation
- retain persistent congestion and persistent free-flow baselines through profile repair or compression

Non-functional requirements:
- zero accepted FIFO violations
- deterministic compile output under fixed seed and input
- auditable repair/rejection behavior for malformed or noisy profile candidates

Named test suites:
- `ProfileStoreTest`
- `CostEngineTest`
- `PersistentBaselineProfileTest`
- `ProfileFifoRepairGateTest`
- `ProfileContractPerfTest`

Equivalence classes:
- directionally symmetric vs directionally asymmetric corridors
- always-congested, always-free, and noisy persistent edges
- FIFO-safe vs FIFO-violating candidate profiles

Dependencies:
- `A2`
- `A3`
- `B1`

Shared contract dependencies:
- none

Closure criteria:
- FIFO validation is proven per directed edge and on the final direction-preserving compiled representation
- any compile or calibration path that merges `A→B` and `B→A` before FIFO validation is explicitly rejected
- persistent baseline behavior survives profile smoothing or repair

Repo anchors:
- `src/main/java/org/Aayush/routing/profile/ProfileStore.java`
- `src/main/java/org/Aayush/routing/cost/CostEngine.java`
- `docs/taro_v11_architecture_plan.md`

#### `B3` Persistence and Periodicity Competency

Core purpose:
- prove that TARO can retain always-on and recurring temporal patterns that matter for expected and robust future-aware routing

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- reproduce persistent traffic behavior for edges that remain predictably congested or uncongested
- reproduce periodic patterns such as rush hour, weekday/weekend shifts, and recurring incidents
- expose when periodic signal is too weak rather than flattening it into misleading averages

Non-functional requirements:
- deterministic periodic alignment under fixed input
- bounded degradation under sparse evidence
- explicit calibration posture for recurrent patterns

Named test suites:
- `PersistenceCompetencyTest` (planned)
- `PeriodicityCompetencyTest` (planned)
- `RecurringIncidentPatternTest` (planned)
- `TemporalFidelityContractTest`

Equivalence classes:
- fully persistent edge
- strict periodic edge
- mixed persistent+periodic edge
- weak-signal recurring edge

Dependencies:
- `B2`

Shared contract dependencies:
- `TemporalFidelityContractTest`

Closure criteria:
- persistent and periodic patterns are recovered at the artifact layer and survive through scenario serving
- robust/P90 milestones cannot close unless periodic and persistent behavior is validated through `TemporalFidelityContractTest`

Repo anchors:
- `docs/taro_v12_architecture_plan.md`
- `ResearchData/2510.09416v1.pdf`
- `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java`

#### `B4` Recency Weighting and Granularity Calibration

Core purpose:
- guarantee that fresh evidence materially influences near-horizon forecasts instead of being flattened into stale historical priors

Current repo status:
- planned

Gate severity:
- hard blocker

Functional requirements:
- encode explicit recency weighting in learned profiles and scenario priors
- distinguish genuine recency influence from mere cache or TTL freshness
- calibrate near-horizon vs far-horizon use of recent observations

Non-functional requirements:
- monotonic recency influence under fixed evidence
- bounded staleness decay
- auditable calibration coefficients and threshold policy

Named test suites:
- `RecencyCalibrationTest` (planned)
- `TemporalFreshnessOrderingTest` (planned)
- `NearHorizonPriorShiftTest` (planned)
- `TemporalFidelityContractTest`

Equivalence classes:
- fresh strong signal vs stale conflicting signal
- same long-run mean with different recency windows
- near-horizon vs far-horizon forecast horizon
- quarantine event vs stale throughput history

Dependencies:
- `B1`
- `B2`

Shared contract dependencies:
- `TemporalFidelityContractTest`

Closure criteria:
- recency weighting is explicitly calibrated and auditable
- no stage may close by relying on TTL-only freshness behavior
- near-horizon outputs respond measurably to fresh evidence and preserve that response through serving

Repo anchors:
- `src/main/java/org/Aayush/routing/core/FutureScenarioSupport.java`
- `docs/taro_v12_architecture_plan.md`
- `ResearchData/2510.09416v1.pdf`

#### `B5` Directionality Asymmetry and Density Calibration

Core purpose:
- prevent the learning and serving stack from silently collapsing directed temporal behavior or sparse/dense candidate coverage

Current repo status:
- planned

Gate severity:
- hard blocker

Functional requirements:
- preserve asymmetric temporal and route behavior for `A→B` vs `B→A`
- preserve one-way and turn-sensitive semantics across compile, serve, and reload
- calibrate candidate and scenario coverage so aggregate-best routes are not missed by sparse candidate collapse

Non-functional requirements:
- deterministic directional asymmetry under fixed evidence
- explicit density calibration reports
- no silent symmetry introduced by profile compression, scenario generation, or planner candidate selection

Named test suites:
- `DirectionalityAsymmetryTest` (planned)
- `DensityCalibrationTest` (planned)
- `DirectedProfileDivergenceTest` (planned)
- `TemporalFidelityContractTest`

Equivalence classes:
- asymmetric opposing corridor profiles
- one-way corridor
- turn-sensitive directional asymmetry
- low-density vs high-density subgraphs

Dependencies:
- `B2`
- `A6`

Shared contract dependencies:
- `TemporalFidelityContractTest`

Closure criteria:
- directional asymmetry and density calibration survive learning, candidate generation, and served planning outputs
- any path that produces correct deterministic behavior on a flattened symmetric surface is explicitly considered a failure

Repo anchors:
- `src/main/java/org/Aayush/routing/core/FutureRouteObjectivePlanner.java`
- `src/main/java/org/Aayush/routing/topology/TopologyModelCompiler.java`
- `ResearchData/2510.09416v1.pdf`

#### `B6` Scenario Confidence and Structural Prior Alignment

Core purpose:
- turn temporal evidence into calibrated scenario probabilities and confidence semantics

Current repo status:
- partially implemented

Gate severity:
- calibration gate

Functional requirements:
- assign normalized scenario probabilities with auditable labels and explanations
- treat homophily as optional modeling context rather than a blocker
- align degree-correlated congestion priors with traffic reality without turning degree into an unconditional incident bias

Non-functional requirements:
- stable probability mass normalization
- bounded confidence overstatement
- explicit calibration artifacts for scenario priors

Named test suites:
- `DefaultScenarioBundleResolverTest`
- `ScenarioConfidenceCalibrationTest` (planned)
- `DegreeAwareScenarioPriorTest` (planned)
- `BundleProbabilityCalibrationTest` (planned)
- `ScenarioPriorConsistencyContractTest` (planned)

Equivalence classes:
- arterial vs low-degree alternative
- incident-present vs no-recent-evidence baseline
- homophilic vs heterophilic neighborhood structure
- probability mass reassignment after fresh evidence

Dependencies:
- `B3`
- `B4`
- `B5`

Shared contract dependencies:
- `ScenarioPriorConsistencyContractTest`
- `DegreeAwareScenarioPriorTest`

Closure criteria:
- scenario confidence reflects both temporal evidence and degree-aware prior shaping without degenerating into unconditional arterial bias
- homophily may inform calibration but cannot block release by default

Repo anchors:
- `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java`
- `docs/taro_v12_architecture_plan.md`
- `ResearchData/2510.09416v1.pdf`

### Phase C: Future-Aware Serving

#### `C1` Scenario Bundle Materialization

Core purpose:
- materialize bounded deterministic future scenarios for one request horizon

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- materialize immutable `ScenarioBundle` instances with probabilities, labels, lineage, and live-state context
- combine active live overlay, quarantine posture, and future priors without mutating the base snapshot
- preserve recency, granularity, and degree-aware scenario calibration inputs

Non-functional requirements:
- bounded scenario count
- deterministic bundle identity under fixed input
- auditable lineage from model and live snapshot to bundle

Named test suites:
- `DefaultScenarioBundleResolverTest`
- `FutureScenarioSupportTest`
- `TemporalFidelityContractTest`
- `ScenarioPriorConsistencyContractTest` (planned)

Equivalence classes:
- baseline-only bundle
- incident-persistent vs clearing-fast bundle
- recent evidence vs no-recent-evidence bundle
- route vs matrix request bundle parity

Dependencies:
- `B1`
- `B4`
- `B6`
- `D1`

Shared contract dependencies:
- `TemporalFidelityContractTest`
- `ScenarioPriorConsistencyContractTest`

Closure criteria:
- scenario probabilities preserve recency ordering and degree-aware calibration
- bundle materialization is deterministic and sufficiently expressive for route and matrix future-aware serving

Repo anchors:
- `src/main/java/org/Aayush/routing/future/ScenarioBundle.java`
- `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java`
- `src/main/java/org/Aayush/routing/core/FutureScenarioSupport.java`

#### `C2` Scenario-Specific Route and Matrix Evaluation

Core purpose:
- run deterministic route and matrix execution once per materialized scenario

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- evaluate point-to-point routes against scenario-specific cost surfaces
- evaluate matrices against scenario-specific cost surfaces
- preserve per-scenario metrics and retained scenario outputs for later aggregation and inspection

Non-functional requirements:
- deterministic execution for fixed scenario bundles
- bounded per-scenario execution overhead
- no planner replacement required beyond scenario-specific cost injection

Named test suites:
- `FutureRouteServiceTest`
- `FutureMatrixServiceTest`
- `FutureScenarioSupportTest`
- `TemporalFidelityContractTest`

Equivalence classes:
- route vs matrix scenario execution
- blocked-edge scenario vs baseline scenario
- reachable vs unreachable scenario results
- repeated scenario execution determinism

Dependencies:
- `A6`
- `C1`

Shared contract dependencies:
- `TemporalFidelityContractTest`

Closure criteria:
- scenario-specific cost engines preserve temporal, directional, and live-state semantics
- route and matrix scenario outputs are consistent enough to support aggregate planning and retained inspection

Repo anchors:
- `src/main/java/org/Aayush/routing/core/FutureRouteEvaluator.java`
- `src/main/java/org/Aayush/routing/core/FutureMatrixEvaluator.java`
- `src/main/java/org/Aayush/routing/future/FutureMatrixService.java`

#### `C3` Aggregate Objective Planning

Core purpose:
- select expected and robust winners by optimizing aggregate objectives directly across scenarios

Current repo status:
- implemented

Gate severity:
- hard blocker for future-aware route correctness

Functional requirements:
- compute expected ETA winners through direct multi-scenario objective search
- compute robust/P90 winners through direct multi-scenario objective search
- avoid restricting aggregate winners to the subset of per-scenario-optimal routes

Non-functional requirements:
- deterministic aggregate selection
- bounded candidate and frontier behavior
- stable regret and tie-break semantics

Named test suites:
- `FutureRouteObjectivePlannerTest`
- `FutureRouteObjectivePlannerWeaknessFinderTest`
- `FutureRouteServiceTest`
- `AggregateCompromiseRouteContractTest`
- `C3AggregateObjectivePerfSmokeTest`
- `TemporalFidelityContractTest`

Equivalence classes:
- aggregate-best compromise route
- robust-best but not expected-best route
- no-optimality-probability selected winner
- dense candidate family vs sparse candidate family

Dependencies:
- `C2`
- `B5`

Shared contract dependencies:
- `TemporalFidelityContractTest`

Closure criteria:
- inject a route that is second-best in every scenario but best in aggregate and verify the planner selects it
- expected and robust winners are sourced from direct multi-scenario planning, not only from per-scenario-optimal seeds

Repo anchors:
- `src/main/java/org/Aayush/routing/core/FutureRouteObjectivePlanner.java`
- `src/main/java/org/Aayush/routing/core/FutureRouteEvaluator.java`
- `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java`

#### `C4` Alternatives, Explanation, and Confidence

Core purpose:
- expose materially distinct route families with confidence-aware explanations rather than a single opaque winner

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- produce top-K materially distinct alternatives seeded by expected and robust winners
- attach optimality probability, regret, dominant-scenario explanation, and ETA band semantics
- preserve scenario-confidence calibration in user-facing explanations

Non-functional requirements:
- bounded alternative count
- deterministic deduplication and ranking
- no silent disappearance of aggregate winners from alternatives

Named test suites:
- `FutureRouteServiceTest`
- `AlternativeConfidenceContractTest` (planned)
- `TemporalFidelityContractTest`
- `ScenarioPriorConsistencyContractTest` (planned)

Equivalence classes:
- expected and robust winners both present
- aggregate-only winner with zero optimality probability
- dense alternatives vs near-identical alternatives
- explanation under high vs low scenario confidence

Dependencies:
- `C2`
- `C3`
- `B6`

Shared contract dependencies:
- `TemporalFidelityContractTest`
- `ScenarioPriorConsistencyContractTest`

Closure criteria:
- alternatives preserve aggregate winners when distinct
- explanation and confidence fields remain consistent with scenario probabilities and aggregate objectives

Repo anchors:
- `src/main/java/org/Aayush/routing/future/FutureRouteResultSet.java`
- `src/main/java/org/Aayush/routing/future/ScenarioRouteSelection.java`
- `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java`

#### `C5` Retained Results and Retrieval Contracts

Core purpose:
- retain future-aware results long enough for frontend or API follow-up inspection

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- retain route and matrix future-aware results by `resultSetId`
- preserve topology-binding and reload compatibility behavior
- expose stable summary/detail retrieval contracts for later API work

Non-functional requirements:
- bounded memory usage
- explicit eviction policy and compression posture
- deterministic invalidation under incompatible reloads

Named test suites:
- `InMemoryEphemeralResultStoreTest`
- `TopologyReloadCoordinatorTest`
- `FutureRouteServiceTest`
- `FutureMatrixServiceTest`
- `RetainedResultApiTest` (planned)

Equivalence classes:
- TTL expiry vs eviction
- retained route vs retained matrix
- reload-compatible vs incompatible retained result
- oversize non-admission

Dependencies:
- `C1`
- `C2`
- `C4`

Shared contract dependencies:
- none

Closure criteria:
- retained results remain bounded, topology-aware, and safe for API retrieval work
- no retained result survives incompatible reload by accident

Repo anchors:
- `src/main/java/org/Aayush/routing/future/InMemoryEphemeralRouteResultStore.java`
- `src/main/java/org/Aayush/routing/future/InMemoryEphemeralMatrixResultStore.java`
- `src/main/java/org/Aayush/routing/topology/ReloadCompatibilityPolicy.java`

### Phase D: Topology Evolution and Snapshot Continuity

#### `D1` Live Overlay and Failure Quarantine

Core purpose:
- represent transient failures without mutating the active base graph and ensure that fresh incidents dominate stale temporal priors

Current repo status:
- partially implemented

Gate severity:
- hard blocker for incident-aware serving

Functional requirements:
- record transient node and edge failures with TTLs and reasons
- expand node failures to blocking incident edges through a topology-bound index
- ensure fresh quarantine posture overrides stale throughput history in near-horizon scenario generation

Non-functional requirements:
- bounded quarantine materialization cost
- deterministic snapshot behavior under fixed clock
- no full-graph scan on the common request path

Named test suites:
- `FailureQuarantineTest`
- `TopologyPublicationServiceTest`
- `QuarantineRecencyOverrideTest` (planned)
- `ReloadTemporalContinuityContractTest` (planned)

Equivalence classes:
- edge failure vs node failure
- active vs expired quarantine
- fresh quarantine vs stale throughput history
- same failure before and after reload

Dependencies:
- `A2`
- `A3`
- `B4`

Shared contract dependencies:
- `QuarantineRecencyOverrideTest`
- `ReloadTemporalContinuityContractTest`

Closure criteria:
- fresh quarantine entries override stale historical throughput in near-horizon scenario generation and route output
- failure snapshots remain deterministic, bounded, and reload-aware

Repo anchors:
- `src/main/java/org/Aayush/routing/topology/FailureQuarantine.java`
- `src/main/java/org/Aayush/routing/topology/TopologyIncidentIndex.java`
- `src/main/java/org/Aayush/routing/overlay/LiveOverlay.java`

#### `D2` Structural Source Model and Change Sets

Core purpose:
- represent durable topology truth at the source-input layer before compilation and publication

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- express typed structural changes for nodes, edges, coordinates, profiles, and turns
- apply changes deterministically to canonical source topology
- preserve explicit rollout intent for validate-only vs publish-and-reload flows

Non-functional requirements:
- deterministic change application
- actionable validation diagnostics
- no hidden mutation of the live runtime snapshot

Named test suites:
- `TopologyModelSourceTest`
- `StructuralChangeApplierTest`
- `TopologyReloadSmokeTest`

Equivalence classes:
- add-edge, add-node, remove-edge, remove-node
- coordinate and profile updates
- turn upsert/removal
- validate-only vs atomic-reload intent

Dependencies:
- `A1`
- `A2`

Shared contract dependencies:
- none

Closure criteria:
- structural changes are captured as typed source-level edits before any runtime mutation happens
- change application remains deterministic and auditable

Repo anchors:
- `src/main/java/org/Aayush/routing/topology/StructuralChangeSet.java`
- `src/main/java/org/Aayush/routing/topology/StructuralChangeApplier.java`
- `src/main/java/org/Aayush/routing/topology/TopologyModelSource.java`

#### `D3` Compilation, Spatial Artifact Rebuild, and Lineage

Core purpose:
- compile changed topology into a new model artifact while preserving spatial, directional, and lineage contracts

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- rebuild FlatBuffer topology, profiles, turns, and spatial artifacts
- preserve deterministic balanced spatial index generation
- retain lineage and stable index layout needed for reload continuity

Non-functional requirements:
- deterministic compile output
- bounded compile latency and artifact growth
- no directional or temporal flattening during rebuild

Named test suites:
- `TopologyModelCompilerTest`
- `TopologyPublicationServiceTest`
- `AsymmetricCorridorReloadTest` (planned)
- `ReloadTemporalContinuityContractTest` (planned)

Equivalence classes:
- minimal compile vs full-feature compile
- balanced vs skewed coordinate distribution
- asymmetric corridor preserved across rebuild
- removed subject dropped cleanly

Dependencies:
- `D2`
- `B2`
- `B5`

Shared contract dependencies:
- `AsymmetricCorridorReloadTest`
- `ReloadTemporalContinuityContractTest`

Closure criteria:
- rebuild preserves spatial validity, lineage, and directional asymmetry where the subject still exists
- no compile path silently normalizes asymmetric temporal behavior into symmetric fallback output

Repo anchors:
- `src/main/java/org/Aayush/routing/topology/TopologyModelCompiler.java`
- `src/main/java/org/Aayush/routing/topology/TopologyIndexLayout.java`
- `src/main/resources/flatbuffers/taro_model.fbs`

#### `D4` Atomic Publication and Reload Compatibility

Core purpose:
- swap validated runtime snapshots atomically while preserving compatible live state and rejecting incompatible retained state

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- publish candidate snapshots after validation gates pass
- preserve compatible live overlay and quarantine posture across reload
- invalidate retained results and dropped subjects explicitly when compatibility rules fail

Non-functional requirements:
- no partial visibility during reload
- fail-safe rollback on invalid publish
- bounded publication latency and memory impact

Named test suites:
- `TopologyPublicationServiceTest`
- `TopologyReloadCoordinatorTest`
- `AsymmetricCorridorReloadTest` (planned)
- `ReloadTemporalContinuityContractTest` (planned)

Equivalence classes:
- compatible reload vs incompatible reload
- retained result survival vs invalidation
- surviving asymmetric corridor vs removed asymmetric corridor
- preserved quarantine vs dropped subject quarantine

Dependencies:
- `D3`
- `C5`

Shared contract dependencies:
- `AsymmetricCorridorReloadTest`
- `ReloadTemporalContinuityContractTest`

Closure criteria:
- topology reload is atomic and explicitly decides what survives and what is invalidated
- asymmetry, quarantine, and retained-result posture survive only when the underlying subject still exists and remains compatible

Repo anchors:
- `src/main/java/org/Aayush/routing/topology/TopologyPublicationService.java`
- `src/main/java/org/Aayush/routing/topology/TopologyReloadCoordinator.java`
- `src/main/java/org/Aayush/routing/topology/ReloadCompatibilityPolicy.java`

#### `D5` Continuity, Parity, and Performance After Reload

Core purpose:
- prove that reload-safe topology evolution does not break runtime parity, temporal fidelity, or performance posture

Current repo status:
- partially implemented

Gate severity:
- release-critical

Functional requirements:
- re-verify route, matrix, spatial, and retained-result behavior after reload
- preserve parity and continuity guarantees across topology versions
- detect reload-driven performance cliffs before publication

Non-functional requirements:
- bounded reload-time performance regression
- deterministic post-reload behavior under fixed snapshot
- actionable diagnostics when continuity fails

Named test suites:
- `TopologyReloadPerfTest`
- `TopologyReloadSmokeTest`
- `SpatialRuntimeTest`
- `ReloadTemporalContinuityContractTest` (planned)

Equivalence classes:
- same-query pre/post reload parity
- added edge vs removed edge behavior
- retained-result continuity vs invalidation
- spatial nearest-node parity after rebuild

Dependencies:
- `D4`

Shared contract dependencies:
- `ReloadTemporalContinuityContractTest`

Closure criteria:
- reload preserves parity, performance, and temporal continuity within documented bounds
- any reload that keeps a subject but flattens its temporal or directional behavior is treated as a failure

Repo anchors:
- `src/test/java/org/Aayush/routing/topology/TopologyReloadPerfTest.java`
- `src/test/java/org/Aayush/routing/topology/TopologyReloadSmokeTest.java`
- `src/main/java/org/Aayush/routing/topology/TopologyRuntimeFactory.java`

### Phase E: Offline Learning and Forecast Intelligence

#### `E1` Ingestion, Validation, and Dataset Manifests

Core purpose:
- create a canonical, reproducible source-of-truth dataset layer for temporal learning and scenario calibration

Current repo status:
- planned

Gate severity:
- release-critical for any learning-based path

Functional requirements:
- ingest raw telemetry, incident, and topology source feeds
- validate schema, units, id consistency, and required temporal fields
- publish reproducible dataset manifests with lineage and filtering metadata

Non-functional requirements:
- scalable batch throughput
- deterministic parse and normalization behavior
- row-level validation diagnostics

Named test suites:
- `DatasetManifestContractTest` (planned)
- `SourceValidationIngestionTest` (planned)
- `LargeBatchIngestionSmokeTest` (planned)

Equivalence classes:
- valid vs malformed source row
- unit mismatch vs missing field
- duplicate ids and late-arriving data
- large-batch ingestion

Dependencies:
- `A1`
- `B1`

Shared contract dependencies:
- none

Closure criteria:
- all learning and calibration stages consume canonical manifests rather than ad hoc raw feeds
- invalid temporal or unit posture is rejected before feature construction begins

Repo anchors:
- `docs/taro_v11_architecture_plan.md`
- `ResearchData/taro_v11_impl_guide.md`
- `dataset_manifest.json` (planned canonical artifact)

#### `E2` Sequence Construction and Temporal Feature Surfaces

Core purpose:
- convert validated source data into deterministic temporal sequences and feature surfaces used by learning and calibration stages

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- build per-edge, per-corridor, and per-time-window temporal sequences
- publish feature surfaces for recency, persistence, periodicity, and corridor-level activity
- compute the canonical historical corridor-bucket frequency baseline used by later prior-calibration tests

Non-functional requirements:
- deterministic sequence construction
- bounded feature-surface growth
- auditable temporal binning and aggregation policy

Named test suites:
- `SequenceBuilderContractTest` (planned)
- `TemporalFeatureSurfaceTest` (planned)
- `CorridorBucketFrequencyArtifactTest` (planned)

Equivalence classes:
- sparse vs dense corridor history
- strict periodic vs weak periodic signal
- recent spike vs stale baseline
- high-degree vs low-degree corridor frequency baseline

Dependencies:
- `E1`
- `B1`

Shared contract dependencies:
- none

Closure criteria:
- feature surfaces required by recency, periodicity, and scenario-prior calibration are published deterministically
- historical corridor-bucket frequency baseline exists as a canonical artifact before it is consumed in later calibration stages

Repo anchors:
- `ResearchData/taro_v11_impl_guide.md`
- `sequence_dataset.parquet` (planned canonical artifact)
- `corridor_bucket_frequency.parquet` (planned canonical artifact)

#### `E3` Forecast and Representation Learning

Core purpose:
- learn temporal representations and forecast surfaces without moving stochastic behavior into runtime

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- train temporal representations over edge/corridor sequences
- learn forecast surfaces that can improve future-aware scenario priors and refined profile candidates
- probe learned representations against persistence, periodicity, recency, direction, and density behavior

Non-functional requirements:
- reproducible training under pinned seed and data window
- bounded model and training cost
- no hidden runtime dependency on stochastic components

Named test suites:
- `ForecastRepresentationLearningSmokeTest` (planned)
- `TemporalAttributeProbeTest` (planned)
- `AblationDeterminismTest` (planned)

Equivalence classes:
- profile-only learning vs richer forecast features
- low-data vs high-data corridor histories
- asymmetric corridor vs symmetric corridor
- periodic vs non-periodic histories

Dependencies:
- `E2`
- `B3`
- `B4`
- `B5`

Shared contract dependencies:
- none

Closure criteria:
- learned forecast surfaces improve the intended temporal attributes without violating the compile-time/runtime separation rule
- representation probing shows where the learning layer is and is not competent before any export path is opened

Repo anchors:
- `docs/taro_v11_architecture_plan.md`
- `ResearchData/taro_v11_learning_pathway.md`
- `ResearchData/2510.09416v1.pdf`

#### `E4` Constraint-Aware Selection, Calibration, and Scenario Priors

Core purpose:
- convert learned or measured temporal signals into calibrated exported profiles and scenario priors that are evidence-responsive

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- select and calibrate refined profiles and scenario priors under safety constraints
- treat preferential attachment as a valid prior-shaping signal for arterials and other high-degree corridors
- ensure scenario prior mass responds to fresh evidence and returns toward historical baseline when evidence disappears

Non-functional requirements:
- auditable calibration output
- bounded prior overconfidence
- explicit publication of the baseline frequency table used by degree-aware prior tests

Named test suites:
- `ForecastCalibrationTest` (planned)
- `DegreeAwareScenarioPriorTest` (planned)
- `ScenarioPriorConsistencyContractTest` (planned)
- `ConfidenceGateSelectionTest` (planned)

Equivalence classes:
- evidence-present arterial incident
- no-recent-evidence arterial baseline
- high-degree arterial vs low-degree alternative
- confidence-qualified vs confidence-rejected candidate

Dependencies:
- `E3`
- `B6`

Shared contract dependencies:
- `ScenarioPriorConsistencyContractTest`
- `DegreeAwareScenarioPriorTest`

Closure criteria:
- evidence-present case: `incident_persists` prior mass for the arterial bucket is at least `historical bucket frequency - 5 percentage points` and at least `10 percentage points` above the low-degree alternative
- no-recent-evidence baseline case: arterial deviation from its historical bucket frequency stays within `±5 percentage points` and does not exceed low-degree deviation by more than `3 percentage points`
- preferential attachment is used as a signal only when it remains evidence-responsive instead of becoming unconditional incident bias

Repo anchors:
- `docs/taro_v11_architecture_plan.md`
- `docs/taro_v12_architecture_plan.md`
- `corridor_bucket_frequency.parquet` (planned canonical baseline-frequency publication artifact; may be published by `E2` or `E4`, but must exist as one canonical table for tests and calibration consumers)

#### `E5` Export Reproducibility and Research Evidence Pack

Core purpose:
- package learning, calibration, and compile decisions into a reproducible evidence set suitable for release and research reporting

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- publish candidate, decision, and validation reports with stable lineage fields
- freeze seeds, manifests, and calibration configs for reproducibility
- produce a research evidence pack that matches release artifacts and claimed results

Non-functional requirements:
- reproducible export under pinned inputs
- clear separation of accepted vs rejected candidates
- low ambiguity in lineage and validation evidence

Named test suites:
- `ReproducibilityPackTest` (planned)
- `LineageArtifactContractTest` (planned)
- `ResearchClaimFreezeTest` (planned)

Equivalence classes:
- same-input reproducibility rerun
- accepted vs rejected candidate evidence
- missing lineage field vs complete lineage field
- release-only vs research-report package

Dependencies:
- `E1`
- `E2`
- `E3`
- `E4`

Shared contract dependencies:
- none

Closure criteria:
- one reproducibility pack can recreate the learning and calibration decisions behind a release artifact
- research claims and release evidence cannot diverge silently

Repo anchors:
- `docs/taro_v11_architecture_plan.md`
- `ResearchData/taro_v11_learning_pathway.md`
- `validation_report.json` (planned canonical artifact)

### Phase F: Product Surface and Production Loop

#### `F1` HTTP/API and Frontend Inspection Flow

Core purpose:
- expose retained result sets and future-aware products through a stable API and frontend retrieval flow

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- expose route, matrix, summary, detail, health, and admin endpoints for future-aware products
- provide stable request validation and error contracts
- support `resultSetId` retrieval for retained route and matrix results

Non-functional requirements:
- versioned API behavior
- predictable error semantics and caller scoping
- safe concurrency under retained-result access

Named test suites:
- `RouteApiContractTest` (planned)
- `MatrixApiContractTest` (planned)
- `RetainedResultApiTest` (planned)
- `ApiRequestValidationTest` (planned)

Equivalence classes:
- valid route vs matrix request
- valid vs invalid `resultSetId`
- caller-scoped vs caller-mismatched retrieval
- topology-compatible vs invalidated retained result

Dependencies:
- `C5`
- `D4`

Shared contract dependencies:
- none

Closure criteria:
- frontend retrieval flow can inspect retained future-aware results without recomputing them
- API error posture is explicit for expired, incompatible, or unauthorized retained results

Repo anchors:
- `docs/taro_v12_architecture_plan.md`
- `docs/taro_v12_v13_migration_report.md`
- `src/main/java/org/Aayush/routing/future/FutureRouteService.java`

#### `F2` Telemetry, Feedback, and Retraining Loop

Core purpose:
- connect served predictions and retained results back into a learning and calibration feedback loop

Current repo status:
- planned

Gate severity:
- calibration gate

Functional requirements:
- capture served prediction metadata and eventual outcome telemetry
- persist lineage, topology version, trait bundle, and scenario information with feedback events
- export feedback data into retraining-ready artifacts

Non-functional requirements:
- low-overhead telemetry capture
- privacy-safe and partitionable event records
- bounded buffering and backpressure control

Named test suites:
- `TelemetryLineageContractTest` (planned)
- `PredictionFeedbackIngestionTest` (planned)
- `RetrainingDatasetExportTest` (planned)

Equivalence classes:
- complete vs incomplete feedback payload
- delayed outcome arrival
- high-volume feedback ingestion
- retraining export with lineage filters

Dependencies:
- `F1`
- `E5`

Shared contract dependencies:
- none

Closure criteria:
- served predictions can be joined back to outcome telemetry with stable lineage
- retraining exports are possible without reconstructing ad hoc joins from scattered logs

Repo anchors:
- `docs/taro_v11_architecture_plan.md`
- `docs/taro_v12_architecture_plan.md`
- `telemetry_event.parquet` (planned canonical artifact)

#### `F3` Observability, Rollout, and Operational Governance

Core purpose:
- make TARO operationally safe to roll out, monitor, and govern at the system level

Current repo status:
- planned

Gate severity:
- release-critical

Functional requirements:
- expose metrics and health signals for future-aware serving, reload, and retained-result behavior
- provide alerting and dashboard posture for parity, reload, and latency regressions
- define rollout, rollback, and governance rules for builder-time and serving-time changes

Non-functional requirements:
- low-cardinality metric labels
- minimal runtime overhead
- actionable operator-facing semantics

Named test suites:
- `MetricsContractTest` (planned)
- `ReloadAlertingTest` (planned)
- `OperationalGovernanceSmokeTest` (planned)

Equivalence classes:
- healthy vs degraded reload
- retained-result memory pressure
- route/matrix latency regression
- parity drift and contract-test failure

Dependencies:
- `F1`
- `F2`
- `D4`

Shared contract dependencies:
- none

Closure criteria:
- the system exposes enough metrics and operational policy to detect and govern failures in future-aware serving and topology evolution
- rollout posture is explicit for both builder-side and serving-side changes

Repo anchors:
- `docs/taro_v13_architecture_plan.md`
- `docs/taro_v12_v13_migration_report.md`
- `docs/trait_runtime_lock_audit_report.md`

## 12. Practical Reading Order

When using this document:

1. Start here for staged roadmap and sequencing questions.
2. Read `docs/taro_v13_architecture_plan.md` for current topology and reload architecture.
3. Read `docs/taro_v12_architecture_plan.md` for future-aware product semantics.
4. Use `docs/taro_v11_ssot_stagewise_breakdown.md` only when historical v11 stage numbering needs to be mapped back.

## 13. Completion Conditions For This Documentation Reset

This v14 staged breakdown becomes the canonical roadmap only when:

- it is added to `AGENTS.md` as the primary staged implementation reference
- the older v11 stagewise breakdown is explicitly marked historical and temporally incomplete
- `docs/codex_repo_concern_map.md` points roadmap and stage questions here first
- the matching PDF is rendered successfully
