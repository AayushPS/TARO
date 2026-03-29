Protocol Step: V8 — Preferential Attachment
Date: 2026-03-28
Commands:
- `mvn test -Dtest=DegreeAwareScenarioPriorTest,BundleProbabilityCalibrationTest -q`
- `.venv/bin/python -m pytest src/main/python/tests/learning/test_forecast_calibration.py -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.3.2 reports that all evaluated models learn preferential attachment, which makes anti-bias calibration an explicit requirement for TARO rather than an assumption.

Observed test evidence:
- `DegreeAwareScenarioPriorTest#testEvidencePresentHighDegreeIncidentPriorOutranksLowDegreeAlternative` asserts that the high-degree corridor prior stays above `historical frequency - 0.05` and at least `0.10` above the low-degree alternative under recent evidence.
- `DegreeAwareScenarioPriorTest#testNoRecentEvidenceReturnsTowardBaseProbabilityWithoutExcessArterialBias` asserts that no-evidence deviation remains within `0.05` and that arterial deviation does not exceed low-degree deviation by more than `0.03`.
- `BundleProbabilityCalibrationTest` still verifies normalized bundle probability mass and structural audit metadata for both recurring and quarantine-driven bundles.
- `BundleProbabilityCalibrationTest#testSparseTopKServingKeepsLowDegreeAlternativeReachable` now verifies the sparse evidence-present case requested by the protocol: the materialized scenario bundle retains two scenarios and the scenario results contain both the arterial route family `[N0, N1, N2, N5]` and the low-degree alternative `[N0, N3, N4, N5]` instead of collapsing serving to arterials only.
- `test_forecast_calibration.py` verifies preferential-attachment adjustment appears when evidence is present and disappears when evidence is insufficient.

Verdict: PASS
