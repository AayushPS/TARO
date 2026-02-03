# TARO (Time-Aware Routing Orchestrator)

TARO is a time-dependent routing engine that treats travel cost as a function of entry time rather than a static weight. It provides a high-performance, data-driven “physics engine” for movement. Clients bring their own map and time-series data; TARO compiles it into a `.taro` model and serves time-aware routing for single-route and matrix queries.

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
mvn exec:java -Dexec.mainClass=org.Aayush.Main
```

## API Endpoints (Planned)
- `GET /route`: Point-to-point routing (A*).
- `POST /matrix`: One-to-many matrix routing (Dijkstra).
- `POST /engine/live`: Live traffic injection / overrides.

## Testing
### Java
```bash
mvn test
```

### Python
```bash
python -m pytest
```

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
- `src/main/java/`: Java runtime implementation
- `src/main/python/`: Python utilities and builder components
- `src/main/resources/flatbuffers/`: FlatBuffers schemas/tests
- `ResearchData/`: Reference specs and architecture docs (ignored in Git)

## License
MIT. See `LICENSE`.
