# TARO v11 Learning Pathway Stones

Date: 2026-02-07  
Primary Reference: `ResearchData/taro_v11_impl_guide.md`

## 1. Pathway Objective

Provide a compact milestone map from idea to validated v11 release and research publication package.

## 2. Stone Dependency Flow

`S00 -> S01 -> S02 -> S03 -> S04 -> S05 -> S06 -> S07 -> S08 -> S09 -> S10 -> S11 -> S12 -> (S13 -> S14) -> S15 -> S16 -> S17 -> S18 -> S19 -> S20 -> S21 -> S22 -> S23`

Notes:
- `S13` and `S14` are optional if structure learning stays disabled in release scope.
- `S15` and `S16` remain mandatory even for profile-only mode.

## 3. Stone Registry

- `S00` Contract Freeze  
- `S01` Data Inventory  
- `S02` Time Alignment  
- `S03` Sequence Builder  
- `S04` Baseline Reproduction  
- `S05` Encoder MVP  
- `S06` Candidate Generator MVP  
- `S07` Deterministic Selector  
- `S08` FIFO Gate  
- `S09` Compile Integration  
- `S10` Routing Metrics Loop  
- `S11` Calibration Layer  
- `S12` Robustness Stress Suite  
- `S13` Structural Proposal Sandbox (optional)  
- `S14` Structure Safety Gates (optional)  
- `S15` Parity Gate Expansion  
- `S16` Runtime Non-Regression  
- `S17` Auditability Pack  
- `S18` Shadow Rollout  
- `S19` Controlled Rollout  
- `S20` Paper Dataset Freeze  
- `S21` Ablation Completion  
- `S22` Draft Claims Validation  
- `S23` Final v11 Gate

## 4. Exit Conditions for Path Completion

Path is complete only when all mandatory stones satisfy:
- deterministic export reproducibility
- zero accepted FIFO violations
- zero parity mismatch
- approved runtime non-regression
- frozen reproducibility package for research reporting

## 5. Recommended Cadence

1. Weekly checkpoint: `S00-S10` phase until first learned model is stable.  
2. Bi-weekly checkpoint: `S11-S16` with deeper validation.  
3. Release checkpoint: `S17-S23` with audit and rollout signoff.

## 6. Minimal Release Cut (If Time-Constrained)

Release with profile-only learning:
- Required: `S00-S12`, `S15-S17`
- Deferred: `S13-S14`, `S18-S23` (can continue as post-release research cycle)

