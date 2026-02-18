# TARO (Time-Aware Routing Orchestrator)

TARO is a time-dependent routing engine that treats travel cost as a function of entry time rather than a static weight. It provides a high-performance, data-driven “physics engine” for movement. Clients bring their own map and time-series data; TARO compiles it into a `.taro` model and serves time-aware routing for single-route and matrix queries.

## Current Runtime Status
- `RouteCore` facade is implemented for point-to-point and matrix routing.
- Algorithms: `DIJKSTRA` and `A_STAR`.
- Heuristics: `NONE`, `EUCLIDEAN`, `SPHERICAL`, and `LANDMARK` (with precomputed landmark data).
- Stage 7 live overlay (`LiveOverlay`/`LiveUpdate`) is implemented and integrated into cost composition.
- Matrix execution uses the native one-to-many planner for `DIJKSTRA + NONE`; `A_STAR` matrix requests remain on the pairwise compatibility path (`TemporaryMatrixPlanner`).

## Architecture Summary
TARO follows a dual-runtime pipeline:
- **Python Builder (Compiler)**: Offline ETL, validation, compression, and FlatBuffers serialization into a `.taro` model.
- **Java Runtime (Engine)**: Memory-mapped model loading, time-dependent cost computation, and routing with A* or Dijkstra.
- **Telemetry Loop**: Actual vs predicted travel data can be collected and fed back into the builder to refine profiles.

## Data Inputs
TARO expects CSV inputs that define topology and time dynamics:
- `nodes.csv`: Node identifiers and coordinates.
- `edges.csv`: Directed edges with base weights and references to nodes.
- `profiles.csv`: Time buckets or profile groups (time-varying multipliers).
- `turn_costs.csv`: Optional turn penalties between edges.

## Build & Run
### Prerequisites
- Java 21
- Maven 3.9+
- Python 3.11 (for builder utilities/tests)

### Build (Java)
```bash
mvn clean package
```

### Run (Example)
```bash
mvn exec:java -Dexec.mainClass=org.Aayush.app.Main
```

## Programmatic Java API
Primary runtime contract:
- `org.Aayush.routing.core.RouterService#route(RouteRequest)`
- `org.Aayush.routing.core.RouterService#matrix(MatrixRequest)`

Example request usage:
```java
RouterService router = /* build RouteCore with graph/profile/cost/id-mapper */;

RouteResponse route = router.route(
        RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N42")
                .departureTicks(1_706_171_400L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.EUCLIDEAN)
                .build()
);

MatrixResponse matrix = router.matrix(
        MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N10")
                .targetExternalId("N11")
                .departureTicks(1_706_171_400L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build()
);
```

## HTTP API Status
- No production HTTP transport is wired yet in this repository.
- Current integration surface is the in-process Java API (`RouterService`/`RouteCore`).
- Planned endpoint shapes are still tracked in stage docs.

## Stage 7 Live Overlay (Implemented)
- Runtime classes: `org.Aayush.routing.overlay.LiveOverlay`, `org.Aayush.routing.overlay.LiveUpdate`
- State model: `MISSING`, `EXPIRED`, `BLOCKED`, `ACTIVE`
- Canonical penalty mapping: missing/expired `1.0`, blocked `INF`, active `1 / speed_factor`
- Hard capacity with configurable overflow policy and hybrid cleanup
- Detailed implementation notes: `docs/stage7_live_overlay_impl.md`

## v11 Offline Learning Planning (Proposed)
- Architecture plan: `docs/taro_v11_architecture_plan.md`
- Architecture PDF: `docs/taro_v11_architecture_plan.pdf`
- Implementation guide: `docs/taro_v11_implementation_guide.md`
- Implementation PDF: `docs/taro_v11_implementation_guide.pdf`
- Single source of truth (stage contracts/requirements/tests): `docs/taro_v11_single_source_of_truth.md`
- Single source of truth PDF: `docs/taro_v11_single_source_of_truth.pdf`
- Stage-wise SSOT breakup (28 stages): `docs/taro_v11_ssot_stagewise_breakdown.md`
- Stage-wise SSOT breakup PDF: `docs/taro_v11_ssot_stagewise_breakdown.pdf`
- Deep research architecture: `ResearchData/taro_v11.md`
- Deep research architecture PDF: `ResearchData/taro_v11.pdf`
- Research implementation pathway: `ResearchData/taro_v11_impl_guide.md`
- Research implementation pathway PDF: `ResearchData/taro_v11_impl_guide.pdf`
- Pathway stones map: `ResearchData/taro_v11_learning_pathway.md`
- Pathway stones PDF: `ResearchData/taro_v11_learning_pathway.pdf`

## Testing
### Java
```bash
mvn test
```

### Python
```bash
python -m pytest
```

## FlatBuffers Regeneration
Generated FlatBuffers code should not be edited manually. Use the helper script to regenerate
Java and Python bindings from `src/main/resources/flatbuffers/taro_model.fbs`:
```bash
./scripts/gen_flatbuffers.sh
```
This keeps the schema namespace as `taro.model` but rewrites Java packages to
`org.Aayush.serialization.flatbuffers.taro.model` so imports remain stable.

## Roadmap (High-Level Stages)
1. Core utilities: ID mapping, time utilities, FlatBuffers schema
2. Data structures: edge-based graph, turn costs, priority queue, overlays
3. Cost engine: time-dependent cost, profiles, live overrides
4. Algorithms: A* and Dijkstra (dual-mode)
5. Traits: addressing, temporal, transition strategies
6. Pipeline: ingestion, linting, compression, model build
7. Runtime: model loader, HTTP API
8. Production: telemetry + observability

## Repo Layout
- `src/main/java/org/Aayush/app/`: runtime entrypoints
- `src/main/java/org/Aayush/core/id/`: ID mapping contracts and implementations
- `src/main/java/org/Aayush/core/time/`: temporal utility functions
- `src/main/java/org/Aayush/routing/core/`: RouteCore facade, request/response models, planners
- `src/main/java/org/Aayush/routing/graph/`: graph topology and turn-cost structures
- `src/main/java/org/Aayush/routing/search/`: queue/state/visited search primitives
- `src/main/java/org/Aayush/routing/overlay/`: live runtime overlay for edge speed overrides
- `src/main/java/org/Aayush/routing/heuristic/`: heuristic providers, calibration, landmark preprocessing/store
- `src/main/java/org/Aayush/serialization/flatbuffers/`: generated FlatBuffers Java bindings
- `src/test/java/org/Aayush/...`: tests mirrored to the same package boundaries
- `src/main/python/`: Python utilities and builder components
- `src/main/resources/flatbuffers/`: FlatBuffers schema + Python generated bindings
- `scripts/`: developer automation scripts
- `docs/`: stage guide and implementation notes
- `ResearchData/`: reference specs and architecture docs

## Package Rules For New Code
- Put business/domain logic in `routing` packages, not `core`.
- Keep `core` framework-agnostic and reusable (no graph/search assumptions).
- Treat `serialization.flatbuffers` as generated-only code.
- Mirror tests to the package they validate so package-private contracts can be tested safely.

## Documentation & Lombok Conventions
- Add class-level and API-level Javadocs for new runtime classes, especially in `routing/core` and `routing/heuristic`.
- Prefer Lombok for low-value boilerplate (`@Getter`, `@Builder`, `@Value`, `@RequiredArgsConstructor`, `@UtilityClass`) when it keeps behavior explicit and testable.
- Keep generated FlatBuffers outputs untouched; apply documentation and Lombok conventions to hand-written runtime code only.

## License
MIT. See `LICENSE`.
