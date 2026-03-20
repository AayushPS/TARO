# TARO v12 Architecture Plan: Scenario-Aware Future Routing and Ephemeral Result Serving

Status: Proposed  
Date: 2026-03-20  
Companion: `docs/taro_v11_architecture_plan.md`  
Scope: Online future-aware serving layer on top of v11 offline learning outputs

## 1. Objective

v12 extends TARO from "better deterministic cost surfaces" in v11 to true future-aware product behavior at query time.

The system must support three user-facing route products from the same scenario evaluation run:

- **Expected ETA route**: best average travel time across plausible future traffic scenarios.
- **Robust/P90 route**: best reliability-oriented route under tail-risk traffic conditions.
- **Top-K scenario routes with confidence**: a compact set of materially distinct alternatives with confidence-aware explanation.

v12 also introduces an **ephemeral result-serving layer** so the backend can finish the full multi-scenario calculation first, retain the results temporarily, and let the frontend query summaries and details afterward.

## 2. What Changes Over v11

v11 focus:
- offline learning to refine profiles before serialization
- deterministic runtime over one compiled cost surface plus live overlay snapshot
- no explicit product contract for uncertainty-aware routing outcomes

v12 additions:
- scenario bundle generation for future traffic horizons
- multi-scenario route evaluation at request time
- aggregation policies for average, robust, and alternative-route products
- temporary result storage keyed by a result-set id for frontend retrieval
- confidence-aware response semantics instead of a single hidden future assumption

v12 does **not** replace v11. It treats v11 as the forecast-quality foundation and adds the serving behavior needed to expose uncertainty honestly.

## 3. Non-Negotiable Contracts (Carried Forward)

1. Runtime route search remains deterministic for a fixed graph, scenario bundle, and overlay snapshot.
2. Runtime does not execute stochastic training logic in the hot planner loop.
3. Every scenario-specific cost surface must preserve FIFO safety.
4. Existing path optimality guarantees remain valid within each deterministic scenario.
5. A* admissibility and A*/Dijkstra parity must not regress per scenario.
6. Expired temporary results must be evictable without affecting core routing correctness.

## 4. v12 Design Principle

Future-aware routing is not one algorithmic mode. It is:

1. generating multiple plausible deterministic futures,
2. routing against each future,
3. aggregating those outcomes according to the requested product objective,
4. exposing the results with transparent confidence semantics.

That means the main architectural gap is not "replace the router." The gap is "add scenario modeling, aggregation, and retained result delivery around the router."

## 5. Four-Plane Architecture

## 5.1 Plane A: Offline Learning Builder (v11 Foundation)

Unchanged core role from v11:
- learn better baseline temporal profiles
- calibrate and export deterministic model artifacts
- validate FIFO and parity before model publication

Outputs used by v12:
- refined per-edge temporal profiles
- confidence and lineage metadata
- optional event/context features for scenario generation

## 5.2 Plane B: Forecast and Scenario Materialization

New in v12:
- combine refined historical profiles with current conditions
- materialize a bounded set of future traffic scenarios over a query horizon
- attach probability or confidence mass to each scenario

Inputs may include:
- v11 refined temporal profiles
- live overlay / incident state
- recent telemetry
- optional weather/event signals

Output:
- immutable `ScenarioBundle` for one routing job

Each scenario is deterministic once materialized. The uncertainty lives in the bundle selection, not inside the planner expansion loop.

## 5.3 Plane C: Multi-Scenario Runtime Evaluation

New in v12:
- execute route search against each scenario in the bundle
- compute route metrics per scenario
- aggregate route quality according to one or more product objectives

Required product layers:
- expected ETA route
- robust/P90 route
- top-K scenario routes with confidence

The runtime may reuse the existing deterministic route engine repeatedly rather than inventing a separate probabilistic pathfinder.

## 5.4 Plane D: Ephemeral Result Serving

New in v12:
- store computed scenario results temporarily after evaluation
- return a `resultSetId` to the calling client
- allow frontend follow-up reads for summary, alternatives, and scenario details

This plane decouples "expensive multi-scenario computation" from "interactive frontend inspection."

## 6. Scenario Bundle Contract

Each `ScenarioBundle` should contain:

- `scenarioBundleId`
- `generatedAt`
- `validUntil`
- query horizon metadata
- ordered scenario list
- per-scenario probability or confidence mass
- deterministic cost-adjustment references for each scenario
- lineage back to model version and live snapshot

Each scenario should contain:

- `scenarioId`
- `probability`
- `label` such as `baseline`, `clearing_fast`, `incident_persists`, `tail_heavy`
- time-windowed edge adjustment source
- optional explanation tags used by product/UI copy

Bundle requirements:
- probabilities sum to `1.0` within tolerance
- scenario count is bounded
- per-scenario adjustments are immutable during one routing job
- bundle identity is stable for auditability and caching

## 7. Product Layers

## 7.1 Expected ETA Route

Objective:
- minimize expected travel time across the scenario bundle

Definition:

`expected_cost(route) = sum over scenarios of p(s) * travel_time(route, s)`

Use when:
- average outcome matters most
- forecast confidence is moderate to high
- user wants the default "fastest" answer

Product contract:
- expose selected route
- expose expected ETA
- expose spread indicators such as min/median/max or standard confidence interval

## 7.2 Robust / P90 Route

Objective:
- minimize high-percentile travel time, typically P90

Definition:
- for each candidate route, derive travel-time distribution across scenarios
- choose the route with the lowest 90th percentile ETA, or another configured robust metric

Use when:
- the user values reliability over raw average speed
- lateness cost is asymmetric
- traffic tails matter more than central tendency

Product contract:
- expose selected route
- expose P50 and P90 ETA
- expose a "reliability-first" explanation

## 7.3 Top-K Scenario Routes With Confidence

Objective:
- return a small set of materially distinct good routes that cover the main plausible futures

Definition:
- evaluate candidates across scenarios
- deduplicate near-identical paths
- rank distinct routes by a configurable utility policy
- attach confidence semantics such as:
  - probability route is optimal
  - probability route is within X minutes of best
  - ETA band confidence

Use when:
- uncertainty is high
- the UI wants alternatives
- operators or power users need transparency

Product contract:
- expose up to `K` distinct routes
- expose confidence, ETA band, and dominant-scenario explanation per route

## 8. Query Lifecycle

v12 request flow:

1. Receive future-aware routing request.
2. Resolve current model version and live-state snapshot.
3. Materialize a bounded `ScenarioBundle` for the request horizon.
4. Execute deterministic route search for each scenario.
5. Aggregate route outcomes into the three product layers.
6. Persist full bundle results into an ephemeral result store.
7. Return lightweight job/result metadata to caller.
8. Let frontend query summaries and details by `resultSetId` until expiry.

This supports the product direction of "calculate first, inspect later" without forcing the frontend to wait on repeated recomputation.

## 9. Ephemeral Result Store

The result store is a temporary serving cache, not a permanent source of truth.

Required properties:
- TTL-based retention
- bounded memory or disk usage
- explicit eviction policy
- stable `resultSetId`
- caller/session scoping rules
- optional compression for geometry-heavy alternatives

Each retained result set should include:
- original normalized request
- scenario bundle metadata
- expected-route summary
- robust-route summary
- top-K alternative summaries
- per-scenario route metrics
- explanation metadata for UI
- expiry timestamp

Recommended default posture:
- retain results for a short product-driven TTL such as 5 to 30 minutes
- keep full details only while hot
- store enough metadata to allow partial summary responses after compaction if needed

## 10. Serving Contracts

Planned internal service concepts:

- `ScenarioBundleResolver`
- `FutureRouteEvaluator`
- `FutureRouteAggregator`
- `EphemeralRouteResultStore`
- `FutureRouteResultSet`

Planned request concepts:

- requested horizon
- risk mode or objective preferences
- top-K alternative budget
- result retention preference

Planned response concepts:

- `resultSetId`
- computation status
- expiry time
- summary of all three product layers

Follow-up frontend reads may expose:

- summary endpoint
- expected-route detail endpoint
- robust-route detail endpoint
- alternatives list endpoint
- per-scenario diagnostics endpoint

## 11. Data Fusion Model

v12 cost prediction should be interpreted as:

`future_cost(edge, t, scenario) = base_cost * refined_profile(t) * forecast_adjustment(edge, t, scenario) * live_or_incident_adjustment(edge, t, scenario) + turn_cost`

Interpretation:
- v11 gives a better baseline temporal shape
- v12 scenario generation adds future conditional adjustments
- live conditions influence near-horizon scenarios rather than replacing the entire future with one flat override

This is the critical serving evolution beyond the current single-expiry live overlay model.

## 12. Distinctness and Confidence Rules for Top-K

Top-K output must avoid low-value duplicates.

Required controls:
- path-overlap thresholding
- ETA similarity thresholding
- max alternative count
- confidence floor

Recommended semantics:
- do not return routes that are visually or operationally equivalent
- prefer fewer meaningful alternatives over exhaustive near-duplicates
- expose why an alternative exists, for example:
  - "best if congestion clears"
  - "best if incident persists"
  - "lowest tail-risk option"

## 13. Evaluation Criteria

Quality:
- expected ETA error
- P90 ETA calibration
- route regret under held-out futures
- alternative usefulness and distinctness

Correctness:
- zero FIFO violations in accepted scenario adjustments
- zero parity regressions per scenario
- deterministic replay under same scenario bundle id

Systems:
- bounded scenario-materialization latency
- bounded total evaluation cost
- bounded ephemeral-store memory footprint
- predictable expiry and eviction behavior

Product:
- frontend can retrieve all three layers without recomputation during TTL
- alternatives are understandable and non-duplicative
- confidence language remains stable and auditable

## 14. Key Risks and Mitigations

1. Scenario explosion  
   Mitigation: cap scenario count, merge similar futures, use bounded candidate route sets.
2. Misleading confidence language  
   Mitigation: standardize confidence semantics and calibrate them offline.
3. Temporary-store bloat  
   Mitigation: TTLs, quotas, compression, and route-detail compaction.
4. Inconsistent repeated answers  
   Mitigation: bind requests to a concrete scenario bundle id and live snapshot.
5. Poor alternative quality due to duplicate paths  
   Mitigation: enforce route distinctness rules before storing results.

## 15. Recommended v12 Rollout

1. Implement scenario bundle contract and materialization path.
2. Ship expected ETA route first as the default future-aware answer.
3. Add robust/P90 aggregation next for reliability-focused mode.
4. Add top-K scenario routes with distinctness and confidence gates.
5. Introduce ephemeral result serving and frontend follow-up retrieval.
6. Add richer diagnostics only after confidence semantics are stable.

## 16. Summary

v11 improves the quality of TARO's deterministic temporal model.

v12 turns that improved model into a true future-aware product by adding:
- multiple plausible future traffic scenarios
- three aggregation layers for different user needs
- temporary result retention for frontend retrieval

This keeps the router core trustworthy, keeps learning mostly out of the hot path, and exposes uncertainty as a first-class product capability instead of hiding it behind one guessed future.
