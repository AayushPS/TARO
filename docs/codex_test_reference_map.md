# TARO Test Reference Map for Future Codex Sessions

Status date: 2026-03-27
Purpose: map the current test corpus to the concerns it protects, so future chats can quickly choose the right evidence and regression suite.

## 1. Test lanes and how they run

The Maven build uses JUnit tags and excludes `perf` by default.

| Command | What it runs |
|---|---|
| `mvn test` | default lane; excludes `perf`, so unit plus untagged tests and tagged `integration` / `smoke` tests still run |
| `mvn -Psmoke-tests test` | only `@Tag("smoke")` tests |
| `mvn -Pintegration-tests test` | only `@Tag("integration")` tests |
| `mvn -Pperf-tests test` | `@Tag("perf")` tests, with the default perf exclusion removed |
| `.venv/bin/python -m pytest -q` | Python tests under `src/main/python/tests` |

Useful scripts:

- `scripts/run_java_tests.sh`
- `scripts/run_python_tests.sh`

## 2. Test helper files worth knowing first

These are not test suites themselves, but many important tests depend on them.

| File | Why it matters |
|---|---|
| `src/test/java/org/Aayush/routing/testutil/RoutingFixtureFactory.java` | shared FlatBuffer-backed runtime fixtures for route, matrix, graph, and cost tests |
| `src/test/java/org/Aayush/routing/testutil/TemporalTestContexts.java` | pre-bound Stage 16 temporal contexts for lower-level tests |
| `src/test/java/org/Aayush/routing/testutil/TransitionTestContexts.java` | pre-bound Stage 17 transition contexts for lower-level tests |
| `src/test/java/org/Aayush/routing/topology/TopologyTestFixtures.java` | topology v13 harness, fixed clock, strict quarantine resolver, and grid-source helpers |

## 3. Test map by package

### 3.1 App and shared primitives

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/app/MainTest.java` | the tiny CLI entrypoint prints the expected sample output | smoke-level sanity only |
| `src/test/java/org/Aayush/core/id/FastUtilIDMapperTest.java` | functional mapping, unicode, dense-index validation, duplicate detection, concurrency, lookup complexity, memory, immutability, and collision behavior | best coverage for Stage 1 id mapping |
| `src/test/java/org/Aayush/core/time/TimeUtilsTest.java` | bucket/day/time-of-day calculations, FIFO checks, tick normalization, overflow validation, time-range formatting, DST handling, and perf smoke | best low-level time-math regression suite |

### 3.2 Serialization and schema contract

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/serialization/flatbuffers/ModelContractValidatorTest.java` | metadata/schema-version/time-unit/profile-timezone validation | quickest place to confirm the root model metadata contract |
| `src/test/java/org/Aayush/serialization/flatbuffers/TaroModelTest.java` | CSR traversal semantics, id-mapping expectations, landmark vectors, and end-to-end FlatBuffer read/write smoke checks | older schema-level integration test, still useful for raw storage questions |

### 3.3 Graph, profile, overlay, spatial, and cost runtime

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/graph/EdgeGraphTest.java` | graph loading, CSR traversal, coordinates, iterator behavior, validation failures, metadata contract checks, concurrency, memory, and throughput | biggest source of graph runtime evidence |
| `src/test/java/org/Aayush/routing/graph/TurnCostMapTest.java` | turn-cost parsing, lookup semantics, collisions, duplicate-key behavior, invalid buffers, concurrency, hash distribution, and lookup performance | best source for forbidden-turn and hash-table behavior |
| `src/test/java/org/Aayush/routing/profile/ProfileStoreTest.java` | direct lookup, day-mask fallback, interpolation, metadata, malformed profiles, accessor validation, and concurrent reads | Stage 9 confidence suite |
| `src/test/java/org/Aayush/routing/overlay/LiveOverlayTest.java` | update validation, TTL handling, capacity policies, active/missing/expired/block semantics, cleanup behavior, clear, and concurrent read/write behavior | Stage 7 behavioral source of truth |
| `src/test/java/org/Aayush/routing/spatial/SpatialRuntimeTest.java` | nearest-node lookup, deterministic tie-breaks, disabled runtime path, malformed KD data, brute-force parity, query validation, perf smoke, and concurrent reads | best Stage 8 spatial evidence |
| `src/test/java/org/Aayush/routing/cost/CostEngineTest.java` | interpolated/discrete temporal sampling, live penalties, turn costs, forbidden turns, invalid transition decisions, explain-path parity, concurrent determinism, and linear-vs-calendar temporal behavior | best source for the final cost formula |
| `src/test/java/org/Aayush/routing/geometry/GeometryDistanceTest.java` | Euclidean distance, longitude normalization, and antimeridian great-circle behavior | focused geometry helper coverage |

### 3.4 Search infrastructure and heuristics

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/search/SearchInfrastructureTest.java` | ordering and reuse behavior for `SearchState`, `VisitedSet`, and `SearchQueue`, including pool exhaustion and random-operation robustness | read this for Stage 6 internals |
| `src/test/java/org/Aayush/routing/heuristic/GeometryHeuristicTest.java` | known Euclidean/spherical distances, boundary stability, concurrent determinism, and perf smoke | geometry-provider behavior |
| `src/test/java/org/Aayush/routing/heuristic/HeuristicAdmissibilityTest.java` | admissibility of Euclidean/spherical heuristics plus randomized stress | quickest answer for "is the heuristic still admissible?" |
| `src/test/java/org/Aayush/routing/heuristic/HeuristicConfigurationExceptionTest.java` | reason-code and constructor contracts for heuristic configuration failures | small contract test |
| `src/test/java/org/Aayush/routing/heuristic/HeuristicFactoryTest.java` | required inputs, coordinate validation, cost-engine contract matching, bounds safety, concurrent factory stress, and perf smoke | best entrypoint-level heuristic test suite |
| `src/test/java/org/Aayush/routing/heuristic/LandmarkArtifactTest.java` | validation and copy behavior for the landmark artifact container | artifact-level unit suite |
| `src/test/java/org/Aayush/routing/heuristic/LandmarkHeuristicProviderTest.java` | direct landmark-provider binding, goal/node bound checks, and node-count mismatch rejection | focused provider-level LANDMARK coverage |
| `src/test/java/org/Aayush/routing/heuristic/LandmarkHeuristicFactoryTest.java` | landmark store requirement, node-count compatibility, signature requirement, admissibility, and mismatch rejection | best LANDMARK binding test |
| `src/test/java/org/Aayush/routing/heuristic/LandmarkPreprocessorTest.java` | deterministic preprocessing, unreachable distance handling, config validation, serializer round-trip, weekday-profile admissibility, and stress/perf | best Stage 12 preprocessing suite |
| `src/test/java/org/Aayush/routing/heuristic/LandmarkStoreTest.java` | accessor validation, defensive copies, signature loading, and invalid-distance rejection | runtime landmark storage behavior |

### 3.5 Core runtime, planners, and stage-specific route/matrix behavior

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/core/RouteCoreTest.java` | request validation, constructor/runtime binding contracts, reachable/unreachable routes, A*/Dijkstra parity, landmark integration, matrix wiring, custom planner injection, exception remapping, and repeated determinism | the single most important general runtime regression suite |
| `src/test/java/org/Aayush/routing/core/BidirectionalTdAStarPlannerTest.java` | Stage 13 planner parity, frontier/label budgets, stale-state handling, overlay churn, overflow behavior, pruning logic, and contract mismatches | best detailed planner-behavior suite |
| `src/test/java/org/Aayush/routing/core/NativeOneToManyMatrixPlannerTest.java` | Stage 14 native matrix equivalence, unreachable/duplicate targets, deterministic repeats, A* native path, compatibility fallback, early termination, and context reuse | best matrix planner suite |
| `src/test/java/org/Aayush/routing/core/MatrixQueryContextTest.java` | row-level matrix query state tracking and reset/reuse behavior | narrow but useful internal coverage |
| `src/test/java/org/Aayush/routing/core/FutureRouteObjectivePlannerTest.java` | direct multi-scenario objective-planner edge cases: source==target, unreachable target, aggregate compromise route, plus focused tie-break and budget guards | best focused unit suite for the new expected/robust winner planner |
| `src/test/java/org/Aayush/routing/core/FutureRouteObjectivePlannerWeaknessFinderTest.java` | exhaustive-oracle comparison for expected/robust winners on adversarial C3 fixtures plus deterministic frontier/label/settled budget overruns | best falsification-oriented C3 planner suite |
| `src/test/java/org/Aayush/routing/core/FutureScenarioSupportTest.java` | scenario cost-engine cloning and scenario-bundle validation error paths | best focused shared future-helper suite |
| `src/test/java/org/Aayush/routing/core/ExecutionProfileRouteCoreTest.java` | startup execution-profile binding, request selector compatibility, and legacy per-request selector support | execution-profile integration with `RouteCore` |
| `src/test/java/org/Aayush/routing/core/MaintainabilityGuardrailTest.java` | line-budget guards and extension-seam/interface checks for key runtime files | protects codebase shape, not runtime behavior |
| `src/test/java/org/Aayush/routing/core/Stage13PrimitiveGuardrailTest.java` | Stage 13 budget defaults, numeric safety, path-evaluator guards, reverse index, query context reset, and Dijkstra budget enforcement | lower-level Stage 13 safety net |
| `src/test/java/org/Aayush/routing/core/Stage14PrimitiveGuardrailTest.java` | Stage 14 matrix budget defaults, frontier/request work guards, and route-to-matrix budget reason remapping | lower-level Stage 14 safety net |
| `src/test/java/org/Aayush/routing/core/Stage15AddressingTraitTest.java` | typed external-id and coordinate routing, mixed mode, snap thresholds, invalid payloads, startup lock, strategy mismatch, telemetry, and mapping-failure wrapping | primary Stage 15 correctness suite |
| `src/test/java/org/Aayush/routing/core/Stage15AddressingTraitStressPerfTest.java` | coordinate-route perf smoke, concurrent determinism, mixed-mode concurrency, and matrix dedup stress | `@Tag("perf")` |
| `src/test/java/org/Aayush/routing/core/Stage16TemporalTraitTest.java` | linear vs calendar semantics, timezone use, temporal-resolution failure mapping, DST determinism, and strict internal-context requirements | primary Stage 16 correctness suite |
| `src/test/java/org/Aayush/routing/core/Stage16TemporalTraitStressPerfTest.java` | temporal-mode concurrency determinism and route/matrix perf smoke | `@Tag("perf")` |
| `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitTest.java` | required config, finite-turn application vs ignore behavior, route/matrix parity across modes, compatibility fallback, forbidden turns, and missing turn-map behavior | primary Stage 17 correctness suite |
| `src/test/java/org/Aayush/routing/core/Stage17TransitionTraitStressPerfTest.java` | no-turn parity/perf, moderate-turn perf, concurrency determinism, and live-overlay churn behavior | `@Tag("perf")` |
| `src/test/java/org/Aayush/routing/core/Stage18TraitRegistryTest.java` | named bundle success, startup lock under bundles, bundle-vs-legacy conflict rejection, and synthesized bundle context | primary Stage 18 integration suite |
| `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java` | randomized route/matrix stress, concurrent determinism, landmark perf, overlay churn, and stage 13/14 delta-report-style comparisons | `@Tag("perf")`; broad system stress |
| `src/test/java/org/Aayush/routing/core/SystemIntegrationStressPerfTest.java` | heavy mixed-traffic concurrency, transition-mode determinism, native matrix limit behavior, and Stage 18 parity over edge cases | `@Tag("integration")`, `@Tag("perf")` |

### 3.6 Execution profile package

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/execution/ExecutionProfileRegistryTest.java` | empty/default registry shape, id normalization, immutable ids, and duplicate/null profile rejection | focused registry contract suite |
| `src/test/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinderTest.java` | named-profile binding, Dijkstra/heuristic compatibility, missing config rejection, and landmark artifact compatibility | best unit test for execution-profile binding |
| `src/test/java/org/Aayush/routing/execution/RouterRuntimeManagerTest.java` | validate-vs-apply behavior and in-flight request pinning during execution-profile swaps | best runtime-swap test for execution profiles |

### 3.7 Addressing, temporal, transition, and trait-registry packages

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/traits/addressing/AddressingPolicyTest.java` | snap-distance defaults, overrides, boundary validation, and strategy-specific defaults | config-policy unit suite |
| `src/test/java/org/Aayush/routing/traits/addressing/CoordinateDistanceStrategyTest.java` | default max-snap behavior and reason-coded coordinate validation exceptions | tiny helper contract suite |
| `src/test/java/org/Aayush/routing/traits/addressing/AddressingTraitCatalogTest.java` | built-ins, overrides, null handling, missing defaults, and invalid ids | catalog contract suite |
| `src/test/java/org/Aayush/routing/traits/addressing/AddressingTraitEngineCacheTest.java` | segmented LRU cache behavior for coordinate resolution | internal cache behavior |
| `src/test/java/org/Aayush/routing/traits/addressing/CoordinateStrategyRegistryTest.java` | default registry shape, overrides, constructor validation, and built-in strategy contracts | strategy-registry suite |
| `src/test/java/org/Aayush/routing/traits/temporal/CalendarTemporalResolutionStrategyTest.java` | fixed-offset resolution, cache/direct parity, and extreme-epoch clamping | strategy-level Stage 16 behavior |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalContextResolverTest.java` | accessors, exception wrapping, and zone/cache preconditions | runtime resolver unit suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalGranularityCompetencyTest.java` | published drift-budget semantics, strict vs permissive coarse-bucket policy, and same-request bounded-drift checks across allowed granularities | primary B1 temporal-granularity competency suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalOffsetCacheTest.java` | zone requirement, UTC/fixed-offset behavior, DST offset changes, clamping, and boundary fallbacks | offset-cache unit suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalPolicyTest.java` | trait/strategy/timezone-policy compatibility rules and unknown trait handling | Stage 16 policy checks |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalRuntimeBinderTest.java` | missing/blank/unknown configs, timezone requirements, model timezone metadata rules, id trimming, valid bindings, and failure mapping | main Stage 16 binder suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalStrategyRegistryTest.java` | built-ins, overrides, explicit registries, null handling, and invalid entries | strategy-registry suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalTimezonePolicyRegistryTest.java` | UTC and model-timezone policy behavior plus registry validation | timezone-policy registry suite |
| `src/test/java/org/Aayush/routing/traits/temporal/TemporalTraitCatalogTest.java` | built-ins, overrides, explicit catalogs, null handling, and invalid ids/strategies | trait-catalog suite |
| `src/test/java/org/Aayush/routing/traits/transition/TransitionCostStrategyTest.java` | packed-turn-decision round trip, canonicalization, neutral/finite/forbidden behavior, and built-in strategy parity | low-level transition strategy semantics |
| `src/test/java/org/Aayush/routing/traits/transition/TransitionPolicyTest.java` | trait/strategy compatibility, forbidden-turn handling, and finite-turn requirements | Stage 17 policy checks |
| `src/test/java/org/Aayush/routing/traits/transition/TransitionRuntimeBinderTest.java` | missing/blank/unknown configs, semantic contract violations, custom strategy drift rejection, trimming, and built-in bindings | main Stage 17 binder suite |
| `src/test/java/org/Aayush/routing/traits/transition/TransitionStrategyRegistryTest.java` | built-ins, overrides, explicit registries, invalid entries, and packed-fallback compatibility | transition strategy registry suite |
| `src/test/java/org/Aayush/routing/traits/transition/TransitionTraitCatalogTest.java` | built-ins, overrides, explicit catalogs, null handling, and invalid ids/strategies | transition trait-catalog suite |
| `src/test/java/org/Aayush/routing/traits/registry/TraitBundleCompatibilityPolicyTest.java` | addressing/coordinate-strategy dependency and incompatibility rules | Stage 18 compatibility policy |
| `src/test/java/org/Aayush/routing/traits/registry/TraitBundleHasherTest.java` | stable trait-hash generation, concurrency stability, and inclusion of resolved transition strategy | Stage 18 hash behavior |
| `src/test/java/org/Aayush/routing/traits/registry/TraitBundleRegistryTest.java` | bundle-id normalization and duplicate rejection | registry contract suite |
| `src/test/java/org/Aayush/routing/traits/registry/TraitBundleRuntimeBinderTest.java` | named/inline bundles, unknown bundle rejection, config requirements, legacy conflicts, dependency checks, compatibility errors, and trait-hash failure mapping | main Stage 18 binder suite |

### 3.8 Future-aware v12 tests

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/future/DefaultScenarioBundleResolverTest.java` | baseline bundle generation plus incident-persistent and clearing-fast scenario expansion from topology-bound quarantine state | best focused resolver branch suite |
| `src/test/java/org/Aayush/routing/future/RecencyCalibrationTest.java` | recurring-evidence freshness weighting, TTL-independence of recency calibration, and probability-audit parity for fresh vs stale signals | focused B4 recurring-recency suite |
| `src/test/java/org/Aayush/routing/future/TemporalFreshnessOrderingTest.java` | monotonic freshness ordering for fresh, stale, and legacy-unknown quarantine evidence plus observed-at audit handling | focused B4 quarantine-recency suite |
| `src/test/java/org/Aayush/routing/future/NearHorizonPriorShiftTest.java` | stronger near-horizon than far-horizon incident-prior shift under the same fresh evidence, with probability normalization preserved | focused B4 horizon-sensitivity suite |
| `src/test/java/org/Aayush/routing/future/DirectionalityAsymmetryTest.java` | one-way corridor and turn-sensitive directional asymmetry surviving future-serving output with route provenance retained | focused B5 future-serving asymmetry suite |
| `src/test/java/org/Aayush/routing/future/DensityCalibrationTest.java` | low-density collapse vs aggregate-rescued high-density candidate families plus auditable density calibration reports | focused B5 density-calibration suite |
| `src/test/java/org/Aayush/routing/future/ScenarioConfidenceCalibrationTest.java` | recurring-evidence confidence tiers, fresh-vs-stale incident probability ordering, and probability-audit alignment on shipped recurring scenarios | focused B6 scenario-confidence calibration suite |
| `src/test/java/org/Aayush/routing/future/DegreeAwareScenarioPriorTest.java` | evidence-present vs no-recent-evidence degree-aware prior shaping, historical-bucket deviation bounds, and anti-arterial-bias checks for quarantine scenarios | focused B6 degree-aware prior suite |
| `src/test/java/org/Aayush/routing/future/BundleProbabilityCalibrationTest.java` | recurring and quarantine bundle normalization, audit-aligned labels, and structural-prior metadata parity across materialized scenario bundles | focused B6 bundle-probability suite |
| `src/test/java/org/Aayush/routing/future/AggregateCompromiseRouteContractTest.java` | dedicated C3 closure coverage for aggregate compromise winners, expected-vs-robust divergence, zero-optimality aggregate winners, and dense-vs-sparse calibration posture | `@Tag("smoke")`, `@Tag("integration")`; best stage-owned C3 smoke suite |
| `src/test/java/org/Aayush/routing/future/C3AggregateObjectivePerfSmokeTest.java` | route-only C3 aggregate-planning perf smoke on a deterministic branching ladder with repeated winner/density stability checks | `@Tag("perf")`, `@Tag("integration")`; dedicated C3 perf lane |
| `src/test/java/org/Aayush/routing/future/AlternativeConfidenceContractTest.java` | dedicated C4 closure coverage for distinct expected/robust winners, aggregate-only winners, near-identical alternative deduplication, and confidence-aware explanation propagation | `@Tag("integration")`; primary stage-owned C4 contract suite |
| `src/test/java/org/Aayush/routing/future/TemporalFidelityContractTest.java` | cross-phase B→C fidelity coverage for persistent and periodic activation behavior, recency-sensitive prior shifts, directional asymmetry, aggregate compromise preservation, and route/matrix scenario-order alignment | `@Tag("integration")`; best shared temporal-fidelity contract suite spanning B3-C3 behavior |
| `src/test/java/org/Aayush/routing/future/ScenarioPriorConsistencyContractTest.java` | cross-phase E→C prior-consistency coverage for route/matrix bundle parity, served dominant-scenario explanation alignment, and audited scenario-prior metadata preservation | `@Tag("integration")`; shared B6/C1/C4/E4 contract suite |
| `src/test/java/org/Aayush/routing/future/FutureRouteServiceTest.java` | expected/robust/top-K aggregation, aggregate-only compromise-route selection, zero-optimality winner semantics, retained results, and topology-aware future-route flow | `@Tag("integration")`; best v12 route suite |
| `src/test/java/org/Aayush/routing/future/FutureMatrixServiceTest.java` | scenario-aware matrix aggregation, retained result sets, and topology-aware future-matrix flow | `@Tag("integration")`; best v12 matrix suite |
| `src/test/java/org/Aayush/routing/future/InMemoryEphemeralResultStoreTest.java` | route-store TTL and byte-budget eviction plus matrix-store compaction, compression, and oversize non-admission | best direct retained-store hardening suite |
| `src/test/java/org/Aayush/api/RouteApiContractTest.java` | route endpoint evaluation, retained-summary response shape, and health-endpoint topology posture through the F1 HTTP surface | `@Tag("integration")` via Spring Boot; primary F1 route/health contract suite |
| `src/test/java/org/Aayush/api/MatrixApiContractTest.java` | matrix endpoint evaluation and retained-summary retrieval through the F1 HTTP surface | `@Tag("integration")` via Spring Boot; primary F1 matrix contract suite |
| `src/test/java/org/Aayush/api/ApiRequestValidationTest.java` | explicit invalid-request posture for malformed payloads, invalid objectives, and missing caller headers | `@Tag("integration")` via Spring Boot; primary F1 validation/error contract suite |
| `src/test/java/org/Aayush/routing/future/RetainedResultApiTest.java` | stable retained route and matrix summary/detail lookup, expiry and oversize absence semantics, and reload compatibility behavior through the typed retrieval surface | `@Tag("integration")`; dedicated C5 retained-result retrieval-contract suite |
| `src/test/java/org/Aayush/api/TelemetryLineageContractTest.java` | route and matrix predictions served through the F1 HTTP surface capture stable topology, scenario, trait-bundle, execution-profile, and quarantine lineage in the F2 telemetry store | `@Tag("integration")`; primary F2 lineage-join contract suite |
| `src/test/java/org/Aayush/api/PredictionFeedbackIngestionTest.java` | partial-vs-complete feedback updates, delayed outcome merge, explicit caller-mismatch rejection, and deterministic bounded telemetry eviction through the F2 feedback API | `@Tag("integration")`; primary F2 ingestion and backpressure suite |
| `src/test/java/org/Aayush/api/RetrainingDatasetExportTest.java` | already-joined telemetry export rows remain filterable by topology version, scenario bundle, trait hash, and completeness without ad hoc joins | untagged Java contract suite; primary F2 retraining-export seam test |
| `src/test/java/org/Aayush/api/MetricsContractTest.java` | live metrics endpoint coverage for serving counts, retained-result store pressure state, reload metadata, and alert-severity coherence | `@Tag("integration")`; primary F3 metrics contract suite |
| `src/test/java/org/Aayush/api/ReloadAlertingTest.java` | deterministic F3 reload, retained-result pressure, route/matrix latency, and parity-drift alert classification without wall-clock-dependent HTTP overhead | direct Java suite; primary F3 alert posture suite |
| `src/test/java/org/Aayush/api/OperationalGovernanceSmokeTest.java` | governance endpoint rollout/rollback posture and compact health endpoint alert summary through the live API surface | `@Tag("smoke")`, `@Tag("integration")`; primary F3 governance smoke suite |
| `src/test/java/org/Aayush/routing/future/FutureResultStoreSizingTest.java` | retained-result sizing regressions for route and matrix metadata plus null-safe sizing-helper behavior | best direct retained-result sizing regression suite |

### 3.9 Topology and reload v13 tests

| Test file | What it verifies | Notes |
|---|---|---|
| `src/test/java/org/Aayush/routing/topology/FailureQuarantineTest.java` | node/edge quarantine expansion into live updates and explanation tags | `@Tag("integration")`; best v13 fast-path suite |
| `src/test/java/org/Aayush/routing/topology/TopologyModelCompilerTest.java` | balanced KD rebuild shape and nearest-node parity against brute force after builder compilation | quickest spatial rebuild regression suite |
| `src/test/java/org/Aayush/routing/topology/TopologyModelSourceTest.java` | source-topology validation for coordinate posture, profiles, edges, and turn-cost contracts | focused source-validation suite |
| `src/test/java/org/Aayush/routing/topology/PersistentBaselineProfileTest.java` | constant and noisy persistent temporal baselines plus direction-specific cost asymmetry surviving compile/load | first focused B2 persistence/asymmetry suite; `@Tag("smoke")` |
| `src/test/java/org/Aayush/routing/topology/DirectedProfileDivergenceTest.java` | compiled opposing directional profiles remain distinct and drive different served winners after compile/load | focused B5 compile/load asymmetry suite; `@Tag("integration")` |
| `src/test/java/org/Aayush/routing/topology/AsymmetricCorridorReloadTest.java` | asymmetric corridor behavior survives harmless rebuild/reload when the subject remains, and drops cleanly to the remaining directed path when the subject is removed | `@Tag("integration")`; focused D3 reload-preserved asymmetry contract suite |
| `src/test/java/org/Aayush/routing/topology/ProfileFifoRepairGateTest.java` | startup rejection of FIFO-violating directed edge profiles and acceptance of boundary-neutral FIFO cases | first focused B2 FIFO gate suite; `@Tag("smoke")` |
| `src/test/java/org/Aayush/routing/topology/ProfileContractPerfTest.java` | directed-edge profile-contract validation throughput on a large topology | focused B2 startup-validator perf guard; `@Tag("perf")` |
| `src/test/java/org/Aayush/routing/topology/QuarantineRecencyOverrideTest.java` | near-horizon recency override from fresh quarantine evidence into incident probability and future route output | `@Tag("integration")`; focused D1 recency override contract suite |
| `src/test/java/org/Aayush/routing/topology/ReloadTemporalContinuityContractTest.java` | deterministic active-failure carry-over across reload plus bounded skip of failures expired at publication time | `@Tag("integration")`; focused D1 reload continuity contract suite |
| `src/test/java/org/Aayush/routing/topology/StructuralChangeApplierTest.java` | typed structural changes, coordinate/profile/turn updates, and node-removal incident-edge cleanup | core change-set application behavior |
| `src/test/java/org/Aayush/routing/topology/TopologyPublicationServiceTest.java` | validate-only publication, atomic publication integrated with future services, quarantine carry-over across reload, and removed-subject quarantine drop | `@Tag("integration")`; best publication-path suite |
| `src/test/java/org/Aayush/routing/topology/TopologyReloadCoordinatorTest.java` | retained-result invalidation vs retention across reloads | `@Tag("integration")`; reload compatibility behavior |
| `src/test/java/org/Aayush/routing/topology/TopologyReloadSmokeTest.java` | add-edge, add-node, unchanged-query coordinate parity, retained-result compatibility, edge-drop plus quarantine parity, and node-failure smoke scenarios | `@Tag("smoke")`, `@Tag("integration")` |
| `src/test/java/org/Aayush/routing/topology/TopologyReloadPerfTest.java` | published-topology performance guardrail | `@Tag("perf")`, `@Tag("integration")` |
| `src/test/java/org/Aayush/app/MainTest.java` | Spring Boot application bootstrap smoke for the F1 entrypoint | context-load guard for the canonical HTTP launcher |

### 3.10 Python tests

| Test file | What it verifies | Notes |
|---|---|---|
| `src/main/python/tests/test_IDMapper.py` | Python `IDMapper` behavior: mapping, unicode, long strings, consistency, errors, lookup performance, collision behavior, immutability, and membership checks | current Python-side main test suite |
| `src/main/python/tests/test_package_layout.py` | package import contract and module path expectations for `src/main/python/Utils` | protects Python package layout assumptions |
| `src/main/python/tests/learning/test_dataset_manifest_contract.py` | E1 dataset-manifest determinism, lineage/filter preservation, and canonical `dataset_manifest.json` emission | primary E1 manifest contract suite |
| `src/main/python/tests/learning/test_source_validation_ingestion.py` | E1 multi-feed ingestion, temporal/unit validation, duplicate-id rejection, and late-arrival diagnostics | primary E1 validation suite |
| `src/main/python/tests/learning/test_large_batch_ingestion_smoke.py` | E1 larger-batch ingestion determinism and manifest summarization over many rows | E1 non-functional smoke coverage |
| `src/main/python/tests/learning/test_sequence_builder_contract.py` | E2 deterministic edge/corridor/time-window sequence construction, auditable bucket-policy validation, and canonical `sequence_dataset.parquet` round-trip | primary E2 sequence contract suite |
| `src/main/python/tests/learning/test_temporal_feature_surface.py` | E2 recency-gap, persistence-run, periodicity, and corridor-activity feature publication over stale-vs-recent corridor history | primary E2 temporal feature-surface suite |
| `src/main/python/tests/learning/test_corridor_bucket_frequency_artifact.py` | E2 dense-vs-sparse corridor historical bucket baseline construction and canonical `corridor_bucket_frequency.parquet` round-trip | primary E2 corridor-frequency artifact suite |
| `src/main/python/tests/learning/test_forecast_representation_learning_smoke.py` | E3 deterministic learned representation and forecast-surface construction over E2 artifacts, including bounded history-window behavior | primary E3 forecast/representation smoke suite |
| `src/main/python/tests/learning/test_temporal_attribute_probe.py` | E3 competence probing across persistence, periodicity, recency, direction, density, and low-data limitation cases | primary E3 temporal attribute-probe suite |
| `src/main/python/tests/learning/test_ablation_determinism.py` | E3 richer-vs-profile-only ablation behavior and exact repeated-run determinism under pinned seed/input | primary E3 ablation and determinism suite |
| `src/main/python/tests/learning/test_forecast_calibration.py` | E4 auditable calibration-bundle construction, canonical corridor-baseline publication, and evidence-responsive preferential-attachment uplift on dense vs sparse corridor priors | primary E4 forecast-calibration suite |
| `src/main/python/tests/learning/test_confidence_gate_selection.py` | E4 confidence-qualified vs confidence-rejected refined-profile selection and historical-baseline fallback for rejected scenario priors | primary E4 confidence-gating suite |
| `src/main/python/tests/learning/test_reproducibility_pack.py` | E5 same-input reproducibility reruns, full pack round-trip reconstruction, and accepted-vs-rejected candidate/decision evidence separation | primary E5 reproducibility-pack suite |
| `src/main/python/tests/learning/test_lineage_artifact_contract.py` | E5 missing-lineage rejection and stable release-lineage propagation across config, report, and pack artifacts | primary E5 lineage contract suite |
| `src/main/python/tests/learning/test_research_claim_freeze.py` | E5 release-only vs release-and-research packaging behavior and rejection of claims that diverge from validation evidence | primary E5 research-claim freeze suite |
| `src/main/python/tests/learning/test_feedback_telemetry_event_artifact.py` | F2 canonical `telemetry_event.parquet` round-trip, metadata preservation, and empty filtered-export validity | primary F2 parquet artifact contract suite |

## 4. Best suites to run by concern

| If you changed... | Run these first |
|---|---|
| request normalization or route facade logic | `RouteCoreTest`, `ExecutionProfileRouteCoreTest` |
| route planner internals | `BidirectionalTdAStarPlannerTest`, `Stage13PrimitiveGuardrailTest`, `RouteCoreStressPerfTest` |
| matrix planner internals | `NativeOneToManyMatrixPlannerTest`, `Stage14PrimitiveGuardrailTest`, `RouteCoreStressPerfTest` |
| addressing or spatial snapping | `Stage15AddressingTraitTest`, `AddressingPolicyTest`, `SpatialRuntimeTest`, `AddressingTraitEngineCacheTest` |
| temporal binding or time math | `Stage16TemporalTraitTest`, `TemporalRuntimeBinderTest`, `TimeUtilsTest`, `TemporalContextResolverTest`, `TemporalGranularityCompetencyTest` |
| transition behavior or turn costs | `Stage17TransitionTraitTest`, `TransitionRuntimeBinderTest`, `TurnCostMapTest`, `CostEngineTest` |
| trait-bundle logic | `Stage18TraitRegistryTest`, `TraitBundleRuntimeBinderTest`, `TraitBundleCompatibilityPolicyTest`, `TraitBundleHasherTest` |
| execution-profile binding | `ExecutionProfileRegistryTest`, `DefaultExecutionRuntimeBinderTest`, `RouterRuntimeManagerTest`, `ExecutionProfileRouteCoreTest` |
| heuristics | `HeuristicFactoryTest`, `HeuristicAdmissibilityTest`, `GeometryHeuristicTest`, `GeometryDistanceTest`, `LandmarkHeuristicProviderTest`, landmark suites |
| HTTP/API product surface | `MainTest`, `RouteApiContractTest`, `MatrixApiContractTest`, `ApiRequestValidationTest`, `RetainedResultApiTest` |
| feedback telemetry / retraining export | `TelemetryLineageContractTest`, `PredictionFeedbackIngestionTest`, `RetrainingDatasetExportTest`, `test_feedback_telemetry_event_artifact.py` |
| operational metrics / governance / rollout posture | `MetricsContractTest`, `ReloadAlertingTest`, `OperationalGovernanceSmokeTest` |
| future-aware v12 logic | `FutureRouteObjectivePlannerTest`, `FutureScenarioSupportTest`, `DefaultScenarioBundleResolverTest`, `ScenarioConfidenceCalibrationTest`, `DegreeAwareScenarioPriorTest`, `BundleProbabilityCalibrationTest`, `DirectionalityAsymmetryTest`, `DensityCalibrationTest`, `TemporalFidelityContractTest`, `ScenarioPriorConsistencyContractTest`, `FutureRouteServiceTest`, `FutureMatrixServiceTest`, `InMemoryEphemeralResultStoreTest`, `TopologyReloadCoordinatorTest`, `QuarantineRecencyOverrideTest` |
| topology publication / reload | `TopologyModelSourceTest`, `DirectedProfileDivergenceTest`, `StructuralChangeApplierTest`, `TopologyModelCompilerTest`, `TopologyPublicationServiceTest`, `TopologyReloadSmokeTest`, `FailureQuarantineTest`, `ReloadTemporalContinuityContractTest` |
| temporal profile integrity / B2 gates | `PersistentBaselineProfileTest`, `ProfileFifoRepairGateTest`, `ProfileContractPerfTest`, `CostEngineTest`, `ProfileStoreTest`, `TopologyModelSourceTest` |
| schema / generated model contracts | `ModelContractValidatorTest`, `TaroModelTest`, `EdgeGraphTest`, `ProfileStoreTest`, `TurnCostMapTest` |

## 5. Practical notes for future chats

- For behavior questions, prefer the large integration suites over tiny unit suites first:
  `RouteCoreTest`, `Stage15AddressingTraitTest`, `Stage17TransitionTraitTest`, `FutureRouteServiceTest`, `TopologyPublicationServiceTest`.
- For guardrail failures in CI, check whether the failure is a behavior regression or a structure-budget regression:
  `MaintainabilityGuardrailTest` is enforcing code shape, not runtime correctness.
- For performance claims, look at the `@Tag("perf")` suites and not only the default `mvn test` lane.
- For topology questions, `TopologyTestFixtures.java` is often the fastest way to understand how the topology services are expected to be assembled in tests.
