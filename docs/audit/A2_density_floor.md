Audit         : A2 — Density Floor is Enforced by Construction
Date          : 2026-03-28
Verdict       : STRUCTURAL

Evidence:
- `src/main/java/org/Aayush/routing/future/DefaultScenarioBundleResolver.java` now names the structural bundle floor explicitly as `MIN_SCENARIO_COVERAGE_FLOOR = 2`, exposed through `minimumScenarioCoverageFloor()`.
- `src/test/java/org/Aayush/routing/future/DefaultScenarioBundleResolverTest.java` verifies the floor directly in `testLowDegreeIncidentCorridorKeepsCoverageFloor`:
  - the resolver reports floor `2`
  - the materialized bundle size is `>= 2`
  - both `incident_persists` and `clearing_fast` remain present on the low-degree incident corridor
- `src/test/java/org/Aayush/routing/future/DensityCalibrationTest.java` proves the serving layer does not collapse to arterial-only output:
  - `testLowDegreeAggregateWinnerRemainsVisibleAndTopKDoesNotCollapse` keeps the low-degree compromise route visible
  - `testIncidentProneLowTrafficCorridorRetainsNonZeroIncidentMass` keeps incident mass above zero on a low-traffic corridor
- `src/test/java/org/Aayush/routing/future/BundleProbabilityCalibrationTest.java` proves a sparse top-K request still exposes a low-degree alternative under arterial evidence.

Config Posture:
- The scenario coverage floor is fixed by construction rather than user-configurable, so the “set it to `0` and fail” sub-check is not applicable in the current codebase.
