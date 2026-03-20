# TARO (Time-Aware Routing Orchestrator)

TARO is a deterministic time-dependent routing engine for systems where travel cost changes with entry time instead of staying static.

At its core, TARO treats movement as a compiled, queryable physics surface:

- a directed graph,
- time-varying edge multipliers,
- transition costs,
- bounded live overrides,
- and deterministic route or matrix search on top of that surface.

The repository already contains a working Java runtime for point-to-point and matrix routing, and the project roadmap now extends that base in three major directions:

- **v11**: offline learning to improve compiled temporal profiles,
- **v12**: future-aware serving with scenario-based route products,
- **v13**: topology evolution, transient failure quarantine, and atomic reload.

## What TARO Is Trying To Be

TARO is not just "shortest path on a graph."

It is a platform for answering questions like:

- What is the best route if traffic depends on when I reach each edge?
- How do I combine historical profiles with current conditions?
- How do I expose uncertainty honestly instead of pretending the future is fixed?
- How do I handle transient outages now and structural topology changes later without breaking determinism?

The design answer is:

1. keep the runtime deterministic,
2. compile learned or validated structure into immutable artifacts,
3. layer live operational state on top in bounded, explicit ways,
4. and only publish structural truth through validated rebuild + reload.

## Current Runtime Status

Implemented today:

- `RouteCore` as the main in-process routing facade
- `RouterService#route(RouteRequest)` for point-to-point routing
- `RouterService#matrix(MatrixRequest)` for many-to-many routing
- `DIJKSTRA` and `A_STAR` execution modes
- `NONE`, `EUCLIDEAN`, `SPHERICAL`, and `LANDMARK` heuristics
- Stage 15 typed addressing
- Stage 16 locked temporal runtime binding
- Stage 17 locked transition runtime binding
- Stage 18 trait-bundle/runtime binding
- startup-locked execution profile binding
- Stage 7 live overlay integration
- native matrix execution paths for `DIJKSTRA + NONE` and bounded-target `A_STAR`

Not implemented yet:

- production HTTP transport
- Stage 25 model loader / atomic hot reload
- v12 scenario-aware serving runtime
- v13 topology-evolution and failure-quarantine runtime

## Architectural Through-Line

TARO now spans three layers of ambition.

### Layer 1: Deterministic Runtime (implemented core)

The Java engine loads immutable topology/profile artifacts and answers deterministic route and matrix queries for a fixed model plus live snapshot.

Key rule:

`effective_edge_cost = base_weight * temporal_multiplier * live_penalty + turn_cost`

With current live overlay semantics:

- missing or expired override -> `1.0`
- active slowdown -> `1 / speed_factor`
- blocked edge -> `INF`

### Layer 2: Future-Aware Product Serving (v12)

v12 adds multiple plausible future traffic scenarios and produces three user-facing route products from the same evaluation run:

- **Expected ETA route**
- **Robust / P90 route**
- **Top-K scenario routes with confidence**

It also adds ephemeral result retention so the backend can finish the calculation first and let the frontend fetch summaries/details afterward by `resultSetId`.

### Layer 3: Topology Evolution and Failure Handling (v13)

v13 adds the operational model for infrequent structural change:

- add/drop node and edge changes are handled through batched rebuild + atomic reload
- transient failures are handled through fast quarantine / suppression
- retained future-aware result sets are versioned against model and topology snapshot identity

## Design Principles

### Determinism First

For a fixed model, fixed runtime binding, and fixed live snapshot, TARO should answer the same query the same way.

### Learning Stays Mostly Offline

v11 improves baseline temporal profiles through offline learning and exports deterministic artifacts. The hot routing path should not run stochastic training logic.

### Runtime Topology Is Immutable Per Snapshot

The active graph is not meant to be mutated in-place while queries are running. Structural truth is published through validated rebuild + reload.

### Operational State Is Explicit

Live slowdowns, blocks, transient failures, and future scenarios are represented as explicit runtime layers, not hidden side effects.

## Core Runtime Concepts

### 1. Graph

The runtime graph is edge-based and optimized for query-time traversal.

Data includes:

- node count
- edge count
- CSR adjacency
- edge origin/destination
- base weights
- profile ids
- optional coordinates

### 2. Temporal Profiles

Temporal profiles provide time-of-day and day-of-week multipliers for edges. The cost engine evaluates an edge using the entry time at that state.

### 3. Transition Costs

Turn or transition penalties are applied according to the bound transition runtime mode.

### 4. Live Overlay

The live overlay is a bounded runtime layer for short-lived per-edge slowdowns or hard blocks.

### 5. Heuristics

Current A* heuristics are admissible lower bounds. They are time-safe, but today they are still goal-bound rather than arrival-time-conditioned.

### 6. Traits and Execution Binding

Addressing, temporal mode, transition mode, trait bundles, and execution profile are chosen at startup and then locked for that `RouteCore` instance.

## Implemented Public Java Contracts

### Router Service

Primary runtime interface:

```java
public interface RouterService {
    RouteResponse route(RouteRequest request);
    MatrixResponse matrix(MatrixRequest request);
}
```

### Route Request

Current client-facing fields:

- `sourceExternalId`
- `targetExternalId`
- `sourceAddress`
- `targetAddress`
- `allowMixedAddressing`
- `maxSnapDistance`
- `departureTicks`
- deprecated compatibility hints:
  - `algorithm`
  - `heuristicType`

Notes:

- startup execution profile binding is the real source of algorithm/heuristic selection
- request-level algorithm/heuristic fields are compatibility hints only

### Matrix Request

Current client-facing fields:

- `sourceExternalIds`
- `targetExternalIds`
- `sourceAddresses`
- `targetAddresses`
- `allowMixedAddressing`
- `maxSnapDistance`
- `departureTicks`
- deprecated compatibility hints:
  - `algorithm`
  - `heuristicType`

### Route Response

Current response shape includes:

- `reachable`
- `departureTicks`
- `arrivalTicks`
- `totalCost`
- `settledStates`
- `algorithm`
- `heuristicType`
- `sourceResolvedAddress`
- `targetResolvedAddress`
- `pathExternalNodeIds`

### Matrix Response

Current response shape includes:

- `sourceExternalIds`
- `targetExternalIds`
- `reachable`
- `totalCosts`
- `arrivalTicks`
- `algorithm`
- `heuristicType`
- `implementationNote`

## Addressing Contracts

Stage 15 supports typed addressing through `AddressInput`:

- `AddressInput.ofExternalId("N42")`
- `AddressInput.ofXY(x, y)`
- `AddressInput.ofLatLon(lat, lon)`

This allows:

- external-id requests
- coordinate-based requests
- mixed-mode requests when explicitly enabled

## Execution Contracts

Current execution modes:

- `RoutingAlgorithm.DIJKSTRA`
- `RoutingAlgorithm.A_STAR`

Current heuristic modes:

- `HeuristicType.NONE`
- `HeuristicType.EUCLIDEAN`
- `HeuristicType.SPHERICAL`
- `HeuristicType.LANDMARK`

Current startup execution binding is controlled through `ExecutionRuntimeConfig`, for example:

- `ExecutionRuntimeConfig.dijkstra()`
- `ExecutionRuntimeConfig.aStar(HeuristicType.EUCLIDEAN)`

## What v11 Adds

v11 is the offline learning layer.

Its job is to improve TARO's compiled temporal model before the runtime ever sees it.

Main ideas:

- build temporal sequences from telemetry
- train context-aware refinement models
- refine edge profile behavior deterministically
- keep runtime contracts unchanged
- preserve FIFO, parity, determinism, and lineage

Default production posture in v11:

- profile refinement first
- structural mutation constrained and optional

## What v12 Adds

v12 is the future-aware serving layer.

It introduces:

- `ScenarioBundleResolver`
- `FutureRouteEvaluator`
- `FutureRouteAggregator`
- `EphemeralRouteResultStore`
- `FutureRouteResultSet`

And three user-facing route products:

- **Expected ETA route**
  - best average ETA across plausible futures
- **Robust / P90 route**
  - best reliability-oriented route under tail risk
- **Top-K scenario routes with confidence**
  - materially distinct alternatives with uncertainty-aware explanation

It also introduces:

- `resultSetId`
- temporary result retention
- frontend follow-up reads for summary and detail

## What v13 Adds

v13 is the topology-evolution and operational-failure layer.

It introduces:

- `TopologyVersion`
- `StructuralChangeSet`
- `FailureQuarantine`
- `TopologyReloadCoordinator`
- `ReloadCompatibilityPolicy`

Its core rule is:

- transient failures are handled fast through quarantine/suppression
- permanent topology changes are handled through batched rebuild + atomic reload

Examples:

- edge/server failure now -> soft-disable immediately
- persistent node removal -> rebuild next topology snapshot and reload
- new edge/node introduced infrequently -> batch into structural build and reload

## Interface Contracts: Current vs Planned

### Implemented Now

- `RouterService#route(RouteRequest)`
- `RouterService#matrix(MatrixRequest)`
- `CostEngine`-driven time-dependent edge evaluation
- `LiveOverlay` runtime override layer
- `SpatialRuntime` nearest-node lookup
- startup-bound execution/trait runtime selection

### Planned Next

Operational/planned contracts already reflected in the docs:

- `ModelLoaderService`
  - `load(path) -> ModelHandle`
  - `reload(path) -> ReloadReport`
- planned API v1 endpoints:
  - `POST /api/v1/route`
  - `POST /api/v1/matrix`
  - `POST /api/v1/engine/live`
  - `POST /api/v1/telemetry`
  - `POST /api/v1/admin/reload`
  - `GET /api/v1/health`
  - `GET /api/v1/metrics`

### Important Separation

The README now intentionally separates:

- **implemented in-process Java contracts**
- **planned v11-v13 architecture contracts**

So readers can tell what exists today versus what the roadmap is building toward.

## Example Usage

### External-ID Route

```java
RouterService router = /* build RouteCore with graph/profile/cost/id-mapper and runtime configs */;

RouteResponse route = router.route(
        RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N42")
                .departureTicks(1_706_171_400L)
                .build()
);
```

### Typed Coordinate Route

```java
RouteResponse typedRoute = router.route(
        RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(12.34, 56.78))
                .targetAddress(AddressInput.ofXY(13.02, 57.11))
                .maxSnapDistance(250.0)
                .departureTicks(1_706_171_400L)
                .build()
);
```

### Matrix Query

```java
MatrixResponse matrix = router.matrix(
        MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N10")
                .targetExternalId("N11")
                .departureTicks(1_706_171_400L)
                .build()
);
```

## Build, Test, and Docs

### Prerequisites

- Java 21
- Maven 3.9+
- Python 3.11+

### Bootstrap

```bash
./scripts/check_env.sh
./scripts/bootstrap_env.sh
```

### Build

```bash
mvn clean package
```

### Run Tests

```bash
./scripts/run_java_tests.sh
./scripts/run_python_tests.sh
```

### Regenerate FlatBuffers Bindings

```bash
./scripts/gen_flatbuffers.sh
```

### Render Markdown Architecture Docs to PDF

```bash
.venv/bin/python scripts/render_md_to_pdf.py docs/taro_v13_architecture_plan.md docs/taro_v13_architecture_plan.pdf
```

## Documentation Map

### Current architecture direction

- `docs/taro_v13_architecture_plan.md`
- `docs/taro_v13_architecture_plan.pdf`

### Future-aware serving

- `docs/taro_v12_architecture_plan.md`
- `docs/taro_v12_architecture_plan.pdf`

### Offline learning foundation

- `docs/taro_v11_architecture_plan.md`
- `docs/taro_v11_implementation_guide.md`
- `docs/taro_v11_single_source_of_truth.md`
- `docs/taro_v11_ssot_stagewise_breakdown.md`

### Research companions

- `ResearchData/taro_v11.md`
- `ResearchData/taro_v11_impl_guide.md`
- `ResearchData/taro_v11_learning_pathway.md`

### Runtime deep dives

- `docs/stage7_live_overlay_impl.md`
- `docs/trait_runtime_lock_audit_report.md`

## Repo Layout

- `src/main/java/org/Aayush/routing/core/`
  - public routing API, requests/responses, planners, `RouteCore`
- `src/main/java/org/Aayush/routing/graph/`
  - graph topology and turn-cost structures
- `src/main/java/org/Aayush/routing/cost/`
  - time-dependent cost computation
- `src/main/java/org/Aayush/routing/overlay/`
  - live edge overrides and blocking
- `src/main/java/org/Aayush/routing/heuristic/`
  - admissible heuristics and landmark preprocessing
- `src/main/java/org/Aayush/routing/spatial/`
  - nearest-node lookup over serialized spatial index
- `src/main/java/org/Aayush/routing/traits/`
  - addressing, temporal, transition, and bundle/runtime binding
- `src/main/python/`
  - builder and Python-side utilities
- `src/test/java/`
  - correctness, parity, determinism, stress, and perf suites
- `docs/`
  - implementation notes and architecture plans
- `ResearchData/`
  - research-track architecture and pathway docs

## Contribution Guardrails

- Treat generated FlatBuffers code as generated-only.
- Keep business logic in `routing/*`, not in framework wrappers.
- Preserve deterministic behavior for fixed model + snapshot queries.
- When documenting or planning, distinguish clearly between implemented behavior and proposed roadmap behavior.
- For stakeholder-facing architecture docs, keep the matching `.md` and `.pdf` in sync.

## License

MIT. See `LICENSE`.
