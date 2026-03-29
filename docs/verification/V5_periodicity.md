Protocol Step: V5 — Periodicity
Date: 2026-03-28
Commands:
- `mvn test -Dtest=TemporalFidelityContractTest,FutureRouteServiceTest -q`
- `.venv/bin/python -m pytest src/main/python/tests/learning/test_temporal_attribute_probe.py -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.2.2 evaluates periodicity by alternating snapshots and checking whether models distinguish odd and even temporal contexts.

Observed test evidence:
- `TemporalFidelityContractTest#testPeriodicArtifactSurvivesFutureServing` and `#testDefaultResolverCarriesPeriodicSignalIntoServing` show that periodic routing behavior can change served winners and scenario bundles.
- The default resolver test proves the periodic scenario is not activated before the configured peak window and does activate at the peak window.
- `TemporalFidelityContractTest#testRushHourRequestProducesWorseEtaAndP90ThanOffPeakForSameCorridor` now fixes all other inputs and compares the same weekday corridor at `07:30` versus `11:30`, asserting the rush-hour request serves `[N0, N2, N3]` instead of `[N0, N1, N3]` and that both expected cost and `P90` are worse at `07:30`.
- `test_temporal_attribute_probe.py#test_periodic_corridor_keeps_distinct_bucket_surfaces_and_non_flat_priors` now proves the learned periodic corridor keeps a materially larger periodicity signal than a flat-mean corridor, its bucket-conditioned E3 surface differs by more than `25.0` between rush and off-peak buckets, and E4 preserves a prior gap greater than `0.20` instead of averaging both buckets into the same prior.

Verdict: PASS
