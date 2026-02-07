# Stage 7 Live Overlay: Implementation Notes

Date: 2026-02-06  
Scope: `src/main/java/org/Aayush/routing/overlay/LiveOverlay.java`, `src/main/java/org/Aayush/routing/overlay/LiveUpdate.java`

## 1. Purpose

Stage 7 adds runtime traffic overrides without mutating the base graph.  
The overlay is a bounded, thread-safe key-value layer:

- Key: `edgeId`
- Value: `(speedFactor, validUntilTicks)`

This lets the cost engine apply live conditions while preserving deterministic base model behavior.

## 2. Canonical Semantics

### Speed factor

- `speedFactor == 0.0f`: edge is blocked.
- `0.0f < speedFactor <= 1.0f`: slowdown factor.
- Values outside `[0,1]` are rejected at `LiveUpdate` creation.

### Expiry boundary

An entry is expired when:

`validUntilTicks <= nowTicks`

So `validUntilTicks` is an exclusive bound.

### Lookup states

`LiveOverlay.lookup(edgeId, nowTicks)` returns one of:

- `MISSING`: no entry exists.
- `EXPIRED`: entry exists but is expired for `nowTicks`.
- `BLOCKED`: entry active with `speedFactor = 0.0`.
- `ACTIVE`: entry active with `0 < speedFactor <= 1.0`.

### Penalty projection

`LiveOverlay.livePenaltyMultiplier(...)` maps state to cost multiplier:

- `MISSING` or `EXPIRED` -> `1.0f`
- `BLOCKED` -> `Float.POSITIVE_INFINITY`
- `ACTIVE` -> `1.0f / speedFactor`

## 3. Data Structure and Concurrency Model

### 3.1 Core storage

- `ConcurrentHashMap<Integer, Entry> entries`
- `Entry` is immutable (`speedFactor`, `validUntilTicks`)

Writers replace whole entries atomically; readers never observe partial writes.

### 3.2 Read/write strategy

- Reads (`lookup`) are lock-free.
- Writes (`applyBatch`, `clear`, `runScheduledSweep`) are serialized by `ReentrantLock`.

This keeps lookup latency low while preserving predictable mutation behavior.

## 4. TTL Normalization (`LiveUpdate`)

Two relative-TTL constructors exist:

1. `fromRelativeTtl(..., nowTicks, ttlTicks)` where `ttlTicks` is already in engine ticks.
2. `fromRelativeTtl(..., nowTicks, ttlValue, inputUnit, engineUnit)` for unit conversion.

Conversion uses `TimeUtils.normalizeToEngineTicks(...)`, then computes:

`validUntilTicks = nowTicks + normalizedTtlTicks`

with `Math.addExact(...)` to detect overflow.

## 5. Capacity and Cleanup Strategy

The overlay has a hard cap: `maxLiveOverrides`.

When a new edge arrives at capacity, behavior depends on `CapacityPolicy`:

- `REJECT_BATCH`
  - Pre-checks distinct new edge IDs for the full batch.
  - Rejects non-expired ingest if capacity cannot hold the batch.
- `EVICT_EXPIRED_THEN_REJECT`
  - Attempts expired cleanup first, then rejects if still full.
- `EVICT_OLDEST_EXPIRY`
  - Attempts expired cleanup first.
  - If still full, evicts the entry with the smallest `validUntilTicks`.

### Hybrid cleanup paths

- Write-path cleanup: `writeCleanupBudget` entries per batch start.
- Scheduled cleanup: `runScheduledSweep(nowTicks, maxRemovals)`.
- Optional read cleanup: one best-effort remove when lookup sees expired data.

This limits long pauses while still controlling memory growth.

## 6. Complexity Expectations

- Lookup: expected `O(1)`
- Upsert existing edge: expected `O(1)`
- Insert at capacity:
  - `REJECT_BATCH`: `O(k)` pre-check (`k = batch size`)
  - Expired cleanup / oldest-expiry scan: up to `O(n)` (`n = overlay size`)

These costs are bounded by configured capacity and cleanup budget.

## 7. Test Coverage

Primary suite: `src/test/java/org/Aayush/routing/overlay/LiveOverlayTest.java`

Covered behavior:

- input validation (`edgeId`, `speedFactor`, finite values)
- TTL normalization across mixed units
- lookup state semantics
- penalty projection semantics
- capacity policy behavior for all policies
- scheduled sweep budget behavior
- concurrent read/write sanity under workload

## 8. Integration Notes

- Keep Stage 10 rule unchanged: `effective = base * temporal / speedFactor`; blocked edge means `INF`.
- Feed overlay updates with normalized engine ticks to avoid cross-unit drift.
- Use `BatchApplyResult` counters for ingest observability (accept/reject/eviction telemetry).
