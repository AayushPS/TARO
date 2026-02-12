# Stage 12 Route Core + ALT Notes

Date: 2026-02-11  
Scope: Stage 12 implementation pass (ALT preprocessing + route-core orchestration wiring)

## Implemented

- Deterministic landmark preprocessing:
  - `LandmarkPreprocessor`
  - `LandmarkPreprocessorConfig`
  - `LandmarkArtifact`
  - `LandmarkSerializer`
  - `LandmarkStore`
- LANDMARK heuristic mode integration:
  - `HeuristicType.LANDMARK`
  - `LandmarkHeuristicProvider`
  - `HeuristicFactory` overload with landmark store validation
- Route-core facade and contracts:
  - `RouterService`, `RouteCore`
  - `RouteRequest`, `RouteResponse`
  - `MatrixRequest`, `MatrixResponse`
  - `RoutingAlgorithm`
  - Stage 12 reason-coded exceptions (`RouteCoreException`)

## Stage 14 Revisit Note

`NOTE(Stage 14 revisit): Replace Stage 12 temporary matrix execution path with the dedicated one-to-many Dijkstra matrix engine, and enforce A*/Dijkstra parity and performance gates before closing Stage 14.`

The note is currently embedded in:
- `src/main/java/org/Aayush/routing/core/TemporaryMatrixPlanner.java`
- `src/main/java/org/Aayush/routing/core/MatrixResponse.java` via `implementationNote`

