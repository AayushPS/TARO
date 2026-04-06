# Complex Route Scenario Datasets

This directory contains checked-in scenario and result datasets generated from
the real future-routing engine.

- `complex_route_scenarios.json` captures the scenario bundle inputs and the
  stage-option catalog for each case.
- `complex_route_results.json` captures the evaluated expected, robust, and
  alternative paths, plus scenario-level route outcomes and stage-option picks.

The current pack includes:

- `incident_split_corridor`
- `aggregate_compromise_triangle`
- `two_hour_branch_reversal`
- `multi_stage_branch_ladder`

Regenerate both artifacts with:

```bash
scripts/export_complex_scenario_datasets.sh
```
