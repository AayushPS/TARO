Protocol Step: V3 — Density
Date: 2026-03-28
Command: `mvn test -Dtest=DensityCalibrationTest,FutureRouteObjectivePlannerTest,DefaultScenarioBundleResolverTest -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.1.3 concludes that models tend to miss true density and can collapse toward predicting no edges under heavier negative sampling.

Observed test evidence:
- `DensityCalibrationTest#testAggregateOnlyCompromiseRouteProducesDensityRescueReport` asserts that the aggregate compromise winner `N0 -> N3 -> N4` remains selectable, reports `HIGH_DENSITY`, and records one aggregate-added candidate.
- `FutureRouteObjectivePlannerTest#testAStarObjectivePlanningFindsCompromiseRoute` independently verifies that the planner selects the same compromise route under the aggregate objective.
- `DensityCalibrationTest#testLowDegreeAggregateWinnerRemainsVisibleAndTopKDoesNotCollapse` proves that the low-degree route family `N0 -> N3 -> N4` appears in the materialized scenario results, remains in the selected alternatives, and preserves the requested `topK=3` floor.
- `DefaultScenarioBundleResolverTest#testLowDegreeIncidentCorridorKeepsCoverageFloor` proves the shipped resolver keeps the two-scenario coverage floor (`incident_persists`, `clearing_fast`) for a low-degree incident corridor instead of pruning it away.
- `DensityCalibrationTest#testIncidentProneLowTrafficCorridorRetainsNonZeroIncidentMass` proves incident-prone low-traffic corridors keep non-zero incident prior mass and stay above the configured minimum incident floor.
- `DensityCalibrationTest#testDuplicateScenarioWinnerCollapseStaysLowDensity` and `#testLowDensityServingReport` still verify the collapse-detection side of the calibration report.

Protocol coverage summary:
- Low-traffic aggregate winner remains visible in scenario materialization: covered.
- Default resolver minimum-coverage floor: covered.
- Low-traffic incident prior does not collapse to zero: covered.
- Top-K lower bound: covered.

Verdict: PASS
