# Stage 14 Delta Report (Final Closure)

Date: 2026-02-14  
Status: Closed

## Scope Delivered

- Native one-to-many Dijkstra matrix planner is the default for `DIJKSTRA + NONE`.
- `A_STAR` matrix compatibility mode is retained via pairwise expansion.
- Deterministic matrix budget and numeric fail-fast wrapping is active in `RouteCore`.
- Stage 14 matrix stress/perf/correctness/completeness gates are implemented in `RouteCoreStressPerfTest`.
- Stage 14 primitive guardrails are implemented in `Stage14PrimitiveGuardrailTest`.

## Commands Run

```bash
mvn -q -Dtest=org.Aayush.routing.core.*Test test
mvn -q test
```

## Pinned Stage12-vs-Stage14 Metrics

Workload:
- Fixture: deterministic 18x18 grid.
- Query set: 52 pinned matrix requests (`seed=14105`), source count 4-8, target count 18-30.
- Algorithm: Dijkstra matrix mode.

Observed gate telemetry:
- `throughput_gain_x=9.592` (`stage12_ms=1178.80`, `stage14_ms=122.89`)
- `stage12_p95_settled_per_cell=698.0444`
- `stage14_p95_work_per_cell=65.4667`
- `p95_work_ratio=0.0938`
- `stage12_heap_peak_delta_per_cell_bytes=11761.0121`
- `stage14_heap_peak_delta_per_cell_bytes=3888.9495`
- `heap_ratio=0.3307`

## Hard Exit Gate Closure

- `H14-FUNC-01..04`: PASS
- `H14-CORR-01..03`: PASS
- `H14-COMPLETE-01..03`: PASS
- `H14-PERF-01`: PASS (`>=3.0x`, observed `9.592x`)
- `H14-PERF-02`: PASS (`<=0.70x`, observed `0.0938x`)
- `H14-PERF-03`: PASS (`<=0.60x`, observed `0.3307x`)
- `H14-STRESS-01..03`: PASS
- `H14-DOC-01..03`: PASS
