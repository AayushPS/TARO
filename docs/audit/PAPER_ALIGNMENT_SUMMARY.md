| Paper Attribute      | Paper Finding (7 models) | TARO Mechanism                            | TARO Verdict | Evidence File |
|----------------------|--------------------------|-------------------------------------------|--------------|---------------|
| Temporal Granularity | Mixed (✓ for most)       | TemporalContextResolver + E2 feature surf. | PARTIAL      | `docs/verification/V1_granularity.md` |
| Direction            | All fail (✗)             | EdgeGraph + CostEngine (structural)        | STRUCTURAL   | `docs/audit/A1_direction_structural.md` |
| Density              | All fail (✗)             | Scenario bundle coverage floor             | STRUCTURAL   | `docs/audit/A2_density_floor.md` |
| Persistence          | Mixed (DyGFormer/TGAT ✓) | ProfileStore + E2/E3 persistence           | PARTIAL      | `docs/verification/V4_persistence.md` |
| Periodicity          | Mixed (GraphMixer/TCL ✓) | E2 periodicity feature + signed E4 path    | PARTIAL      | `docs/audit/A4_periodicity_pipeline.md` |
| Recency              | All fail (✗)             | Quarantine override (explicit) + E3 signal | STRUCTURAL   | `docs/audit/A3_recency_explicit.md` |
| Homophily            | Mixed                    | Bounded corridor-cluster uplift            | LEARNED      | `docs/verification/V7_homophily.md` |
| Preferential Attach. | All succeed (✓)          | E4 signed degree-aware calibration         | PARTIAL      | `docs/audit/A5_preferential_attachment_calibrated.md` |
