Audit         : A4 — Periodicity Survives the E2→E3→Serving Pipeline
Date          : 2026-03-29
Verdict       : PARTIAL

Evidence:
- E2 publishes the periodicity feature explicitly in the canonical sequence artifact schema as `periodicity_bucket_count` (`src/main/python/learning/datasets/builder.py`).
- `src/main/python/tests/learning/test_temporal_feature_surface.py` now verifies three distinct E2 periodicity classes:
  - period-2 corridor → bucket count `2`
  - persistent corridor → bucket count `4`
  - random corridor → bucket count `1`
- `src/main/python/tests/learning/test_temporal_attribute_probe.py` verifies that the periodic corridor retains a materially stronger learned periodicity signal than the flat corridor and that the calibrated prior spread remains non-flat across rush/off-peak buckets.
- `src/test/java/org/Aayush/routing/future/TemporalFidelityContractTest.java` carries the contract into serving:
  - the `07:30` request yields a worse ETA and wider `P90` than the `11:30` request on the same corridor
  - the route itself changes between rush-hour and off-peak evaluations
- `src/main/python/learning/calibration/selector.py` now preserves the sign of the forecast shift at E4, so off-peak periodic buckets can calibrate downward instead of receiving the same positive uplift as rush buckets.
- `src/main/python/tests/learning/test_ablation_determinism.py` now proves the periodicity feature changes the calibrated E4 prior spread itself, not just the E3 forecast surface:
  - `recency`-only training keeps the periodic corridor spread narrower
  - `periodicity + recency` widens the signed rush/off-peak prior gap by more than `5 pp`
  - the flat corridor remains pinned to the neutral `0.50` baseline, showing the feature effect is not a generic prior inflation

Posture:
- Periodicity has a structural floor at E2 and demonstrable survival through E3, E4, and serving. The learned path is now materially exercised at calibration time rather than stopping at the forecast surface, but the final behavior still depends on learned artifacts as well as structural runtime rules. The overall posture remains `PARTIAL`, not purely `STRUCTURAL`.
