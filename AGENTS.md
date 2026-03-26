# TARO Agent Guidance

This file gives project-level instructions to Codex and other coding agents working in this repository.

---

## Part 1: Primary Rule

When answering architecture, roadmap, planning, or product-behavior questions for TARO,
always read the latest project docs from disk first instead of relying on prior chat context.

---

## Part 2: Canonical Document Order

Use these documents in this priority order when they are relevant:

1. `docs/taro_v14_stagewise_breakdown.md`
   - Latest canonical staged roadmap and implementation sequencing reference.
   - Primary source for phase/stage order, stage closure rules, and the integrated v11/v12/v13/v14 roadmap.

2. `docs/taro_v13_architecture_plan.md`
   - Latest topology-evolution and failure-handling plan.
   - Current top-level direction for infrequent node/edge add-drop handling,
     transient failure quarantine, and atomic reload behavior.

3. `docs/taro_v12_architecture_plan.md`
   - Latest future-aware serving plan.
   - Current top-level architecture direction for scenario-aware routing,
     expected ETA / robust / top-K products, and ephemeral result retention for frontend querying.

4. `docs/taro_v11_architecture_plan.md`
   - Foundation for offline learning and compile-time refinement.
   - Baseline that v12 and v13 extend.

5. `docs/taro_v11_implementation_guide.md`
   - Practical implementation sequencing for the v11 learning foundation.

6. `docs/taro_v11_single_source_of_truth.md`
   - Contract-oriented reference when behavior, gates, or requirements need confirmation.

7. `docs/taro_v11_ssot_stagewise_breakdown.md`
   - Historical v11-only stage numbering and cross-reference aid.
   - Use only when a question explicitly depends on the old 28-stage numbering
     or historical stage intent.

8. `docs/trait_runtime_lock_audit_report.md`
   - Runtime-locking and trait-binding context when working on startup/runtime immutability topics.

9. `docs/taro_v12_v13_migration_report.md`
   - Current tracker for what v12/v13 additions have actually landed in code.

10. `docs/codex_repo_concern_map.md`
    - Package/file/concern map; fast orientation for any session.

11. `docs/codex_test_reference_map.md`
    - Test corpus map; use to identify the right evidence and regression suites.

---

## Part 3: Interpretation Rules

- v14 is the latest canonical staged roadmap.
- Higher-version docs supersede lower-version planning docs when they conflict.
- v13 is the latest operational topology and failure-handling direction.
- v12 remains the latest future-aware product-serving direction underneath v13.
- v11 remains the offline learning foundation underneath v12/v13/v14.
- `docs/taro_v11_ssot_stagewise_breakdown.md` is historical and must not override v14 staging
  unless the user explicitly asks for legacy stage mapping.
- Older stage docs are useful for implementation detail but must not override
  v14/v13/v12 architecture intent unless the user explicitly asks for historical stage behavior.
- README.md may be stale on implementation status; trust the migration report and code over README.

---

## Part 4: Expected Agent Behavior

- If the user asks for the "latest stage breakdown", "latest staged roadmap", or implementation
  sequence, start with `docs/taro_v14_stagewise_breakdown.md`.
- If the user asks for the "latest architecture plan", start with `docs/taro_v13_architecture_plan.md`.
- If the user asks how current work fits the roadmap, explain relative to v14 first,
  then v13, then v12, then v11 if needed.
- If the user asks about future-aware routing behavior, include the three v12 product layers:
  - expected ETA route
  - robust/P90 route
  - top-K scenario routes with confidence
- If the user asks about structural changes or failures, include the v13 distinction between:
  - transient failure quarantine
  - batched structural rebuild + atomic reload
- If the user asks about frontend retrieval flow, include the v12 ephemeral result-serving model.
- If a response depends on project architecture or stage sequencing, open the relevant doc(s)
  in this file before answering.
- When doing further implementation, explicitly check whether touched code is canonical,
  compatibility-only, stale, or effectively dead relative to v14.
- Do not assume existing code deserves preservation just because it passes tests;
  prefer tracking irrelevant or redundant paths and call them out for pruning
  when they are off the canonical roadmap.

---

## Part 5: Documentation Maintenance

- When creating a new top-level planning doc version, add it to this file above older versions.
- When creating a markdown architecture/plan doc meant for stakeholders,
  also render the matching PDF when feasible using `scripts/render_md_to_pdf.py`.

---

## Part 6: Semi-Autonomous Stage Execution Protocol

This section governs how Codex executes any stage (e.g. `C1`, `B4`, `D3`) when the user
is not present. The protocol is designed to be:

  - **Reliable over fast**: spend tokens freely; every step must be internally verified.
  - **Self-halting when genuinely blocked**: do not guess on architectural decisions;
    emit a HALT message instead (see Section 6.8).
  - **Auditable**: every step produces visible artifacts so the user can resume context
    after returning.

### 6.1 How to Enter the Protocol

The user will say something like:

```
Work on stage C1.
```
or
```
Continue from stage B4 step 3.
```

Before touching any code, read:
1. `AGENTS.md` (this file) — full pass.
2. `docs/taro_v14_stagewise_breakdown.md` — the full entry for the named stage.
3. `docs/codex_repo_concern_map.md` — relevant sections for the packages in scope.
4. `docs/codex_test_reference_map.md` — identify all test suites related to the stage.
5. Any architecture doc that the stage's `Shared contract dependencies` section references.

Do not skip any of these reads. Do not rely on context from a previous session.

---

### 6.2 The Step Sequence

Every stage is executed in exactly this sequence of numbered steps.
Each step must be completed and its output written to disk before moving to the next.

```
Step 1 — DEEP READ
Step 2 — THINK
Step 3 — PLAN
Step 4 — PLAN RECHECK
Step 5 — IMPLEMENT
Step 6 — SELF-REVIEW
Step 7 — TEST
Step 8 — TEST AUDIT
Step 9 — CODEBASE RECHECK
Step 10 — STAGE CLOSURE CHECKLIST
```

Each step is defined below.

---

#### Step 1 — DEEP READ

Goal: build a complete, current picture of the stage before writing a single line.

Actions:
1. Read all documents listed in Section 6.1.
2. Read the full stage entry from `docs/taro_v14_stagewise_breakdown.md`:
   - core purpose
   - current repo status
   - gate severity
   - functional requirements
   - non-functional requirements
   - named test suites
   - equivalence classes
   - dependencies
   - shared contract dependencies
   - closure criteria
   - repo anchors
3. Read every source file listed in the stage's `repo anchors`.
4. For each named test suite in the stage, read the existing test file if it exists,
   or note it as `MISSING` if it does not.
5. Read the `docs/codex_repo_concern_map.md` section covering each package touched.
6. Read the `docs/codex_test_reference_map.md` rows relevant to each package touched.

Output of Step 1 (write to `docs/agent_work/STAGE_XX_step1_deep_read.md`):
- Bullet list: every file read, its current status (exists / missing / stub).
- Bullet list: every named test suite, its file path, its current status (exists / missing).
- One-paragraph summary of what is already implemented and what is still absent.
- Explicit list of any gaps between the v14 closure criteria and the current repo state.

Do not proceed to Step 2 until this file is written.

---

#### Step 2 — THINK

Goal: reason deeply about the implementation before forming a plan.
This step is token-expensive by design. Use as many tokens as needed.

Think about all of the following. Write your reasoning explicitly.

**Architecture questions:**
- Does the stage touch an interface that is used by other stages?
  If yes, which stages, and what contract must be preserved?
- Does the stage modify any class that is bound at startup under the runtime lock?
  If yes, read `docs/trait_runtime_lock_audit_report.md` and confirm the change is compatible
  with startup immutability.
- Does the stage interact with the v12 retained-result serving model?
  If yes, confirm that TTL semantics and byte-budget contracts are not broken.
- Does the stage interact with v13 topology reload or quarantine?
  If yes, confirm that atomic swap invariants are preserved.
- Does the stage affect any temporal attribute (granularity / direction / density /
  persistence / periodicity / recency / homophily / preferential attachment)?
  If yes, identify which and check the paper attribute severity table in v14 Section 4.

**Behavioral questions:**
- For every functional requirement in the stage entry, write down:
  - the specific class(es) and method(s) that will implement it
  - any data structure choices that need to be made and why
  - what the correct behavior is for the normal case
  - what the correct behavior is for each boundary or failure case
- For every equivalence class in the stage entry, write down:
  - which test suite will cover it
  - whether that test suite currently has a gap for this case

**Dependency questions:**
- Are all dependency stages (`Dependencies:` list) already closed (green)?
  If any are not closed, can the current stage still proceed safely?
  If not, HALT — see Section 6.8.
- Are all shared contract dependencies listed in the stage entry confirmed as passing?
  Run the relevant test suites and check before forming the plan.

**Risk questions:**
- What is the worst regression this implementation could introduce?
- Which existing tests are most likely to catch that regression?
- Is there any code that currently passes tests but is stale or non-canonical per v14?
  If so, note it explicitly.

Output of Step 2 (append to `docs/agent_work/STAGE_XX_step2_think.md`):
- Full written reasoning for each category above.
- Explicit yes/no answers to every question.
- Named risks and which test suites mitigate them.

Do not proceed to Step 3 if any question above results in an unresolvable ambiguity.
Instead, go to Section 6.8 (HALT).

---

#### Step 3 — PLAN

Goal: produce a concrete, ordered implementation plan.

The plan must list every action in the exact order it will be taken.
Each action must name the specific file and the specific change.
No hand-waving. No "add the necessary logic here".

Plan format — write each item as:

```
[ACTION N]
  File       : <path relative to repo root>
  Type       : NEW_FILE | MODIFY_CLASS | ADD_METHOD | ADD_TEST | MODIFY_TEST |
               ADD_INTERFACE | DELETE_DEAD_CODE | OTHER
  What       : <one-sentence precise description of the change>
  Why        : <one-sentence tie to the functional requirement or closure criterion it satisfies>
  Test cover : <test suite and test method that will verify this change>
```

After writing all actions, write a short dependency-ordered justification:
why this particular ordering is safe given the startup-lock, runtime contracts,
and test execution model.

Also write a rollback note: if the implementation must be abandoned midway,
which actions are reversible and which leave the repo in an intermediate state.

Output of Step 3 (write to `docs/agent_work/STAGE_XX_step3_plan.md`).

Do not proceed to Step 4 until this file is complete and every action has a `Test cover` entry.
If you cannot assign a `Test cover` entry to an action, stop and go to Section 6.8.

---

#### Step 4 — PLAN RECHECK

Goal: adversarially review the plan before a single line of production code is written.

For each action in the plan, challenge it:

1. **Completeness**: does this action fully satisfy its stated functional requirement,
   or does it only partially satisfy it?
   If partial, is the remaining part covered by a subsequent action?
2. **Isolation**: does this action change behavior in a way that affects stages
   that are not in scope? If yes, is that intentional and safe?
3. **Contract preservation**: does this action preserve all interface contracts
   used by dependent stages and test suites?
4. **Test sufficiency**: is the named `Test cover` entry a genuine behavioral test
   (not just a compilation guard)?
5. **Ordering**: is there any action later in the plan that must logically come earlier?
6. **Dead code risk**: does any action add a code path that will immediately become
   unreachable given the runtime lock or execution model?
7. **Cross-phase contract regression**: does any action change an artifact that is
   cited in a cross-phase contract test from `docs/taro_v14_stagewise_breakdown.md` Section 10?
   If yes, confirm the cross-phase test still passes after this change.

For each failure found in the recheck, either:
- Revise the plan (update `STAGE_XX_step3_plan.md`) and note the revision, or
- Determine that the failure is unresolvable and go to Section 6.8.

Output of Step 4 (write to `docs/agent_work/STAGE_XX_step4_plan_recheck.md`):
- Verdict per action: PASS | REVISED | BLOCKED.
- For each REVISED: describe what changed and why.
- For each BLOCKED: describe exactly what is ambiguous or unresolvable.
- Final recheck verdict: PROCEED or HALT.

If the final verdict is HALT, go to Section 6.8 immediately.
Do not write any production code if the verdict is HALT.

---

#### Step 5 — IMPLEMENT

Goal: execute the approved plan exactly, action by action.

Rules during implementation:

- Implement one action at a time. After each action, compile with `mvn compile -q` (or
  equivalent) and confirm it compiles before moving to the next action.
- Do not skip actions. Do not reorder actions without re-running Step 4 for the reordering.
- Do not add undocumented behavior. If an unexpected need arises that was not in the plan,
  stop, go back to Step 3, revise the plan, re-run Step 4, and then continue.
- Every new or modified public method must have a Javadoc comment that names
  the stage it belongs to and the closure criterion it satisfies.
  Example: `/** Stage C1 — recency-weighted scenario scoring. Satisfies closure criterion: ... */`
- Every new class must have a package-level comment or class Javadoc that places it
  in the v14 stage context.
- No TODO comments may be left in production code without a corresponding entry
  in `docs/agent_work/STAGE_XX_implementation_notes.md`.

Compilation rule: every individual action must leave the repo in a compilable state.
If an action breaks compilation and you cannot fix it within that same action's scope,
revert the action, document the failure in `docs/agent_work/STAGE_XX_implementation_notes.md`,
and go to Section 6.8.

Output of Step 5:
- Modified/new production source files (the actual code).
- `docs/agent_work/STAGE_XX_implementation_notes.md` — any deviations, surprises,
  or non-obvious choices made during implementation, with the plan action number cited.

---

#### Step 6 — SELF-REVIEW

Goal: before running any test, read every changed file as a reviewer who did not write the code.

For each modified or new file:

1. Read the full file top to bottom. Not a diff — the whole file.
2. Check: does every public method have the stage-context Javadoc from Step 5?
3. Check: is there any code that is unreachable given the runtime execution model?
4. Check: is there any edge case in the equivalence classes list from the stage entry
   that is not covered by the implementation?
5. Check: is there any import, dependency, or utility that could be removed or is
   already provided elsewhere in the repo?
6. Check: is there any behavior in this file that contradicts a cross-phase contract?
7. Check: does the implementation match the plan exactly, or are there undocumented deviations?

For each issue found:
- Fix it immediately and note it in `docs/agent_work/STAGE_XX_step6_self_review.md`.
- If the fix requires a plan change, go back to Step 3 for that specific change,
  re-run Step 4, then fix and continue.

Output of Step 6 (write to `docs/agent_work/STAGE_XX_step6_self_review.md`):
- File-by-file review log: CLEAN | FIXED (with description) | CONCERN (with description).
- Any remaining concerns that do not block but should be tracked.

Do not proceed to Step 7 if any file is in status BLOCKED.

---

#### Step 7 — TEST

Goal: run the test suites in the correct order and confirm all pass.

Run order:
1. Compile: `mvn compile -q` — must be clean.
2. Run the stage's own named test suites:
   `mvn test -Dtest=<TestSuite1>,<TestSuite2> -q`
3. Run the shared contract dependency test suites cited in the stage entry.
4. Run the cross-phase contract tests relevant to this stage from v14 Section 10.
5. Run the full default lane: `mvn test -q`
6. Run Python tests if any Python files were touched: `.venv/bin/python -m pytest -q`
7. If any perf-tagged suites are named in the stage entry, run:
   `mvn -Pperf-tests test -Dtest=<PerfSuite> -q`

For each test failure:
- Diagnose whether it is a pre-existing failure or a regression introduced in Step 5.
- If a regression: fix it, go back to Step 6 for the affected file, then re-run Step 7.
- If a pre-existing failure: document it in `docs/agent_work/STAGE_XX_step7_test_log.md`
  with evidence that it predates this session (e.g., git blame, test history).
  Do not claim stage closure if a pre-existing failure affects a named test suite
  for this stage. Go to Section 6.8 instead.

Output of Step 7 (write to `docs/agent_work/STAGE_XX_step7_test_log.md`):
- Test suite name, run command, result (PASS / FAIL / ERROR), and any failure detail.
- Final verdict: ALL PASS | FAILURES (describe each).

Do not proceed to Step 8 if verdict is FAILURES, unless the failure is confirmed
pre-existing AND does not affect the stage's closure criteria.

---

#### Step 8 — TEST AUDIT

Goal: verify that the tests themselves are correct, not just green.

For each named test suite in the stage entry, and for every test method added or
modified in Step 5:

1. Read the test method fully.
2. Check: does it actually exercise the behavior named in the stage's equivalence classes,
   or is it a trivial no-op that passes by construction?
3. Check: does it use real behavioral assertions (asserting specific output values,
   specific state, or specific exception types) rather than just
   `assertNotNull` / `assertTrue(true)` style guards?
4. Check: does it use `RoutingFixtureFactory`, `TemporalTestContexts`,
   `TransitionTestContexts`, or `TopologyTestFixtures` where appropriate
   (see `docs/codex_test_reference_map.md` Section 2)?
5. Check: if the test is tagged `@Tag("smoke")` or `@Tag("integration")`, is the tag
   correct for the test's actual scope (smoke = fast self-contained; integration = service-level)?
6. Check: is there a test for the negative/failure equivalence classes (invalid input,
   boundary violations, contract violations), not just the happy path?

For each weak test found:
- Strengthen it. Re-run Step 7 after any test change.
- Document the strengthening in `docs/agent_work/STAGE_XX_step8_test_audit.md`.

Output of Step 8 (write to `docs/agent_work/STAGE_XX_step8_test_audit.md`):
- Per-test assessment: STRONG | STRENGTHENED (what changed) | WEAK (why, if left as-is).
- Final test quality verdict: ACCEPTABLE | CONCERNS.

If verdict is CONCERNS on a test that covers a hard-blocker closure criterion,
go to Section 6.8.

---

#### Step 9 — CODEBASE RECHECK

Goal: zoom out from the stage and confirm the whole repo is in a healthy state.

Actions:
1. Run `mvn test -q` one final time and confirm the full default lane is clean.
2. Run `mvn -Psmoke-tests test -q` and confirm all smoke tests pass.
3. Scan every file touched during this session (production and test) and confirm:
   a. No file has been left in a partially-implemented state.
   b. No interface has a new method without implementation in all concrete classes.
   c. No import is unused.
   d. No class referenced in a test does not exist.
4. Cross-check the v14 stage entry's `closure criteria` one more time against what
   was implemented. Write a one-to-one mapping: each criterion → each implementing
   element (class, method, test).
5. Check whether any code touched in this session is stale or non-canonical per v14.
   If yes, note it explicitly as a pruning candidate in
   `docs/agent_work/STAGE_XX_step9_codebase_recheck.md`.
   Do not prune without explicit user authorization unless the dead code is provably
   unreachable and was already marked for removal in a prior session.
6. Confirm that `docs/codex_repo_concern_map.md` and `docs/codex_test_reference_map.md`
   are still accurate for the files touched. If new files were added that are not yet
   in those maps, append entries and note the update.

Output of Step 9 (write to `docs/agent_work/STAGE_XX_step9_codebase_recheck.md`):
- Final test run result.
- Closure criterion to implementation mapping (table format).
- List of stale/non-canonical code found.
- List of doc map updates made.
- Overall health verdict: HEALTHY | ISSUES (describe each).

If verdict is ISSUES and the issues affect closure criteria, go to Section 6.8.

---

#### Step 10 — STAGE CLOSURE CHECKLIST

Goal: produce the final stage closure record.

Write `docs/agent_work/STAGE_XX_closure.md` containing:

```
Stage      : <stage id, e.g. C1>
Date       : <ISO date>
Gate       : <gate severity from v14>
Status     : CLOSED | PARTIAL (reason) | BLOCKED (see halt record)

Closure Criteria Satisfaction:
  [criterion 1 text] → [implementing class/method/test] → SATISFIED / NOT SATISFIED
  [criterion 2 text] → ...
  ...

Named Test Suites:
  [suite name] → PASS / FAIL / MISSING
  ...

Cross-Phase Contract Tests:
  [suite name] → PASS / FAIL / NOT APPLICABLE
  ...

Pre-existing Failures (if any):
  [suite name] → KNOWN FAILURE → [evidence it predates this session]

Stale Code Identified:
  [file] → [reason it is stale] → PRUNING CANDIDATE

Implementation Notes:
  [any non-obvious choice or deviation from the plan, with rationale]

Next Stage Recommendation:
  [which stage should be worked next and why, based on v14 dependency graph]

Agent Halt Record:
  [if Section 6.8 was triggered during this session, record it here]
```

After writing the closure record:
- If Status is CLOSED: output the following final message to the chat exactly:

```
STAGE <id> COMPLETE.
All closure criteria satisfied. All named test suites pass.
Closure record written to docs/agent_work/STAGE_XX_closure.md.
Recommended next stage: <stage id and reason>.
Stale code candidates (if any): <list or "none">.
```

- If Status is PARTIAL or BLOCKED: go to Section 6.8.

---

### 6.3 Working Notes Convention

All intermediate work files written during the protocol must go under:
`docs/agent_work/STAGE_<ID>_step<N>_<name>.md`

Example: `docs/agent_work/STAGE_C1_step2_think.md`

These files are ephemeral working notes and are not canonical documentation.
They exist so the user can audit the session and so a future session can resume
from a known step if the agent was interrupted.

Do not delete or overwrite these files during a session.
Do not include them in production releases or canonical docs.

---

### 6.4 Token Budget Guidance

There is no token limit that justifies a shallow step.
The reliability of the output is the only constraint.

Specifically:
- Step 2 (THINK) should be as long as needed to resolve every architectural question.
  A short Step 2 is a red flag, not a sign of efficiency.
- Step 4 (PLAN RECHECK) should be adversarial. If every item passes on first read,
  that is suspicious — re-examine more carefully.
- Step 8 (TEST AUDIT) should read every added/modified test method in full.
  Do not skim.

---

### 6.5 Multi-Session Resume

If the agent session ends before the protocol completes (e.g. due to context limit),
a new session can resume by:

1. Reading `AGENTS.md` (this file) fully.
2. Reading `docs/agent_work/STAGE_<ID>_step<N>_*.md` for all existing step files for the stage.
3. Identifying the last completed step (the highest N for which a step file exists and
   contains a complete output, not a partial draft).
4. Resuming from the step immediately after the last completed step.

The resuming agent must not assume that steps it did not personally execute were
done correctly. It should briefly re-read the output of the last two completed steps
and verify they are self-consistent before continuing.

---

### 6.6 Scope Discipline

The agent must not expand scope beyond the named stage during a session.

Specifically:
- Do not implement parts of a future stage in advance, even if it seems convenient.
- Do not refactor code that is outside the named stage's `repo anchors`, even if
  the refactor seems obviously correct.
- Do not add test cases for a different stage's equivalence classes.

If a change in an adjacent area is clearly necessary for the current stage to compile
or pass tests, it is allowed, but it must be:
- Documented in `docs/agent_work/STAGE_XX_implementation_notes.md`.
- Minimal: the smallest change that unblocks the current stage.
- Consistent with the v14 closure criteria and architectural rules of the adjacent stage.

---

### 6.7 Code Classification Rule

Before modifying any existing file, classify it:

| Classification    | Meaning                                                                 | Action                                         |
|-------------------|-------------------------------------------------------------------------|------------------------------------------------|
| CANONICAL         | Lives on the v14 implementation path and is actively needed             | Modify freely within stage scope               |
| COMPATIBILITY     | Exists only to bridge old behavior for tests or callers not yet updated | Modify minimally; note it as a pruning target  |
| STALE             | Present in-tree but no longer on the canonical path                     | Do not expand; flag for pruning                |
| DEAD              | Unreachable given the current execution model                           | Do not touch; flag for pruning with evidence   |

Write the classification for every file you touch in
`docs/agent_work/STAGE_XX_step5_file_classifications.md`.
If you are unsure of a classification, go to Section 6.8.

---

### 6.8 HALT Protocol

Trigger a HALT when any of the following conditions is true:

1. **Dependency not closed**: a stage listed in `Dependencies:` is not confirmed green
   and the current stage cannot safely proceed without it.

2. **Unresolvable architectural ambiguity**: Step 2 produces a question that cannot be
   answered from the documents on disk, and getting it wrong would affect a
   hard-blocker or release-critical closure criterion.

3. **Plan recheck BLOCKED verdict**: Step 4 finds an action that is BLOCKED with no
   viable revision.

4. **Mid-implementation surprise**: during Step 5, an unexpected condition arises
   (a class has a different contract than the docs describe, a required interface
   does not exist, a type parameter is incompatible) that was not covered in the plan
   and cannot be resolved without architectural judgment.

5. **Pre-existing test failure on a closure-relevant suite**: Step 7 finds a failure
   in a named test suite for this stage that predates the current session and has not
   been acknowledged by a prior closure record.

6. **Code classification ambiguity**: a file that must be modified has no clear
   classification (could be canonical or stale) and the decision changes the
   implementation direction materially.

7. **Cross-phase contract broken with no clear fix**: the implementation satisfies
   the current stage but breaks a cross-phase contract test and no fix is apparent
   within the current stage's scope.

**When HALT is triggered:**

1. Stop all implementation immediately.
   Do not write any further production code after the halt condition is detected.

2. Revert any partial changes from the current action (not the whole session —
   only the action that was in progress when HALT was triggered).
   Confirm compilation is clean after revert.

3. Run `mvn test -q` to confirm the repo is in the same state as before the failed action.

4. Write `docs/agent_work/STAGE_XX_halt_record.md` containing:
   ```
   Stage              : <stage id>
   Step               : <step number where halt occurred>
   Action             : <plan action number, if applicable>
   Halt condition     : <one of the 7 conditions above, with detail>
   Last clean state   : <description of what is in the repo right now and that it compiles and passes tests>
   Files in progress  : <list of any files that were being edited when halt was triggered>
   Decision needed    : <precise question the user must answer, with the specific options and their consequences>
   Evidence           : <relevant quotes from docs, code, or test output that define the decision space>
   Recommended option : <if there is a clearly better option, say so and say why — do not guess if genuinely ambiguous>
   ```

5. Output the following as the **final message in the chat** (do not output anything else after this):

```
⛔ HALT — STAGE <id>, STEP <N>

I cannot proceed without a decision from you.

SITUATION:
<one paragraph describing what was being worked on and what was encountered>

DECISION NEEDED:
<precise question — be specific, not vague>

OPTIONS:
  A) <option A and its consequence>
  B) <option B and its consequence>
  [C) ...if applicable]

CURRENT REPO STATE:
✅ Compiles cleanly.
✅ All tests pass (or: ⚠️ pre-existing failure in <suite>, predates this session).
📁 Work files saved under docs/agent_work/STAGE_<id>_*.md

RESUME INSTRUCTIONS (after you decide):
Reply with your decision and I will continue from Step <N>, Action <M>.
```

The agent must not attempt to resolve the ambiguity itself.
The agent must not continue past the HALT message.
The agent must not ask follow-up questions in the HALT message — one clear decision request only.

---

### 6.9 Between-Stage Transition

After a stage is closed (Status: CLOSED in the closure record), before starting the next stage:

1. Confirm `mvn test -q` is clean.
2. Confirm `mvn -Psmoke-tests test -q` is clean.
3. Read the `Next Stage Recommendation` from the closure record.
4. Verify the recommended next stage's `Dependencies:` list against the v14 breakdown.
   Confirm all dependencies are now closed before entering the next stage's Step 1.
5. If any dependency is not closed, output to chat:

```
ℹ️ STAGE <id> CLOSED.
Next recommended stage is <next id>, but its dependency <dep id> is not yet closed.
Available unblocked stages: <list stages whose dependencies are all closed>.
Please confirm which stage to work next.
```

Then wait for user input. Do not start a new stage without confirmation if
there is a dependency gap.

---

## Part 7: Test Execution Reference (Quick Lookup)

| Command                                         | What runs                                      |
|-------------------------------------------------|------------------------------------------------|
| `mvn test`                                      | Default lane: unit + untagged + integration + smoke (no perf) |
| `mvn -Psmoke-tests test`                        | Only `@Tag("smoke")` tests                     |
| `mvn -Pintegration-tests test`                  | Only `@Tag("integration")` tests               |
| `mvn -Pperf-tests test`                         | Only `@Tag("perf")` tests                      |
| `mvn test -Dtest=SuiteName1,SuiteName2`         | Specific suites only                           |
| `mvn compile -q`                                | Compile check only                             |
| `.venv/bin/python -m pytest -q`                 | Python tests under `src/main/python/tests`     |
| `scripts/run_java_tests.sh`                     | Java test helper script                        |
| `scripts/run_python_tests.sh`                   | Python test helper script                      |

Key shared fixtures — always check these before writing new test infrastructure:
- `RoutingFixtureFactory` — FlatBuffer-backed route, matrix, graph, cost fixtures
- `TemporalTestContexts` — pre-bound Stage 16 temporal contexts
- `TransitionTestContexts` — pre-bound Stage 17 transition contexts
- `TopologyTestFixtures` — v13 topology harness, fixed clock, quarantine resolver, grid-source helpers

---

## Part 8: Gate Severity Reference (From v14)

| Severity           | Meaning                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------|
| `hard blocker`     | Cannot claim green for release scope until all closure criteria pass                       |
| `release-critical` | Must pass for releases that depend on that feature family                                   |
| `calibration gate` | May ship only with bounded uncertainty semantics and documented calibration posture         |
| `advisory`         | Informative and tracked, but not a release gate                                            |

Paper-driven temporal attribute severity (from v14 Section 4):

| Attribute               | Severity in TARO     | Reason                                                          |
|-------------------------|----------------------|-----------------------------------------------------------------|
| temporal granularity    | release-critical     | discretization drift can quietly flatten time behavior          |
| direction               | hard blocker         | asymmetric corridors and turn-sensitive costs are directional   |
| density                 | hard blocker         | sparse candidate or prior collapse distorts scenario coverage   |
| persistence             | release-critical     | durable congestion patterns must remain representable           |
| periodicity             | release-critical     | recurring patterns affect expected and robust outputs           |
| recency                 | hard blocker         | near-horizon forecasting is invalid without fresh evidence      |
| homophily               | calibration gate     | useful modeling signal, not a default release blocker           |
| preferential attachment | positive prior signal| high-traffic corridors should influence priors when supported   |

---