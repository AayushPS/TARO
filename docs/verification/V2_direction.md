Protocol Step: V2 — Direction
Date: 2026-03-28
Command: `mvn test -Dtest=DirectedProfileDivergenceTest,AsymmetricCorridorReloadTest,DirectionalityAsymmetryTest -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.1.2 reports that most models assign nearly symmetric probabilities to `(u,v)` and `(v,u)`, with differences below `0.02` for roughly half of positive edges.

Observed test evidence:
- `DirectedProfileDivergenceTest#testCompiledOpposingProfilesRemainDistinct` asserts distinct compiled profile IDs for `E01` and `E10`, then verifies served forward and reverse winners diverge: forward uses `N0 -> N2 -> N1`, reverse uses `N1 -> N0`.
- `DirectedProfileDivergenceTest#testServedDirectionalCostsRetainOpposingProfileRatio` adds the protocol-strength numeric check: the served forward cost is `40.0`, the reverse cost is `10.0`, and the served cost ratio remains `4.0`, matching the opposing profile ratio instead of collapsing toward symmetry.
- `AsymmetricCorridorReloadTest#testAsymmetricCorridorSurvivesReloadWhenSubjectStillExists` verifies the asymmetric forward/reverse winners survive a publication+reload cycle.
- `AsymmetricCorridorReloadTest#testRemovedAsymmetricSubjectDropsCleanlyInsteadOfNormalizing` verifies that removing the reverse subject yields `N1 -> N2 -> N0` rather than a silent symmetric fallback.
- `DirectionalityAsymmetryTest` covers both one-way and turn-sensitive asymmetry through `FutureRouteService` output.
- `TemporalFidelityContractTest#testDirectionalAsymmetrySurvivesFutureServing` remains the required full-pipeline cross-phase contract outside the exact V2 command.

Full-pipeline direction test:
- Present. Direction is exercised beyond `CostEngine` level and survives into served route selection and reload behavior, with an explicit served-cost ratio assertion in the named V2 suite.

Verdict: PASS
