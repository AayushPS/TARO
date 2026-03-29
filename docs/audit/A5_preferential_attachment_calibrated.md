Audit         : A5 — Preferential Attachment Does Not Become Uncalibrated Degree Bias
Date          : 2026-03-29
Verdict       : PARTIAL

Evidence:
- `src/test/java/org/Aayush/routing/future/DegreeAwareScenarioPriorTest.java` verifies the no-evidence baseline case:
  - arterial deviation stays within `±5 pp`
  - arterial deviation does not exceed low-degree deviation by more than `3 pp`
- `src/test/java/org/Aayush/routing/future/BundleProbabilityCalibrationTest.java` verifies that a sparse top-K bundle still exposes a low-degree route when arterial evidence is present.
- The Python E4 calibration layer keeps the uplift bounded:
  - `src/main/python/learning/calibration/contracts.py` enforces `preferential_attachment_range <= maximum_prior_adjustment`
  - `src/main/python/tests/learning/test_forecast_calibration.py` proves the uplift disappears without evidence
  - `src/main/python/tests/learning/test_forecast_calibration.py` also proves a negative forecast shift calibrates below the historical baseline instead of carrying a residual positive uplift
  - `src/main/python/learning/calibration/selector.py` only applies preferential-attachment uplift on positive evidence-aligned movement, so degree bias cannot survive as a one-way upward adjustment once the learned signal turns favorable vs unfavorable
  - `src/main/python/tests/learning/test_confidence_gate_selection.py` proves rejected priors fall back to the historical baseline instead of zero

Posture:
- Preferential attachment is intentionally present, but only as a bounded, evidence-responsive prior-shaping signal. Because the useful signal is combined with explicit anti-bias guards, the current TARO posture is `PARTIAL`.
