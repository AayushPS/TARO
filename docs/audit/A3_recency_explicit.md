Audit         : A3 — Recency is Explicit, Not Emergent
Date          : 2026-03-28
Verdict       : STRUCTURAL

Evidence:
- The near-horizon runtime parameter is `RecencyCalibrationConfig.defaults().freshnessHalfLife()`, with current default `Duration.ofMinutes(45)`.
- `src/test/java/org/Aayush/routing/topology/QuarantineRecencyOverrideTest.java` verifies the runtime mechanism at the boundary:
  - just-inside `45` minutes carries higher incident probability than just-outside
  - the boundary test preserves the exact inside/outside `observedAtTicks` values
  - fresh quarantine evidence reroutes served output away from the historical shortcut
- The runtime and builder recency paths are independent:
  - runtime path: `DefaultScenarioBundleResolver.freshnessWeight(...)` uses quarantine `observedAtTicks`
  - builder path: `src/main/python/learning/forecasting/trainer.py` uses `history_window_end_ticks - row.timestamp_ticks` for recency weighting
- Empty-quarantine / flat-recency behavior still yields meaningful output:
  - `DefaultScenarioBundleResolver` no-failure branch emits a `baseline` scenario with probability `1.0`
  - `TemporalFidelityContractTest.testPersistentArtifactSurvivesFutureServing` shows served future output remains reachable and meaningful without quarantine-triggered recency overrides.
