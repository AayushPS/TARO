Protocol Step: V6 — Recency
Date: 2026-03-28
Commands:
- `mvn test -Dtest=QuarantineRecencyOverrideTest,ReloadTemporalContinuityContractTest,FutureRouteServiceTest -q`
- `.venv/bin/python -m pytest src/main/python/tests/learning/test_temporal_attribute_probe.py -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.2.3 reports that the studied models show essentially no probability separation by how recently an edge was observed.

Observed test evidence:
- `QuarantineRecencyOverrideTest#testFreshObservationRaisesNearHorizonIncidentPersistenceProbability` asserts that a fresh quarantine produces higher `incident_persists` probability and freshness weight than a stale quarantine for the same corridor family.
- `QuarantineRecencyOverrideTest#testFreshQuarantineReroutesAwayFromHistoricalShortcut` asserts that the served future route changes from the historically fastest branch to the quarantined-safe branch when fresh evidence is present.
- `QuarantineRecencyOverrideTest#testFreshnessWindowBoundaryOrderingIsExplicit` names the shipped `45 minute` freshness window and verifies just-inside versus just-outside boundary ordering on both probability and freshness weight.
- `ReloadTemporalContinuityContractTest#testReloadPreservesScenarioPriorContinuityForFreshQuarantine` proves that reload preserves the fresh quarantine's `observedAtTicks`, `freshnessWeight`, and served `incident_persists` probability rather than silently flattening the prior.
- `test_temporal_attribute_probe.py#test_recency_clusters_raise_e4_prior_mass_for_incident_and_worsening_surfaces` proves the builder path publishes at least a `0.10` calibrated-prior gap for both `INCIDENT_PERSISTS` and `CONGESTION_WORSENING` surfaces when two corridors have identical historical bucket frequencies but only one has a recent evidence cluster.

Explicit threshold evidence (`10 pp` uplift requirement):
- `test_recency_clusters_raise_e4_prior_mass_for_incident_and_worsening_surfaces` asserts:
  - `recent_incident.calibrated_prior_probability - stale_incident.calibrated_prior_probability >= 0.10`
  - `recent_worsening.calibrated_prior_probability - stale_worsening.calibrated_prior_probability >= 0.10`

Protocol coverage summary:
- Quarantine path: covered, including served route output and explicit freshness-window boundary.
- Builder path: covered for both incident-persisting and congestion-worsening calibration surfaces with the required `10 pp` threshold.
- Reload path: covered at the scenario-prior audit level.

Verdict: PASS
