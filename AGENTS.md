# TARO Continuous Improvement Agent

## Purpose

This agent runs an infinite agile loop: scan the codebase for flaws, triage
them by severity, fix the highest-priority flaw, verify the fix, record it,
and immediately begin the next scan. It does not stop between cycles. It does
not wait for a human to approve a cycle. It halts only on the conditions
defined in Section 7.

This file is the agent's only instruction source. It supersedes all prior
AGENTS.md documents for this purpose.

---

## 1. The Loop

Every cycle follows this exact sequence. No step may be skipped.

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│   SCAN ──► TRIAGE ──► PICK ──► FIX ──► VERIFY      │
│     ▲                                      │        │
│     └──────────── RECORD ◄─────────────────┘        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

Each step is atomic: complete it fully and write its output before
proceeding. A partial step is treated as no step.

---

## 2. SCAN

### 2.1 What to Scan

Run all of the following on every cycle, in order. Capture every finding.

```
# Java build and tests
mvn verify -q 2>&1

# Java static analysis
mvn checkstyle:check spotbugs:check -q 2>&1

# Python tests
.venv/bin/python -m pytest -q 2>&1

# Python type check
.venv/bin/python -m mypy src/main/python --ignore-missing-imports -q 2>&1

# Frontend dependency rule audit
node scripts/check-import-boundaries.js 2>&1

# Frontend lint
cd taro-frontend && npx eslint . --format compact 2>&1

# Dead code detection (Java)
mvn dependency:analyze -q 2>&1

# API contract drift
node scripts/check-api-contract.js 2>&1

# Temporal competency regression check
mvn test -Dtest=TemporalFidelityContractTest,QuarantineRecencyOverrideTest,
    DirectedProfileDivergenceTest,AsymmetricCorridorReloadTest -q 2>&1
```

If a scan script does not exist yet, the agent must create it before treating
that scan category as active. Creating a missing scan script is itself a
flaw-fix cycle (Section 4 type TOOLING).

### 2.2 Scan Output Format

After running all scans, write `docs/agent/scan_YYYYMMDD_HHMMSS.md`:

```
Scan ID     : <timestamp>
Cycle       : <monotonically incrementing integer, persisted in docs/agent/cycle.txt>
Duration    : <seconds>

Findings:
  <SEVERITY> | <TYPE> | <LOCATION> | <DESCRIPTION>
  ...

Summary:
  BLOCKER   : N
  CRITICAL  : N
  MAJOR     : N
  MINOR     : N
  ADVISORY  : N
  Total     : N
```

Every line in scan tool output that indicates a failure, warning, violation,
or error is a finding. Passed checks produce no finding lines.

---

## 3. TRIAGE

### 3.1 Severity Classification

Assign exactly one severity to every finding.

| Severity | Condition |
|---|---|
| BLOCKER | Build fails, any test fails, any temporal competency contract test fails, any frontend cross-app import violation |
| CRITICAL | Static analysis error (not warning), mypy error, API contract field missing or type-changed, any hard-blocker stage closure criterion broken |
| MAJOR | Static analysis warning that indicates real defect risk (null dereference, unchecked cast, resource leak), missing error handling on API boundary, `useEffect` without cleanup, context value not memoised |
| MINOR | Code style violation, unused import, dead code, advisory Checkstyle rule, naming inconsistency |
| ADVISORY | Informational only — log it, do not fix in this cycle |

If a single scan line matches multiple severities, assign the highest.

### 3.2 Deduplication

Before writing the triage output, deduplicate against
`docs/agent/fixed_flaws.log`. Any finding whose `<LOCATION> + <DESCRIPTION>`
hash already appears in that log with status `FIXED` is skipped unless the
tool reports it again — meaning the fix regressed. A regressed fix is
re-classified as one severity level higher than its original classification.

### 3.3 Triage Output

Append to `docs/agent/triage_log.md`:

```
Cycle       : <N>
Scan ID     : <timestamp>

Active findings after dedup:
  <SEVERITY> | <TYPE> | <LOCATION> | <DESCRIPTION> | HASH:<8-char hash>
  ...

Regressions detected:
  <original HASH> | <original severity> → <new severity> | <LOCATION>
```

---

## 4. PICK

Select exactly one finding to fix per cycle. Selection rule is strict priority:

```
1. Any BLOCKER            — pick the first one (scan order)
2. Any CRITICAL           — pick the first one
3. Any MAJOR              — pick the oldest by first-seen cycle number
4. Any MINOR              — pick the oldest by first-seen cycle number
5. No actionable findings — enter IDLE (see Section 7.3)
```

If the selected finding cannot be fixed without resolving a different finding
first (dependency), pick the blocking dependency instead and note the chain
in the fix record.

Write `docs/agent/pick_<cycle>.md`:

```
Cycle        : <N>
Selected     : <HASH>
Severity     : <SEVERITY>
Type         : <TYPE>
Location     : <LOCATION>
Description  : <full description>
Rationale    : <why this one over alternatives, if non-obvious>
Blocked by   : <HASH of dependency, or "none">
```

---

## 5. FIX

### 5.1 Fix Types

Every fix is classified as one of these types. The type determines the fix
protocol.

| Type | Definition |
|---|---|
| BUILD | Compilation error, missing dependency, broken module |
| TEST | Test failure — either fix the code under test or fix a broken test assertion; never delete a test to make it pass |
| TEMPORAL | Temporal competency contract test failure or regression in direction, recency, density, persistence, periodicity, or granularity behavior |
| STATIC | Static analysis violation — fix the code, not the suppression |
| TYPE | mypy or TypeScript type error |
| ARCH | Architecture rule violation (cross-app import, context not memoised, prop drilling beyond one level, URL string outside endpoints.js, localStorage usage) |
| CONTRACT | API response field missing, type-changed, or endpoint removed without frontend update |
| COVERAGE | Missing behavioral assertion on a hard-blocker test suite |
| DEAD | Dead code, unused import, unreachable branch |
| TOOLING | Missing scan script, broken scan script, missing build script |
| STYLE | Pure style/naming violation with no behavioral impact |

### 5.2 Fix Protocol by Type

#### BUILD, TEST, TEMPORAL, STATIC, TYPE
1. Read the full error output for the finding.
2. Read every source file involved before making any edit.
3. Make the minimum edit that resolves the finding without introducing
   new findings. Do not refactor beyond the finding boundary.
4. Re-run the specific failing command to confirm the finding is gone.
   Do not proceed to VERIFY if the targeted command still fails.

#### ARCH
1. Identify the exact import path or pattern violating the rule.
2. Trace what the importer actually needs and whether the right abstraction
   exists in `shared/` already.
3. If the abstraction exists: fix the import path.
4. If it does not exist: create it in `shared/` first, then fix the import.
   This is two edits but one fix record.
5. Re-run `scripts/check-import-boundaries.js` to confirm clean.

#### CONTRACT
1. Identify which frontend component or hook consumes the changed field.
2. Update `shared/api/transforms.js` to normalise the new shape before
   propagating it. Do not scatter field-name assumptions into components.
3. If the field was removed from the backend entirely, surface a typed
   `ApiContractError` in `TaroHttpClient` — do not silently omit the field.
4. Update `shared/api/endpoints.js` if the endpoint path changed.

#### COVERAGE
1. The missing assertion must be behavioral — it must fail if the code under
   test is wrong, and pass if it is right.
2. Do not add an assertion that is trivially always true (e.g., `assertNotNull`
   on a literal).
3. After writing the assertion, temporarily break the code it tests to confirm
   the assertion catches it, then revert the break.

#### DEAD, STYLE
1. Make the edit.
2. Do not combine a DEAD or STYLE fix with any behavioral change.

#### TOOLING
1. Create the missing script.
2. Run it once to confirm it produces useful output on the current codebase.
3. Document its expected output format in a comment at the top of the script.

### 5.3 Fix Constraints

- **One finding per cycle.** Do not fix two findings in one edit session,
  even if they are adjacent lines. Each must be its own cycle with its own
  verification.
- **No suppression escapes.** Do not add `@SuppressWarnings`, `# noqa`,
  `// eslint-disable`, or `@Ignore` to make a finding disappear. The only
  exceptions are documented false positives in `docs/agent/false_positives.md`
  (see Section 6.4).
- **No test deletion.** A test that fails is evidence of a real problem.
  Fix the code, or if the test is genuinely wrong, document exactly why
  in the fix record before changing the assertion.
- **No scope creep.** If fixing a finding reveals a different problem,
  log the new problem as a finding in the next scan. Do not fix it inline.

### 5.4 Fix Record

Write `docs/agent/fix_<cycle>.md` before running VERIFY:

```
Cycle       : <N>
Finding     : <HASH>
Type        : <TYPE>
Location    : <LOCATION>
Description : <what was wrong>

Root cause  : <why it existed — not just what the symptom was>

Edit summary:
  File      : <path>
  Change    : <description of the change, no code>
  ...

Scope check : <confirm no behavioral change outside the finding boundary>
```

---

## 6. VERIFY

### 6.1 Verification Suite

After every fix, run the full verification suite in order.
All commands must pass before the cycle closes.

```
# 1. Build
mvn verify -q

# 2. Python
.venv/bin/python -m pytest -q
.venv/bin/python -m mypy src/main/python --ignore-missing-imports -q

# 3. Temporal contracts (always run — regressions must be caught immediately)
mvn test -Dtest=TemporalFidelityContractTest,QuarantineRecencyOverrideTest,
    DirectedProfileDivergenceTest,AsymmetricCorridorReloadTest,
    ReloadTemporalContinuityContractTest,ScenarioPriorConsistencyContractTest -q

# 4. Architecture
node scripts/check-import-boundaries.js
cd taro-frontend && npx eslint . --format compact

# 5. API contract
node scripts/check-api-contract.js
```

### 6.2 Verification Outcome

**All pass**: cycle closes, proceed to RECORD.

**Any failure introduced by the fix** (a command that passed in the previous
cycle now fails): the fix is reverted immediately. The agent writes a
REVERT record to `docs/agent/fix_<cycle>.md`:

```
REVERT: fix introduced new failures
  New failures:
    <list each new failing command and its first error line>
  Action: all edits from this cycle reverted
  Next action: re-TRIAGE this finding with root cause updated
```

The finding is re-queued at the same severity. The cycle counter increments.
Do not attempt the same fix strategy twice in a row without changing the
approach.

**A pre-existing failure that already existed before this cycle**: do not
count it as introduced by the fix. Note it in the fix record as pre-existing
and continue.

### 6.3 Verification Record

Append to `docs/agent/verify_log.md`:

```
Cycle   : <N>
Finding : <HASH>
Result  : PASS | REVERT
Commands run:
  mvn verify              : PASS | FAIL
  pytest                  : PASS | FAIL
  mypy                    : PASS | FAIL
  temporal contracts      : PASS | FAIL
  import boundary check   : PASS | FAIL
  eslint                  : PASS | FAIL
  api contract check      : PASS | FAIL
```

### 6.4 False Positive Registration

If after genuine investigation a scan finding is confirmed to be a tool error
(not a real code problem), the agent may register it as a false positive.
Append to `docs/agent/false_positives.md`:

```
Hash        : <HASH>
Tool        : <tool name>
Location    : <file:line>
Finding     : <original finding text>
Reason      : <why this is a false positive — must be specific, not "seems fine">
Registered  : <cycle N>
```

Once registered, deduplication in TRIAGE will suppress this hash permanently.
False positive registrations must be rare. If more than 3 are registered in
10 consecutive cycles, the agent must stop and emit a TOOLING AUDIT alert
(see Section 7.2).

---

## 7. RECORD and Loop

### 7.1 RECORD Step

After a PASS verification, append one line to `docs/agent/fixed_flaws.log`:

```
<HASH> | <SEVERITY> | <TYPE> | <LOCATION> | FIXED | cycle=<N> | <timestamp>
```

After a REVERT, append:

```
<HASH> | <SEVERITY> | <TYPE> | <LOCATION> | REVERTED | cycle=<N> | <timestamp>
```

Increment `docs/agent/cycle.txt` by 1.

Then immediately return to SCAN. No pause. No user confirmation.

### 7.2 Cycle Summary (every 10 cycles)

Every 10 cycles, write `docs/agent/summary_cycle_<N>.md`:

```
Cycles      : <N-9> through <N>
Fixed       : <count> (breakdown by type)
Reverted    : <count>
Regressions : <count>
Remaining by severity:
  BLOCKER   : <count>
  CRITICAL  : <count>
  MAJOR     : <count>
  MINOR     : <count>

Velocity    : <fixed per cycle, last 10>
Regression rate: <reverted / attempted, last 10>

Notable patterns:
  <any type or location that appears in 3+ findings this window>

False positives registered this window: <count>
TOOLING AUDIT triggered: YES | NO
```

If false positives this window ≥ 3, emit `TOOLING AUDIT REQUIRED` and halt
(Section 7.4). The summary is the halt record.

### 7.3 IDLE Condition

If TRIAGE produces zero actionable findings (all findings are ADVISORY or
already in `fixed_flaws.log`):

1. Write `docs/agent/idle_<cycle>.md` with the current finding counts.
2. Run a deeper scan:
   ```
   mvn test -Dtest=*ContractTest,*CompetencyTest,*GuardrailTest -q
   mvn dependency:analyze -q
   .venv/bin/python -m pytest --tb=short -q
   node scripts/check-api-contract.js --strict
   ```
3. If deeper scan produces new findings, resume loop from TRIAGE.
4. If deeper scan also produces nothing: write idle record and **pause for
   60 seconds**, then restart from SCAN. Do not halt on IDLE — the codebase
   is not frozen and new findings will emerge.

### 7.4 HALT Conditions

The agent halts completely (stops the loop, does not restart) only when:

| Condition | Halt code |
|---|---|
| The same BLOCKER finding is REVERTED 3 times in a row with different fix strategies | `BLOCKER_UNRESOLVABLE` |
| A temporal competency contract test regresses and cannot be fixed within 5 cycles | `TEMPORAL_REGRESSION_UNRESOLVABLE` |
| `docs/agent/false_positives.md` grows by ≥ 3 in any 10-cycle window | `TOOLING_AUDIT_REQUIRED` |
| `docs/agent/cycle.txt` cannot be read or written | `RECORD_FAILURE` |
| `mvn verify` fails and the Java source cannot be parsed at all | `BUILD_UNRESOLVABLE` |

On halt, write `docs/agent/HALT_<code>_cycle_<N>.md`:

```
HALT CODE   : <code>
Cycle       : <N>
Trigger     : <exact condition that triggered halt>
Last finding: <HASH> | <LOCATION> | <DESCRIPTION>

History (last 5 cycles):
  <cycle> : <HASH> : FIXED | REVERTED

Required human action:
  <specific description of what the agent could not resolve and why>
```

Halt is not failure. It is the agent correctly recognising the boundary of
its authority and handing off with a precise, actionable record.

---

## 8. Flaw Taxonomy

The agent must be able to recognise every flaw class below during SCAN.
This list is ordered by the severity they most commonly produce.

### 8.1 Java / Backend

| Flaw class | Detection | Common cause |
|---|---|---|
| Test failure | `mvn verify` red | Logic error, broken assumption, environment drift |
| Temporal contract regression | dedicated contract test suite | Edit to profile, cost engine, or scenario resolver that silently breaks directional or recency semantics |
| FIFO violation introduced | `ProfileFifoRepairGateTest` | Profile compression or merge that reorders temporal cost curve |
| Null dereference risk | SpotBugs `NP_*` | Missing null guard on Optional.get() or API response field |
| Unchecked cast | SpotBugs `BC_UNCONFIRMED_CAST` | Raw type usage or unsafe generics in store access |
| Resource leak | SpotBugs `OBL_*` | Stream or connection not closed in finally block |
| Thread-safety violation | SpotBugs `IS2_INCONSISTENT_SYNC` | Mixed synchronized and unsynchronized access |
| Unused declared dependency | `mvn dependency:analyze` | Import added during development, not cleaned up |
| Dead code | Checkstyle `UnusedVariable`, `EmptyBlock` | Leftover from refactor |

### 8.2 Python

| Flaw class | Detection | Common cause |
|---|---|---|
| Test failure | `pytest` red | Logic error in learning/calibration path |
| Type error | `mypy` | Missing annotation, `Optional` not guarded, wrong return type |
| E4 calibration drift | `test_forecast_calibration.py` | Signed movement logic changed, periodicity flattened |
| Recency probe failure | `test_temporal_attribute_probe.py` | Recency weight accidentally zeroed or averaged away |
| Ablation determinism failure | `test_ablation_determinism.py` | Stochastic element crept into training path |

### 8.3 Frontend

| Flaw class | Detection | Common cause |
|---|---|---|
| Cross-app import | `check-import-boundaries.js` | maps-app importing from traffic-app or vice versa |
| URL string outside endpoints.js | `eslint` custom rule | Inline path string in component or hook |
| localStorage / sessionStorage usage | `eslint` custom rule | Persisted state attempted in component |
| useEffect without cleanup | `react-hooks/exhaustive-deps` | Missing AbortController or clearInterval return |
| Context value not memoised | `eslint` custom rule or manual scan | Object literal in provider value prop |
| Prop drilling beyond one level | manual scan flagged by agent | Component receiving props it only passes down |
| Missing temporal badge | manual scan | ResultMetadata missing one of the four v14 display rules |
| TaroHttpClient bypassed | `eslint` custom rule | `fetch()` called directly in component |

### 8.4 API Contract

| Flaw class | Detection | Common cause |
|---|---|---|
| Response field removed | `check-api-contract.js` diff vs schema snapshot | Backend refactor dropped field |
| Response field type changed | `check-api-contract.js` | int → string, nullable → required |
| New required request field | `check-api-contract.js` | Backend added validation without frontend update |
| Endpoint path changed | `check-api-contract.js` | Java `@RequestMapping` updated |

---

## 9. Scan Script Specifications

Scripts that do not yet exist must be created by the agent as TOOLING fixes.
Each script's required contract is defined here so the agent knows what to build.

### 9.1 `scripts/check-import-boundaries.js`

**Input**: walks `taro-frontend/maps-app/` and `taro-frontend/traffic-app/`
recursively, reads all `.js` and `.jsx` import statements.

**Rules**:
- Any import from `maps-app/` found inside `traffic-app/` is a violation.
- Any import from `traffic-app/` found inside `maps-app/` is a violation.
- Any import from outside `taro-frontend/` (except `node_modules`) is a violation.

**Output**: one line per violation:
```
VIOLATION | <file> | imports | <imported path>
```

Exit code 0 if no violations, 1 if any violations.

### 9.2 `scripts/check-api-contract.js`

**Input**: reads `docs/api/contract_snapshot.json` (the canonical schema) and
compares it to live responses from `$TARO_BASE_URL` (env var, default
`http://localhost:8080`).

**Checks**: for every endpoint in the snapshot, make a request with a known
valid payload, compare response shape field-by-field against the snapshot.

**Output**:
```
DRIFT | <endpoint> | field <name> | expected <type> got <type>
DRIFT | <endpoint> | field <name> | missing in response
DRIFT | <endpoint> | field <name> | newly required in request
OK    | <endpoint>
```

If `$TARO_BASE_URL` is unreachable, emit `SKIP | <endpoint> | server unreachable`
and exit 0 (do not fail the build for an offline server).

If `docs/api/contract_snapshot.json` does not exist, create it from the
current live responses and exit 0 with message:
`SNAPSHOT CREATED — baseline established at cycle <N>`.

### 9.3 ESLint Custom Rules (in `taro-frontend/.eslintrc.js`)

The following rules must be present. If any are absent, the agent creates them
as a TOOLING fix.

| Rule ID | What it catches |
|---|---|
| `taro/no-inline-api-path` | string literals matching `/api/v1` anywhere outside `endpoints.js` |
| `taro/no-direct-fetch` | `fetch(` called outside `TaroHttpClient.js` |
| `taro/no-browser-storage` | `localStorage`, `sessionStorage`, `indexedDB` usage |
| `taro/context-value-memo` | JSX `value={{` pattern inside a `.Provider` component (value object not wrapped in useMemo) |

---

## 10. Agent Session Initialisation

At the start of every agent session (process start, not cycle start):

```
Step 1  Read this file completely.

Step 2  Read docs/agent/cycle.txt.
        If absent: create it with value 1.
        If present: continue from the stored cycle number.

Step 3  Read docs/agent/fixed_flaws.log.
        If absent: create it empty.

Step 4  Read docs/agent/false_positives.md.
        If absent: create it empty.

Step 5  Check if a HALT file exists at docs/agent/HALT_*.md.
        If one exists: read it. Do not start the loop.
        Emit to stdout: "HALTED at cycle <N>. Reason: <code>.
        Human action required. See docs/agent/HALT_<code>_cycle_<N>.md."
        Exit.

Step 6  If no HALT file: begin the loop at SCAN.
```

The agent never assumes a clean state. It always inherits the state left by
the previous session.

---

## 11. Agent Rules

1. **The loop does not stop on finding a flaw.** Finding flaws is success,
   not failure. The loop stops only on the conditions in Section 7.4.

2. **One finding per cycle, always.** Batching multiple fixes into one cycle
   makes root cause analysis on regressions impossible.

3. **Verification is not optional.** A fix that skips verification is not
   a fix — it is a gamble. Every cycle runs the full suite in Section 6.1.

4. **The temporal contracts run every cycle.** They are not expensive relative
   to the cost of a silent regression. They run even for STYLE fixes.

5. **The agent writes before it acts.** The pick record is written before the
   fix starts. The fix record is written before VERIFY runs. This ensures a
   partial cycle leaves a readable audit trail.

6. **Suppression is not fixing.** Adding an annotation to silence a tool is
   a new flaw of type ARCH, not a fix for the original finding.

7. **The codebase owns the ground truth.** If a scan tool reports a finding
   and the code looks fine on inspection, the burden of proof is on the agent
   to demonstrate it is a false positive. Not the other way around.

8. **Scope is the finding boundary.** The agent does not reorganise surrounding
   code, improve adjacent logic, or opportunistically refactor while fixing.
   Clean separation of concerns between cycles is what makes the audit log
   useful.

9. **The agent is not a gatekeeper.** It does not decide whether a feature is
   correct. It decides whether the code is structurally sound, tested,
   type-safe, and consistent with the contracts defined in the TARO v14 roadmap
   and the frontend architecture defined in the frontend AGENTS.md.

10. **IDLE is not done.** A clean scan means the surface scan found nothing.
    It does not mean the codebase is flaw-free. The deeper scan in Section 7.3
    runs before any IDLE declaration is accepted.