Audit         : A1 — Direction is Structural, Not Learned
Date          : 2026-03-28
Verdict       : STRUCTURAL

Evidence:
- `src/main/java/org/Aayush/routing/graph/EdgeGraph.java` stores directed topology as explicit `edgeTarget`, `edgeOrigin`, and per-edge `edgeProfileIds` buffers. There is no automatic reverse-edge materialization or graph symmetrization path in the runtime graph.
- `src/test/java/org/Aayush/routing/graph/EdgeGraphTest.java` now includes `testAsymmetricTraversalDoesNotInventReverseEdge`, which loads a one-way edge set and proves the reverse node has degree `0` and an empty iterator.
- `src/main/java/org/Aayush/routing/cost/CostEngine.java` resolves traversal behavior from `edgeGraph.getProfileId(edgeId)`, so forward and reverse edges can carry genuinely different temporal profiles when the graph encodes them that way.
- `src/test/java/org/Aayush/routing/topology/DirectedProfileDivergenceTest.java` verifies both path divergence and served-cost divergence:
  - forward route detours while reverse remains direct
  - served forward/reverse expected costs stay at `40.0` vs `10.0`
  - the retained directional ratio remains `4.0`

Inference:
- If directional profile separation were removed, `DirectedProfileDivergenceTest.testServedDirectionalCostsRetainOpposingProfileRatio` would fail its explicit `40.0`, `10.0`, and `4.0` assertions. This is an inference from the current assertions rather than a mutation test run.
