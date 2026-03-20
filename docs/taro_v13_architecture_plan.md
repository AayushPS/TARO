# TARO v13 Architecture Plan: Topology Evolution, Failure Quarantine, and Atomic Reload

Status: Proposed  
Date: 2026-03-20  
Companion: `docs/taro_v12_architecture_plan.md`  
Scope: Infrequent topology additions/removals, transient failure handling, and reload-safe future-aware serving

## 1. Objective

v13 extends TARO from v12 future-aware scenario serving into a system that can safely handle:

- infrequent **topology additions** such as a new node or edge,
- infrequent **topology removals** such as a retired node or edge,
- sudden **runtime failures** such as a broken link, blocked corridor, or failed network/server node,
- and the interaction of all of the above with:
  - deterministic routing,
  - future-aware scenario evaluation,
  - ephemeral result retention,
  - and large-query perf/smoke/parity gates.

v13 does **not** introduce arbitrary in-place graph mutation inside the live query engine.

Instead, it formalizes a two-speed operating model:

1. **Fast soft-disable path** for transient failures.
2. **Batched structural rebuild + atomic reload path** for persistent topology changes.

## 2. What Changes Over v12

v12 focus:
- future-aware routing over a fixed topology snapshot,
- scenario bundle generation,
- expected ETA / robust / top-K product layers,
- temporary result serving for frontend follow-up retrieval.

v13 additions:
- explicit structural lifecycle for add/drop node and edge events,
- runtime failure quarantine contract for transient outages,
- topology-versioned reload semantics,
- compatibility rules for ephemeral result sets across reloads,
- validation and performance gates for topology changes on large route and matrix workloads.

## 3. Why v13 Is Needed

v12 assumes the model topology is fixed during a routing job, which is correct for deterministic serving.

But real systems still face structural change:

- roads open or close,
- network links are added,
- servers fail,
- devices disappear,
- maintenance windows temporarily disable parts of the graph,
- long-lived failures eventually become permanent topology edits.

If TARO only supports full offline recompilation with no intermediate failure posture, product behavior is too slow for operational incidents.

If TARO allows live graph mutation in the hot path, determinism, safety, and performance become much harder to protect.

v13 resolves that tension by separating **operational suppression** from **structural truth**.

## 4. Design Principle

Topology truth should remain immutable per active runtime snapshot.

Operational failures should be represented by deterministic overlays or quarantine layers until the next validated structural model is ready.

That means v13 distinguishes:

- **Soft structural suppression**: make an existing edge or node unusable now, without changing the base graph arrays.
- **Hard structural evolution**: publish a new validated graph snapshot and atomically swap runtimes.

## 5. Event Classes

## 5.1 Additive Structural Events

Examples:
- new road segment
- new logical network edge
- new node/device/server introduced into the topology

Handling:
- stage into builder inputs,
- regenerate dependent artifacts,
- validate,
- atomically reload as a new topology version.

## 5.2 Subtractive Structural Events

Examples:
- road permanently removed,
- link retired,
- server/node permanently decommissioned,
- edge removed due to policy or physical loss.

Handling:
- if the outage is immediate, soft-disable first,
- then rebuild and publish a topology version where the node/edge is absent.

## 5.3 Transient Failure Events

Examples:
- broken network/server node,
- temporary edge outage,
- emergency closure,
- incident-driven deactivation.

Handling:
- represent operationally at runtime without mutating the base graph snapshot,
- propagate failure posture into scenario generation and route evaluation,
- reconcile into a later model version only if the failure becomes persistent.

## 6. Two-Speed Structural Model

## 6.1 Fast Path: Failure Quarantine

Purpose:
- react quickly to broken nodes or edges.

Mechanism:
- use deterministic runtime suppression to block or penalize affected graph elements for the current snapshot lifetime.

For edge failure:
- existing Stage 7 `LiveOverlay` already supports hard block via `speedFactor == 0.0f`, which maps to infinite cost.

For node failure:
- v13 should treat a failed node as a derived block on all incident edges,
- ideally through a dedicated quarantine layer rather than manually fanning out ad hoc edge updates at each caller.

This path is operationally fast and avoids rebuilding the model immediately.

## 6.2 Slow Path: Structural Rebuild and Reload

Purpose:
- make topology additions/removals durable and canonical.

Mechanism:
- rebuild `.taro`,
- regenerate dependent artifacts,
- validate correctness and performance gates,
- atomically swap in the new runtime snapshot.

This path is appropriate because the user has stated these changes are **not frequent**.

## 7. v13 Runtime Additions

Planned internal concepts:

- `TopologyVersion`
- `StructuralChangeSet`
- `FailureQuarantine`
- `TopologyReloadCoordinator`
- `ReloadCompatibilityPolicy`

## 7.1 `FailureQuarantine`

Purpose:
- express temporary unavailability without editing the base graph.

Capabilities:
- block one edge
- block one node by suppressing its incident edges
- apply TTLs
- annotate reason and source
- bind to one snapshot for deterministic replay

Recommended scope:
- edge failures use existing overlay semantics where possible
- node failures use a normalized incident-edge suppression view

## 7.2 `StructuralChangeSet`

Purpose:
- define one batched topology update for builder/reload processing.

Possible fields:
- added nodes
- removed nodes
- added edges
- removed edges
- changed coordinates
- changed turn relationships
- changed profile assignments
- requested rollout policy

## 7.3 `TopologyVersion`

Purpose:
- make topology identity explicit across route jobs and retained result sets.

Minimum fields:
- model version
- topology version
- generation timestamp
- source data lineage hash
- change-set hash

## 8. Standard Handling for Add and Drop Operations

## 8.1 Add New Edge

If the edge connects existing nodes:

1. Add edge to source inputs.
2. Recompute edge arrays and dependent turn structures.
3. Recompute affected builder artifacts.
4. Revalidate FIFO/parity/perf.
5. Reload atomically.

Operational note:
- this is usually one of the cheaper structural changes,
- but it can still change branching factor and matrix search behavior significantly near busy hubs.

## 8.2 Add New Node and Edge

1. Add node to topology inputs and coordinates.
2. Add connecting edges and turn relationships.
3. Rebuild spatial artifacts.
4. Rebuild heuristic artifacts that depend on topology size and connectivity.
5. Revalidate and reload.

Operational note:
- more expensive than edge-only change,
- because node count, coordinates, nearest-node behavior, and heuristic stores all shift.

## 8.3 Drop Edge

For immediate outage:
- soft-block first via failure quarantine.

For persistent removal:
1. remove edge in next structural build,
2. rebuild dependent artifacts,
3. atomically reload.

## 8.4 Drop Node

For immediate outage:
- quarantine all incident edges so the node becomes unreachable.

For persistent removal:
1. remove node and incident edges in next structural build,
2. rebuild topology-derived artifacts,
3. reload atomically.

Operational note:
- node drop is the most sensitive case because it affects connectivity, spatial lookup, and remapping behavior.

## 9. Cost Model

v13 treats structural change cost as a **batch rebuild cost**, not a live single-record edit cost.

Main contributors:

1. **Graph/materialization cost**
   - rebuild and validate topology arrays.
2. **Turn/transition artifact cost**
   - edge additions/removals may change legal transitions.
3. **Spatial artifact cost**
   - node additions/removals require spatial index rebuild when coordinates are enabled.
4. **Heuristic artifact cost**
   - landmark or other topology-derived lower bounds must be regenerated.
5. **Validation cost**
   - FIFO, parity, determinism, stress/perf, and reload safety gates.

Because changes are infrequent, v13 explicitly prefers:
- batched updates,
- deterministic rebuild,
- atomic swap,
- rather than incremental mutation infrastructure.

## 10. Interaction with v12 Future-Aware Serving

v12 result sets are temporary and tied to the snapshot used during computation.

v13 requires each retained result set to store:
- `modelVersion`
- `topologyVersion`
- `scenarioBundleId`
- live/quarantine snapshot identity

This prevents stale results from being confused with current topology.

Recommended policy:
- results generated under an old topology remain readable until TTL expiry,
- but they must be labeled with their snapshot version,
- and new requests always bind to the newest active topology version.

Optional stricter policy:
- invalidate all retained results on topology reload if product consistency demands it.

## 11. Failure Quarantine and Scenario Generation

Future-aware serving must see current failures.

Therefore:
- failure quarantine becomes an input to scenario bundle materialization,
- not just a late cost-engine patch.

Examples:
- blocked edge now and likely reopened later -> scenario divergence by time horizon
- failed server node now -> all incident paths suppressed in near-term scenarios
- maintenance window -> deterministic future availability windows

This lets v12 scenarios stay compatible with operational reality while preserving determinism per scenario.

## 12. Non-Negotiable Contracts

1. No in-place mutation of active graph topology in the query hot path.
2. Failure quarantine must be deterministic for a fixed snapshot and TTL state.
3. Structural reload must be atomic with no partial visibility.
4. Old snapshot remains available until replacement is validated.
5. Topology add/drop changes must not bypass parity or performance gates.
6. Large-query route and matrix smoke tests remain release blockers after topology changes.

## 13. Perf, Smoke, and Correctness Gates

Every structural release candidate should pass:

- schema/topology validation
- turn/transition integrity validation
- spatial artifact validation when coordinates are enabled
- FIFO validation
- A* vs Dijkstra parity
- route perf smoke
- matrix perf smoke
- concurrency determinism
- reload success/failure smoke

High-value existing suites remain relevant:
- `RouteCoreStressPerfTest`
- `SystemIntegrationStressPerfTest`

v13 should add:
- topology add-edge reload smoke
- topology add-node reload smoke
- edge-drop quarantine + reload parity test
- node-failure quarantine test
- retained-result compatibility test across reload

## 14. Reload Semantics

v13 reload flow:

1. Build candidate model from base inputs plus accepted structural changes.
2. Materialize runtime stores off the active path.
3. Run validation gates.
4. If validation fails, keep current runtime.
5. If validation passes, atomically publish the new runtime.
6. Start serving new queries on the new topology version.
7. Handle old retained result sets according to compatibility policy.

This preserves the operational standard already implied by planned hot reload, while adding topology-aware result semantics.

## 15. Recommended v13 Rollout

1. Formalize `TopologyVersion` and result-set version binding.
2. Implement failure quarantine for edges first.
3. Add node-failure quarantine through incident-edge suppression.
4. Define `StructuralChangeSet` builder contracts.
5. Implement batched rebuild + atomic reload path.
6. Add reload compatibility behavior for retained v12 result sets.
7. Add topology-change smoke/perf/parity suites for large route and matrix workloads.

## 16. Summary

v12 makes TARO future-aware under one fixed topology snapshot.

v13 makes TARO operationally realistic by handling:
- infrequent topology additions,
- infrequent topology removals,
- sudden transient node/edge failures,
- and reload-safe coexistence with retained future-aware result sets.

The core rule stays the same:

- **temporary failures are quarantined,**
- **permanent structural truth is rebuilt and atomically reloaded.**

That gives TARO the right balance of safety, determinism, and operational responsiveness without forcing live graph mutation into the hot path.
