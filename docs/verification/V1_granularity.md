Protocol Step: V1 — Temporal Granularity
Date: 2026-03-28
Command: `mvn test -Dtest=TemporalGranularityCompetencyTest,TimeUtilsTest -q`
Result: PASS

Paper context checked:
- `ResearchData/2510.09416v1.pdf` Section 4.1.1 states that flattening timestamps "consistently harms performance to a severe degree" and treats this as evidence that fine-grained temporal information matters.

Observed test evidence:
- `TemporalGranularityCompetencyTest#testBindingPublishesGranularityContract` asserts an explicit drift budget of `1800` seconds and policy `REJECT_EXCESS_DRIFT`.
- `TemporalGranularityCompetencyTest#testRejectsBucketWidthThatExceedsDriftBudget` rejects an hourly bucket under a 15-minute drift budget and asserts the exception mentions the drift budget and `bucketSizeSeconds=3600`.
- `TemporalGranularityCompetencyTest#testSameRequestAtTwoGranularitiesStaysWithinPublishedDriftBudget` compares 30-minute and 60-minute bucket representatives and asserts the 60-minute representative remains within the published drift budget.
- `TemporalGranularityCompetencyTest#testFineGrainedAndDailyCoarsenedRequestsDivergeInServedFutureOutput` evaluates the same future-aware request at `2026-03-08T07:05:00Z` and a daily-coarsened representative `2026-03-08T12:00:00Z`, and asserts the served expected route changes from `[N0, N2, N3]` to `[N0, N1, N3]` with a higher fine-grained expected cost.
- `TemporalGranularityCompetencyTest#testTemporalContextResolverFlagsCollapsedTimestampSets` covers three levels explicitly: sub-hour (`1800` seconds) preserves multiple resolved buckets, daily (`86400` seconds) collapses distinct training timestamps into one resolved bucket, and a flattened constant timestamp set also collapses to one resolved bucket.
- `TimeUtilsTest` verifies bucket math and day/time helpers, including DST-aware offset behavior.

Flattened-vs-fine-grained divergence assertion:
- Fine-grained and daily-coarsened requests are now asserted to produce different served future-route outputs.
- `TemporalContextResolver` is now asserted to flag both coarse collapse and flattened single-constant collapse explicitly through resolved-bucket cardinality checks.

Verdict: PASS
