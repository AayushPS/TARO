# Stage 13 Delta Report (Stage12 vs Stage13 A*)

Date: 2026-02-14  
Workload: pinned-seed randomized A* queries on deterministic 18x18 grid fixture  
Source: `src/test/java/org/Aayush/routing/core/RouteCoreStressPerfTest.java`  
Test: `testStage13BaselineDeltaReportAgainstStage12`

## Command

```bash
mvn -q -Dtest=RouteCoreStressPerfTest test
```

## Measured Result (fixed seed)

- Query count: `320`
- Seed: `17013`
- Stage 12 baseline A*: `EdgeBasedRoutePlanner(true)`
- Stage 13 A*: `BidirectionalTdAStarPlanner` (RouteCore default)

| Metric | Stage 12 | Stage 13 |
| --- | ---: | ---: |
| Settled-states p50 | 557 | 311 |
| Settled-states p95 | 1153 | 495 |
| Heap peak delta (bytes) | 52,428,800 | 11,534,336 |
| Heap peak delta / query (bytes) | 163,840.00 | 36,044.80 |

## Gate Outcome

- A*/Dijkstra parity on the pinned set: `pass`
- Stage13 p95 settled-states <= Stage12 p95: `pass` (`495 <= 1153`)
- Memory telemetry recorded for both planners: `pass`

## Notes

- Heap telemetry is sampled from JVM used-heap (`totalMemory - freeMemory`) during the fixed workload run.
- The settled-state metric is deterministic under fixed model + query seed and is the primary non-regression signal.
