# TARO Agent Guidance

This file gives project-level instructions to Codex and other coding agents working in this repository.

## Primary Rule

When answering architecture, roadmap, planning, or product-behavior questions for TARO, always read the latest project docs from disk first instead of relying on prior chat context.

## Canonical Document Order

Use these documents in this priority order when they are relevant:

1. `docs/taro_v13_architecture_plan.md`
   - Latest topology-evolution and failure-handling plan.
   - Treat this as the current top-level direction for infrequent node/edge add-drop handling, transient failure quarantine, and atomic reload behavior.
2. `docs/taro_v12_architecture_plan.md`
   - Latest future-aware serving plan.
   - Treat this as the current top-level architecture direction for scenario-aware routing, expected ETA / robust / top-K products, and ephemeral result retention for frontend querying.
3. `docs/taro_v11_architecture_plan.md`
   - Foundation for offline learning and compile-time refinement.
   - Use this as the baseline that v12 and v13 extend.
4. `docs/taro_v11_implementation_guide.md`
   - Practical implementation sequencing for the v11 learning foundation.
5. `docs/taro_v11_single_source_of_truth.md`
   - Contract-oriented reference when behavior, gates, or requirements need confirmation.
6. `docs/trait_runtime_lock_audit_report.md`
   - Runtime-locking and trait-binding context when working on startup/runtime immutability topics.

## Interpretation Rules

- Higher-version docs supersede lower-version planning docs when they conflict.
- v13 is the latest operational topology and failure-handling direction.
- v12 remains the latest future-aware product-serving direction underneath v13.
- v11 remains the offline learning foundation underneath v12/v13.
- Older stage docs are useful for implementation detail, but should not override v12/v11 architecture intent unless the user explicitly asks for historical stage behavior.

## Expected Agent Behavior

- If the user asks for the "latest plan", start with `docs/taro_v13_architecture_plan.md`.
- If the user asks how current work fits the roadmap, explain it relative to v13 first, then v12, then v11 if needed.
- If the user asks about future-aware routing behavior, include the three v12 product layers:
  - expected ETA route
  - robust/P90 route
  - top-K scenario routes with confidence
- If the user asks about structural changes or failures, include the v13 distinction between:
  - transient failure quarantine
  - batched structural rebuild + atomic reload
- If the user asks about frontend retrieval flow, include the v12 ephemeral result-serving model.
- If a response depends on project architecture, open the relevant doc(s) in this file before answering.

## Documentation Maintenance

- When creating a new top-level planning doc version, add it to this file and place it above older versions.
- When creating a markdown architecture/plan doc meant for stakeholders, also render the matching PDF when feasible using `scripts/render_md_to_pdf.py`.
