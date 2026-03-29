Protocol Step: V4 — Persistence
Date: 2026-03-28
Commands:
- `mvn test -Dtest=PersistentBaselineProfileTest,ProfileFifoRepairGateTest,CostEngineTest -q`
- `.venv/bin/python -m pytest src/main/python/tests/learning/test_temporal_attribute_probe.py -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.2.1 frames persistence as the ability to reproduce a fixed snapshot across timesteps and keep persistent edges separated from non-edges.

Observed test evidence:
- `PersistentBaselineProfileTest` verifies constant and noisy persistent profiles survive compile/load without flattening and remain directionally distinct.
- `ProfileFifoRepairGateTest` verifies FIFO-invalid persistent edge representations are rejected at snapshot build time.
- `test_temporal_attribute_probe.py` still marks the `C-PERSIST` probe case as `STRONG`.
- `test_temporal_attribute_probe.py#test_persistent_corridor_remains_distinct_from_same_mean_volatile_corridor_through_e4` now proves a persistent corridor and a same-mean volatile corridor remain separated through `E2 -> E3 -> E4`: the persistent representation keeps a materially higher `persistence_signal`, its high buckets stay above baseline in the learned forecast surface, and its published scenario-prior rows for buckets `8/9/10` remain `>= 0.80` and at least `0.20` above the volatile corridor.

Protocol closure evidence:
- Constant/high persistent profiles still survive FIFO and compile/load on the Java path.
- Same-mean persistent vs volatile corridors are now separated at E3 and remain separated in published E4 priors.

Verdict: PASS
