Pipeline    : E1 → E2 → E3 → E4 → E5
Date        : 2026-03-28

Stage Records:
  E1 → PASS — docs/training/PIPELINE_E1_record.md
  E2 → PASS — docs/training/PIPELINE_E2_record.md
  E3 → PASS — docs/training/PIPELINE_E3_record.md
  E4 → PASS — docs/training/PIPELINE_E4_record.md
  E5 → PASS — docs/training/PIPELINE_E5_record.md

Attribute Probe Results:
  Temporal Granularity : manifest temporal bounds preserved at `100..130` ticks with explicit lineage
  Recency-gap          : recent/stale ordering re-verified at `100 < 900`, `100 < 700`, and `50 < 700`
  Persistence-run      : consecutive run `4`, alternating-gap run `1`
  Periodicity          : period-2 bucket count `2`, persistent `4`, random `1`
  Direction            : centered cosine similarity `-0.994393`
  Density              : min centered representation norm `6.373730`
  Preferential Attach. : arterial uplift delta `0.026000`

Calibration Posture:
  Homophily uplift is bounded and evidence-responsive, degree-aware uplift remains capped by explicit calibration limits, and no accepted E4 prior saturated above `0.99` in the verified gate set.

Pack Published    : yes
Pack Artifact     : docs/training/e5_reproducibility_pack/reproducibility_pack.json
