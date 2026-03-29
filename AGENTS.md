# TARO Frontend Build Agent Guidance — v2

This file supersedes `AGENTS.md` v1 entirely for frontend work.

The repo already contains backend closure and audit records under
`docs/verification/`, `docs/training/`, and `docs/audit/`.
For frontend work, treat those records as the current backend baseline.
Do not re-run backend verification or training protocols unless the user
explicitly asks for backend re-validation.

The sole task defined here is building two frontend applications with
correct layered architecture. A standalone monolithic `.jsx` file is
explicitly rejected as an output format.

This frontend guidance is additive to the current TARO backend repository.
It does not replace the Java runtime, Python learning pipeline, or the current
verification and audit docs already present in this repo. The frontend must be
built on top of the live API surface that exists under `src/main/java/org/Aayush/api`,
with explicit placeholder handling for any planned endpoint that is not yet implemented.

---

## 1. Architecture Overview

### 1.1 Repository Layout

```
<repo root>
├── src/                            # existing Java + Python backend
├── docs/                           # existing verification / training / audit docs
├── pom.xml
├── pyproject.toml
└── taro-frontend/                  # new additive frontend workspace
    ├── package.json                # workspace root
    ├── vite.config.js              # shared Vite config
    │
    ├── shared/                     # cross-app shared layer (no app-specific logic)
    │   ├── api/
    │   │   ├── TaroHttpClient.js   # single fetch wrapper, base URL, interceptors
    │   │   ├── endpoints.js        # all URL constants
    │   │   └── transforms.js       # backend-envelope -> frontend-view-model normalisation
    │   ├── hooks/
    │   │   ├── usePolling.js       # generic interval poller with pause/resume
    │   │   ├── useAbortableFetch.js # fetch + AbortController lifecycle
    │   │   └── useEventLog.js      # append-only bounded event log
    │   ├── context/
    │   │   └── ConfigContext.jsx   # API base URL, poll interval, shared settings
    │   └── utils/
    │       ├── time.js             # relative/absolute time formatting
    │       ├── duration.js         # seconds -> "Xm Ys"
    │       └── geo.js              # GeoJSON helpers, bbox computation
    │
    ├── maps-app/                   # App 1: current TARO operational surface
    │   └── ...
    └── traffic-app/                # App 2: planned traffic/infra console
        └── ...
```

### 1.2 Dependency Rules

Enforced by directory structure and reflected in all import paths.

```
shared/        → no dependencies on maps-app/ or traffic-app/
maps-app/      → may import from shared/ only
traffic-app/   → may import from shared/ only
maps-app/      → must NOT import from traffic-app/
traffic-app/   → must NOT import from maps-app/
```

Circular imports across this boundary are a build error. The agent must not
create any cross-app import regardless of how convenient it would be.

### 1.3 Three-Layer State Model

Both apps use the same state model. Every piece of state must be assigned to
exactly one layer before any component is written.

```
Layer 1 — Server state     : managed by custom hooks (useAbortableFetch,
                              usePolling, useTrafficStream). Raw API responses
                              never stored in raw useState at component level.

Layer 2 — Shared UI state  : React Context. Contexts hold derived or
                              cross-component state only — not raw responses.
                              No Redux, no Zustand.

Layer 3 — Local UI state   : useState inside leaf components (form field
                              values, hover states, collapsed panels, selected
                              row index). Must not escape the component.
```

### 1.4 TaroHttpClient Contract

All network calls in both apps go through `shared/api/TaroHttpClient.js`.

`TaroHttpClient` is a factory, not a singleton:
`createTaroClient({ baseUrl, timeoutMs, onLog })`.

It must:
- Attach `Content-Type: application/json` to all POST/DELETE bodies.
- Attach `X-Taro-Caller-Id` to all caller-scoped route, matrix, retrieval, and
  feedback requests. This header is required by the live backend.
- Reject non-`application/json` responses with a typed `ApiShapeError`.
- Return `{ ok: true, data } | { ok: false, error: ApiError }`.
  No exceptions propagate beyond the client boundary.
- Accept an `AbortSignal` on every call.
- Invoke `onLog({ method, url, status, durationMs })` after every response.
  `useEventLog` in both apps subscribes to this callback.

One `TaroHttpClient` instance is created at each app root and injected via
`ConfigContext`. Components receive it from context, never via module import.

### 1.5 Backend Alignment Rules

The frontend must distinguish three kinds of API contract:

1. Implemented now in the Java backend
2. Planned but not yet implemented
3. Frontend-normalized view models produced by `shared/api/transforms.js`

Current implemented backend families:

- route evaluation and retained retrieval
- matrix evaluation and retained retrieval
- feedback ingestion
- health
- metrics
- governance
- retained-result purge admin

Current planned-but-missing backend families:

- quarantine registry / mutation API
- topology validate / publish API
- traffic stream / routing rule / rate limit / ingestion status APIs

Rule:

- If an endpoint is implemented now, frontend code must use the live endpoint.
- If an endpoint is planned but not implemented, the frontend must render an
  explicit placeholder state and log the gap.
- `transforms.js` is the only place allowed to reshape the current backend
  envelopes into the frontend model expected by components.

---

## 2. App 1 — TARO Maps (Full Operational Surface)

### 2.1 Scope

App 1 is the primary TARO product interface. It covers route planning, map
visualization, **and** the full operational surface: feedback telemetry
submission, health monitoring, quarantine management, topology reload, and
audit log. All Phase F functionality (F1, F2, F3) lives in this app.
Nothing is deferred to App 2.

Backend alignment for the current repo:

- route planning, retained result retrieval, feedback, health, metrics, and
  governance are live-backed today
- quarantine management and topology reload controls are planned UI surfaces,
  but must render explicit "API not yet available" placeholders until the
  matching backend endpoints exist

### 2.2 AppShell Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  AlertBar (full width, shown only when system is degraded)      │
├────────────────────┬────────────────────────────────────────────┤
│  Sidebar (320px)   │  MapCanvas (fills remaining space)         │
│  ┌──────────────┐  │                                            │
│  │ RequestPanel │  │   RouteLayer    — blue / orange dashed     │
│  ├──────────────┤  │   ScenarioLayer — gray thin lines          │
│  │ ScenarioBundle│  │   AsymmetryLayer — red thick segments     │
│  ├──────────────┤  │   QuarantineLayer — pulsing red circles    │
│  │ ResultMeta   │  │                                            │
│  ├──────────────┤  │                                            │
│  │ [tab strip]  │  │                                            │
│  ├──────────────┤  │                                            │
│  │ Feedback     │  │                                            │
│  │ Health       │  │                                            │
│  │ Quarantine   │  │                                            │
│  │ Reload       │  │                                            │
│  │ Log          │  │                                            │
│  └──────────────┘  │                                            │
└────────────────────┴────────────────────────────────────────────┘
```

The tab strip switches the bottom section of the sidebar only.
`RequestPanel`, `ScenarioBundle`, and `ResultMetadata` are always visible
regardless of active tab.

### 2.3 RouteContext Contract

Single source of truth for the active route result.

```
{
  status:             "idle" | "loading" | "success" | "error",
  result:             RouteResultViewModel | null,
  selectedScenarioId: string | null,
  error:              ApiError | null,
  submitRoute:        (RouteRequest) => void,
  retrieveById:       (string) => void,
  selectScenario:     (string | null) => void
}
```

`submitRoute` cancels any in-flight request before starting a new one.
All map layer components subscribe to `RouteContext` directly — they do not
receive route data as props.

`RouteResultViewModel` is a frontend-normalized model built in `transforms.js`
from the live `RouteApiResponse` + retained summary/detail envelopes.

### 2.4 MapContext Contract

Holds the Leaflet map instance and a mutable layer registry. Only `MapCanvas`
writes to it.

```
{
  mapRef:         React.MutableRefObject<L.Map | null>,
  registerLayer:  (id: string, layer: L.Layer) => void,
  removeLayer:    (id: string) => void,
  fitBounds:      (bounds: L.LatLngBounds) => void
}
```

### 2.5 OperationsContext Contract

Drives Health, Quarantine, and Reload panels. Composes `usePolling` for health
and quarantine. Derives `healthStatus` from response fields.

```
{
  health:           HealthResponse | null,
  metrics:          MetricsResponse | null,
  governance:       GovernanceResponse | null,
  healthStatus:     "ok" | "degraded" | "unknown",
  quarantine:       QuarantineEntry[] | "unavailable",
  addQuarantine:    (QuarantinePayload) => Promise<Result<QuarantineEntry> | CapabilityUnavailable>,
  topologyStatus:   TopologyStatusResponse | "unavailable",
  validateTopology: () => Promise<Result<ValidationResult> | CapabilityUnavailable>,
  publishTopology:  () => Promise<Result<PublishResult> | CapabilityUnavailable>,
  pollPaused:       boolean,
  setPollPaused:    (boolean) => void
}
```

`OperationsContext` must compose `/health`, `/metrics`, and `/governance`.
`healthStatus` is derived from:

- `metrics.alerts.overallStatus` when `/metrics` is available
- otherwise `health.alertStatus` / `health.reloadHealth`
- otherwise `"unknown"`

`AlertBar` reads `healthStatus` from this context and only renders when
`healthStatus === "degraded"`.

`CapabilityUnavailable` is the normalized placeholder contract for planned
frontend surfaces whose backend endpoint is not present yet:

```json
{
  "ok": false,
  "code": "ENDPOINT_UNAVAILABLE",
  "endpoint": "/api/v1/quarantine"
}
```

### 2.6 FeedbackForm Pre-Population Rule

When a route result is active in `RouteContext`, `FeedbackForm` must
auto-fill `resultSetId` from `RouteContext.result.resultSetId` and make
that field read-only. The user must explicitly clear the active result
to submit feedback for an arbitrary ID.

### 2.7 Temporal Display Rules (Non-Optional)

`ResultMetadata` must implement all four badges. These are visual encodings
of v14 hard-blocker attributes — not optional decorations.

| Condition | Badge text | Colour |
|---|---|---|
| `result.quarantineActive === true` | `⚡ Live incident influencing route` | yellow |
| `result.asymmetricSegments.length > 0` | `⚠ Asymmetric corridor — N segments` | amber |
| `result.optimalityProbability < 0.5` | `⚠ No dominant route — high uncertainty` | orange |
| departure time > now + 72h | `ℹ Far-horizon — P90 uncertainty wider` | blue |

If a field is absent in the response the badge must not render.
These badges are computed from the normalized frontend model, not from a
fictional flat backend envelope.

### 2.8 useLeaflet Hook Contract

```
useLeaflet(): { ready: boolean }
```

Checks `window.L` first. Injects script tag only if absent. Uses a
module-level `let scriptPromise = null` to deduplicate concurrent calls
under React 18 strict-mode double-mount. `MapCanvas` renders a loading
placeholder until `ready === true`.

### 2.9 Stage Breakdown for Maps App

#### Stage M1: Shared Layer Foundation
- **Objective**: All shared utilities, API client, and base hooks are in place
  and independently testable before any app code is written.
- **Functional Requirements**:
    - `createTaroClient({ baseUrl, timeoutMs, onLog })` factory
    - All API path strings in `endpoints.js` only
    - `usePolling(fetcher, intervalMs, { paused })` — generically reusable
    - `useAbortableFetch(fetcher)` → `{ status, data, error, execute, abort }`
    - `useEventLog(maxEntries)` → `{ entries, append, clear }`
    - `ConfigContext` providing `{ apiBaseUrl, pollIntervalMs, maxLogEntries, client }`
    - `time.js`: `relativeTime(iso)`, `absoluteTime(iso)`
    - `duration.js`: `formatSeconds(n) → "Xm Ys"`
    - `geo.js`: `geoJsonToBounds(LineString) → L.LatLngBounds`
- **Non-Functional Requirements**:
    - `TaroHttpClient` must be pure — no module-level side effects, no singletons
    - All hooks safe under React 18 strict-mode double-mount
    - Zero dependency on Leaflet, Recharts, or app-specific code
- **Interface Contracts**:
    - `createTaroClient(config): TaroHttpClient`
    - `TaroHttpClient.get(path, signal): Promise<Result<T>>`
    - `TaroHttpClient.post(path, body, signal): Promise<Result<T>>`
    - `TaroHttpClient.delete(path, signal): Promise<Result<T>>`
    - `usePolling(fetcher, interval, opts): { data, status, pause, resume }`
    - `useAbortableFetch(fetcher): { data, status, error, execute }`
    - `useEventLog(max): { entries, append, clear }`
- **Test Equivalence Classes**:
    - Client: 2xx success, 4xx error, 5xx error, network failure, abort mid-flight,
      non-JSON response body, timeout, `onLog` invoked on every response
    - `usePolling`: normal interval cycle, pause mid-flight request, resume,
      unmount during active poll (no setState after unmount)
    - `useAbortableFetch`: concurrent calls (last-write wins), abort before
      response arrives, error shape matches `ApiError`
    - `useEventLog`: append under max, append at max (oldest dropped), clear,
      `entries` is stable reference when nothing changes
- **Dependencies**: None

#### Stage M2: Context Layer
- **Objective**: All four context providers are defined with contracts, initial
  state, and action/reducer patterns. No component code yet.
- **Functional Requirements**:
    - `ConfigContext`: reads initial `apiBaseUrl` from `window.__TARO_CONFIG__`
      or query param `?api=`; falls back to `http://localhost:8080`
    - `RouteContext`: internally drives `useAbortableFetch`; `submitRoute`
      cancels in-flight before re-submitting
    - `MapContext`: provides `mapRef` (MutableRefObject) and an internal
      `Map<string, L.Layer>` registry
    - `OperationsContext`: composes `usePolling` for `/health`, `/metrics`,
      and `/governance`; quarantine and reload actions are capability-gated
      and must resolve to `CapabilityUnavailable` without issuing a network call
      when endpoint probes show the API is absent
    - `healthStatus` derived from `/metrics.alerts.overallStatus` when present,
      otherwise from `/health.alertStatus` / `/health.reloadHealth`
- **Non-Functional Requirements**:
    - Every context value object is `useMemo`-stabilised
    - Every action function is `useCallback`-stabilised
    - `OperationsContext` polling stops cleanly on provider unmount
    - `RouteContext` abort on new request is synchronous before the new fetch starts
- **Interface Contracts**:
    - `RouteContext.submitRoute(req: RouteRequest): void`
    - `RouteContext.retrieveById(id: string): void`
    - `RouteContext.selectScenario(id: string | null): void`
    - `MapContext.registerLayer(id: string, layer: L.Layer): void`
    - `MapContext.removeLayer(id: string): void`
    - `OperationsContext.addQuarantine(p: QuarantinePayload): Promise<Result<QuarantineEntry> | CapabilityUnavailable>`
    - `OperationsContext.validateTopology(): Promise<Result<ValidationResult> | CapabilityUnavailable>`
    - `OperationsContext.publishTopology(): Promise<Result<PublishResult> | CapabilityUnavailable>`
- **Test Equivalence Classes**:
    - `RouteContext`: submit while idle, submit while loading (cancel confirmed),
      retrieve by ID, select scenario with active result, select with null result
    - `OperationsContext`: poll cycle runs, pause suspends both pollers,
      `healthStatus` derives from metrics first then health fallback,
      capability-unavailable quarantine and reload actions short-circuit without fetch
- **Dependencies**: M1

#### Stage M3: MapCanvas and Layer Components
- **Objective**: Leaflet map mounts correctly; all four overlay layers render
  from context without prop threading.
- **Functional Requirements**:
    - `MapCanvas` uses `useLeaflet`; renders `<div>Loading map…</div>` until ready
    - `MapCanvas` provides `MapContext` to its subtree
    - `RouteLayer` subscribes to `RouteContext.result`; renders expected winner
      (blue `#2563EB`, weight 5) and robust winner (orange `#EA580C`, weight 5, dashed)
    - `ScenarioLayer` renders per-scenario gray polylines; selected scenario
      (`selectedScenarioId`) becomes weight 4 with a scenario-index colour
    - `AsymmetryLayer` renders `result.asymmetricSegments` as red `#DC2626` thick segments
    - `QuarantineLayer` renders `result.quarantineZones` as pulsing circles via a
      single `<style>` tag injected into `document.head` on first mount
    - After every new result `MapContext.fitBounds` is called with the union bbox
      of all visible polylines
- **Non-Functional Requirements**:
    - Every layer component calls `MapContext.removeLayer(id)` in its cleanup
    - Leaflet layer data is updated via property mutation on the existing layer
      object — not by creating a new layer on every state change
    - `MapCanvas` container div height must be set via `style={{ height: "100%" }}`,
      not Tailwind (avoids Leaflet 0px height bug)
- **Interface Contracts**:
    - `useLeaflet(): { ready: boolean }`
    - All layer components: zero props (all data from context)
- **Test Equivalence Classes**:
    - Map mount: first mount, strict-mode double-mount, `window.L` already present
    - `RouteLayer`: null result, expected winner only, both winners, result update
      replaces previous polylines without ghost layers
    - `AsymmetryLayer`: zero segments, multiple segments, result cleared removes layer
    - `QuarantineLayer`: zero zones, multiple overlapping zones
- **Dependencies**: M2

#### Stage M4: Sidebar Route and Scenario Components
- **Objective**: `RequestPanel`, `ScenarioBundle`, and `ResultMetadata` drive
  `RouteContext` correctly with full validation and temporal badge logic.
- **Functional Requirements**:
    - `RequestPanel`: all fields controlled; departure defaults to `now + 5m`;
      "Route Now" → `RouteContext.submitRoute`; "Retrieve by ID" → `retrieveById`;
      loading spinner on `status === "loading"` (inline SVG); error banner on
      `status === "error"` showing `error.message`; disabled submit when fields empty
    - `ScenarioBundle`: sorted descending by probability; click → `selectScenario`;
      selected row has `bg-blue-50`; probability bar proportional fill
    - `ResultMetadata`: ETA/P90 formatted via `duration.js`; all four temporal
      badges from Section 2.7; "Copy resultSetId" → `navigator.clipboard.writeText`
- **Non-Functional Requirements**:
    - No `alert()` calls anywhere; all validation surfaces inline
    - Temporal badges computed purely from response fields; no hardcoded strings
- **Interface Contracts**: All three components have zero props (context only)
- **Test Equivalence Classes**:
    - `RequestPanel`: valid submit, empty-field block, loading state disables button,
      API error displayed, result clears error
    - `ScenarioBundle`: zero scenarios, one scenario, five scenarios, select and deselect
    - `ResultMetadata`: all four badge conditions true simultaneously, none true,
      partial (two of four), `optimalityProbability` exactly 0.5 (boundary)
- **Dependencies**: M2, M3

#### Stage M5: Operational Panels
- **Objective**: All five sidebar tabs (Feedback, Health, Quarantine, Reload, Log)
  are functional against the live TARO API where supported, with explicit
  capability placeholders where the backend surface is not implemented yet.
- **Functional Requirements**:
    - **Feedback**: `FeedbackForm` pre-populates from `RouteContext` (see 2.6);
      POST → `/api/v1/feedback/route/results/{resultSetId}/outcome`; resets on success; `FeedbackLog` shows last 50
      feedback events from `useEventLog` filtered by type `"feedback"`
    - **Health**: `HealthPanel` driven primarily by `OperationsContext.metrics`
      with `/health` and `/governance` as supporting metadata; metric cards
      use the live low-cardinality metrics fields that already exist in the backend
    - **Quarantine**: until `/api/v1/quarantine` exists, `QuarantinePanel`
      renders a capability placeholder with no mock data
    - **Reload**: until topology validate/publish endpoints exist, `ReloadPanel`
      renders governance and active-topology posture from `/api/v1/governance`
      and `/api/v1/health`, plus an explicit "controls unavailable" placeholder
    - **Log**: `AuditLog` renders `useEventLog.entries`; row expand/collapse;
      "Export" triggers client-side JSON download via temporary `<a>` element;
      "Clear" → `useEventLog.clear`
- **Non-Functional Requirements**:
    - Health poller continues when Health tab is not active (lives in
      `OperationsContext`, not in the panel component)
    - `FeedbackForm` reset is synchronous after successful API response
    - `ConfirmModal` must trap focus within the modal while open
    - Log export is pure client-side — no server round-trip
- **Interface Contracts**: All panel components have zero props
- **Test Equivalence Classes**:
    - Feedback: pre-populated ID (from active result), manual ID, submit success
      (form resets), API error (form retained), `FeedbackLog` 50-entry overflow
    - Health: normal metric render, alert state from `/metrics.alerts.overallStatus`,
      chart buffer rolls at 60 samples, poll pause/resume
    - Quarantine: capability unavailable placeholder, no silent mock mode
    - Reload: governance posture visible, control-unavailable placeholder visible,
      no publish API call attempted when the endpoint is absent
    - Log: export produces valid JSON, clear resets entries to [], row expand/collapse
- **Dependencies**: M2, M4

---

## 3. App 2 — TARO Internet Traffic Handler

### 3.1 Scope

App 2 is the HTTP infrastructure console for the TARO service cluster. It
monitors and controls network-level traffic: request rates per endpoint,
latency distributions, error rates, live in-flight requests, load-balancing
routing rules, per-caller rate limit quota, and the E1–E5 data ingestion
pipeline status.

It does not duplicate any route planning, map visualization, feedback form,
quarantine management, or health metrics from App 1. Those belong to App 1.

Backend alignment for the current repo:

- none of the App 2 traffic-specific endpoints currently exist in the Java API
- App 2 therefore starts as an architecture-first shell with explicit endpoint
  capability placeholders
- App 2 must not fabricate traffic, routing-rule, rate-limit, or ingestion data

### 3.2 TrafficContext Contract

```
{
  requestFeed:  RequestEvent[],       // rolling 500-entry circular buffer
  aggregates:   Map<string, EndpointAggregate>,  // derived, not fetched
  feedStatus:   "live" | "paused" | "error",
  pause:        () => void,
  resume:       () => void
}
```

`EndpointAggregate: { rps: number, p50Ms: number, p99Ms: number, errorRate: number }`

`aggregates` is recomputed inside the context on every feed update over a
rolling 60-second window. It is never a separate API call.

### 3.3 InstanceContext Contract

```
{
  instances:        TaroInstance[],
  routingRules:     RoutingRule[],
  selectedInstance: TaroInstance | null,
  select:           (instanceId: string) => void,
  addRule:          (RoutingRulePayload) => Promise<r>,
  deleteRule:       (ruleId: string) => Promise<r>
}
```

`deleteRule` is optimistic: remove the row immediately, roll back on API error.

### 3.4 AppShell Layout

```
┌────────────────────────────────────────────────────────────────────┐
│  TARO Traffic Handler                  [● LIVE]  [Pause]  [⚙]     │
├────────────────┬───────────────────────────────────────────────────┤
│  Sidebar       │  Main Panel                                        │
│  ───────────   │                                                    │
│  Traffic       │  [active tab content fills this space]            │
│  Routing       │                                                    │
│  Rate Limits   │                                                    │
│  Ingestion     │                                                    │
└────────────────┴───────────────────────────────────────────────────┘
```

`[● LIVE]` / `[⏸ PAUSED]` badge in header drives `TrafficContext.pause/resume`.

### 3.5 Tab: Traffic

Four sub-panels in a 2-column CSS grid.

**RequestRateChart**: Recharts `LineChart`, one `<Line>` per TARO endpoint.
X-axis: last 120 seconds (one data point per second). Y-axis: RPS.
Legend items are togglable via click.

**LatencyHeatmap**: `<table>`-based (no external library). Rows = endpoints;
columns = ten 10-second buckets (last 100s). Cell `backgroundColor` interpolates
white → red for P99 0ms → ≥1000ms via inline `style`.

**ErrorRatePanel**: Recharts `BarChart`, stacked bars for 4xx and 5xx counts,
last 12 × 10s buckets per endpoint.

**ActiveRequestTable**: in-flight requests from `requestFeed` where
`completedAt === null`. Elapsed column re-renders every 500ms via a
`setInterval` scoped to that column only — not the whole table. Rows
stalled > 30s highlighted red.

### 3.6 Tab: Routing

**InstanceMap**: Leaflet map via the shared `useLeaflet` hook. Instance markers
coloured by `instance.health` (green/yellow/red). Click → `InstanceContext.select`.

**RoutingRuleTable**: table of `InstanceContext.routingRules`. Delete requires
inline confirmation text (not a modal). `deleteRule` is optimistic.

**RuleEditor** (collapsible form): endpoint glob, caller prefix, time window
HH:MM–HH:MM, target instance dropdown, weight 0–100, priority. Calls
`InstanceContext.addRule`. Client-side validation before submit.

### 3.7 Tab: Rate Limits

**RateLimitPanel**: polled from `GET /api/v1/ratelimits`. Columns: caller ID,
endpoint, window, quota, used, remaining, reset-in. Row click selects for gauge.

**RateLimitGauge**: horizontal fill for selected row `used / quota`.
Colour thresholds: green < 70%, yellow < 90%, red ≥ 90%.
CSS `transition` on fill width. Memoised — re-renders only on row selection change.

**ThrottleEventLog**: filters `TrafficContext.requestFeed` for `throttled === true`.
Memoised filter keyed on feed length. Auto-scrolls on new entry via `useRef`.
Capped at 100 entries displayed.

### 3.8 Tab: Ingestion

**IngestionPipelinePanel**: vertical stage flow, E1 → E2 → E3 → E4 → E5.
Polled via `useIngestionPoll`. Connector lines between rows via CSS
`border-left` on a centred absolutely-positioned pseudo-element.

**StageStatusRow**: badge colours: `IDLE` gray, `RUNNING` blue pulse
(CSS keyframe), `DONE` green, `FAILED` red. Columns: stage, name, status,
last-run (relative), records-in, records-out, duration.

**IngestionThroughputChart**: Recharts `LineChart`, one line per stage,
records/sec, rolling 60-sample buffer (same pattern as `LatencyChart` in App 1).

If any stage is `FAILED`, the `AppShell` in App 2 shows a red alert bar:
`⚠ Ingestion pipeline failure — E{n} stage failed.`

### 3.9 Stage Breakdown for Traffic App

#### Stage T1: Traffic Data Layer
- **Objective**: `TrafficContext` provides a live rolling request feed and
  derived per-endpoint aggregates. Feed source is SSE if available,
  polling fallback otherwise.
- **Current backend note**:
    - The current repo does not expose these traffic endpoints yet.
    - Implement endpoint capability detection and an explicit unavailable
      placeholder before implementing live feed logic.
- **Functional Requirements**:
    - `useTrafficStream` probes `GET /api/v1/traffic/stream`: if response is
      `Content-Type: text/event-stream` use `EventSource`; else fall back to
      1-second polling of `GET /api/v1/traffic/recent`
    - `requestFeed` is a bounded circular buffer capped at 500 `RequestEvent` objects
    - `aggregates` recomputed O(n) over the 60-second rolling window on every
      feed append
    - `pause()` closes the `EventSource` or suspends the polling interval;
      `resume()` reconnects or restarts
- **Non-Functional Requirements**:
    - `EventSource` must be closed on context unmount
    - No events dropped during SSE→polling fallback transition
    - Aggregate recomputation must not block the main thread for > 16ms at 500
      events (single O(n) pass, no sorting)
- **Interface Contracts**:
    - `useTrafficStream(client): { feed, append, status }`
    - `TrafficContext.pause(): void`
    - `TrafficContext.resume(): void`
- **Test Equivalence Classes**:
    - SSE: connect, receive events, unmount closes stream
    - Polling fallback: interval fires, dedup on `requestId`
    - Buffer: append at capacity (oldest evicted), pause stops new entries,
      resume resumes without timestamp gap
    - Aggregates: empty feed, single endpoint only, all endpoints, 60s window
      rollover evicts old events from aggregate
- **Dependencies**: M1

#### Stage T2: Instance and Routing Layer
- **Objective**: `InstanceContext` provides the node registry and rule CRUD.
  `InstanceMap` renders the Leaflet topology.
- **Current backend note**:
    - The current repo does not expose instance or routing-rule APIs yet.
    - This stage must start with capability placeholders and no fabricated data.
- **Functional Requirements**:
    - Polls `GET /api/v1/instances` every `pollIntervalMs`
    - `addRule` → `POST /api/v1/routing/rules`
    - `deleteRule` → `DELETE /api/v1/routing/rules/{id}`, optimistic removal
    - `InstanceMap` uses shared `useLeaflet` hook
    - Marker colour updates without full map reinit on poll cycle change
    - `RoutingRuleTable` delete: inline confirm string (not modal), roll back row
      on API error
- **Non-Functional Requirements**:
    - Rule deletion must roll back on failure within the same event loop tick
    - Marker `L.CircleMarker` colour updated via `setStyle`, not by removing and
      recreating the marker
- **Interface Contracts**:
    - `InstanceContext.addRule(p: RoutingRulePayload): Promise<Result<r>>`
    - `InstanceContext.deleteRule(id: string): Promise<Result<r>>`
    - `InstanceContext.select(id: string): void`
- **Test Equivalence Classes**:
    - Instance poll: empty list, mixed health colours, poll update changes marker
      colour in-place
    - Rule add: valid payload, weight out-of-range (0–100), invalid time window format
    - Rule delete: success, API error rolls back row, concurrent deletes (no double delete)
- **Dependencies**: M1, T1

#### Stage T3: Rate Limit and Throttle Layer
- **Objective**: `RateLimitPanel` shows per-caller quota and `ThrottleEventLog`
  surfaces throttle events from the live feed.
- **Current backend note**:
    - The current repo does not expose rate-limit APIs yet.
    - This stage must render explicit unavailable states until the backend adds them.
- **Functional Requirements**:
    - Rate limit data from `GET /api/v1/ratelimits` polled separately from feed
    - `RateLimitGauge` re-renders only on selected row change (React.memo with
      `{ used, quota }` props)
    - `ThrottleEventLog` filters `requestFeed` for `throttled === true`;
      memoised filter keyed on `feed.length`
    - Auto-scroll via `scrollTop = scrollHeight` on the log container ref
      whenever a new throttle event arrives
- **Non-Functional Requirements**:
    - `RateLimitGauge` fill width CSS transition duration 200ms
    - `ThrottleEventLog` filter must not re-render the full table on every
      unrelated feed update
- **Interface Contracts**:
    - `RateLimitGauge` props: `{ used: number, quota: number }`
    - `ThrottleEventLog` props: none (reads `TrafficContext`)
- **Test Equivalence Classes**:
    - Gauge: 0%, 69% (green), 70% (yellow boundary), 89%, 90% (red boundary), 100%+
    - Throttle log: empty feed, feed with no throttle events, feed with events,
      auto-scroll on new entry, display capped at 100 entries
- **Dependencies**: T1

#### Stage T4: Ingestion Pipeline Panel
- **Objective**: E1–E5 pipeline status is visualised with throughput charting
  and pipeline failure surfaces in the app-level alert.
- **Current backend note**:
    - The current repo does not expose ingestion-status APIs yet.
    - This stage must start in capability-placeholder mode without mock throughput.
- **Functional Requirements**:
    - `useIngestionPoll` drives polling of `GET /api/v1/ingestion/status`
    - `RUNNING` badge uses CSS `@keyframes` pulse (blue); injected as a single
      `<style>` tag on first mount of the pipeline panel
    - Alert condition: `stages.some(s => s.status === "FAILED")` drives a
      `useState` in `AppShell`; clears when all stages leave FAILED state
    - `IngestionThroughputChart` 60-sample rolling buffer; same implementation
      pattern as `LatencyChart` in App 1 (do not duplicate logic — share the
      rolling-buffer logic from `shared/utils/` if needed)
- **Non-Functional Requirements**:
    - Pipeline poll continues when Ingestion tab is inactive
    - Alert bar clears automatically when stages recover without user action
- **Interface Contracts**:
    - `useIngestionPoll(client): { stages: StageStatus[], pollStatus }`
    - `StageStatusRow` props: `{ stage: StageStatus }`
- **Test Equivalence Classes**:
    - All IDLE, all DONE, one FAILED (alert fires), FAILED then DONE (alert clears),
      RUNNING with non-zero throughput, zero records in/out
- **Dependencies**: M1, T1

---

## 4. TARO HTTP API Contract

### 4.1 Implemented Maps / Core Endpoints

```
POST /api/v1/route
GET  /api/v1/route/results/{resultSetId}/summary
GET  /api/v1/route/results/{resultSetId}/detail
POST /api/v1/matrix
GET  /api/v1/matrix/results/{resultSetId}/summary
GET  /api/v1/matrix/results/{resultSetId}/detail
GET  /api/v1/health
GET  /api/v1/metrics
GET  /api/v1/governance
POST /api/v1/feedback/route/results/{resultSetId}/outcome
POST /api/v1/feedback/matrix/results/{resultSetId}/outcome
POST /api/v1/admin/retained-results/purge
```

All caller-scoped endpoints above require:

```
X-Taro-Caller-Id: <stable caller id>
```

### 4.2 Planned Frontend Endpoints (Not Yet Implemented In Repo)

```
GET    /api/v1/quarantine
POST   /api/v1/quarantine
GET    /api/v1/topology/status
POST   /api/v1/topology/validate
POST   /api/v1/topology/publish
GET    /api/v1/traffic/stream          (SSE preferred)
GET    /api/v1/traffic/recent          (polling fallback)
GET    /api/v1/instances
POST   /api/v1/routing/rules
DELETE /api/v1/routing/rules/{id}
GET    /api/v1/ratelimits
GET    /api/v1/ingestion/status
```

These endpoints are architectural targets for the frontend, not live contracts
in the current repo. Components using them must remain capability-gated until
the backend implements them.

### 4.3 Error Shape

All error responses follow:
```json
{
  "code": "INVALID_REQUEST",
  "status": 400,
  "message": "human-readable text",
  "path": "/api/v1/route",
  "timestamp": "2026-03-29T00:00:00Z"
}
```

`TaroHttpClient` surfaces this as `ApiError { status, code, message }` in the
`{ ok: false, error }` branch. Unknown fields are ignored (permissive parsing).

### 4.4 Route API Envelope (Implemented Now)

```json
{
  "resultSetId": "string",
  "retained": true,
  "expiresAt": "2026-03-29T00:10:00Z",
  "topologyVersion": {
    "topologyVersion": "topo-api"
  },
  "summary": {
    "resultSetId": "string",
    "scenarioBundleId": "bundle-api",
    "scenarioCount": 2,
    "expectedRoute": {
      "expectedCost": 874.0,
      "p90Cost": 1090.0,
      "optimalityProbability": 0.71,
      "dominantScenarioId": "incident_persists"
    },
    "robustRoute": {
      "expectedCost": 910.0,
      "p90Cost": 1130.0
    }
  }
}
```

`transforms.js` must convert this envelope plus retained `detail` payloads into
frontend view models used by `ResultMetadata`, `ScenarioBundle`, and map layers.
Frontend components must not assume the backend directly returns the flattened
"FutureRouteResponse" shape.

---

## 5. Agent Build Protocol

### 5.1 Build Order

```
Step 1    Read this file completely before creating any file.

Step 2    Create the full directory tree from Section 1.1.
          Create all directories, even empty ones.

Step 3    Implement Stage M1 (shared layer).
          No app code until M1 is complete and contains no parse errors.

Step 4    Implement Stage M2 (Maps context layer).

Step 5    Implement Stages M3, M4, M5 in parallel (no write conflict).

Step 6    Implement Stage T1 (Traffic data layer).
          Depends on M1 only — may begin as soon as M1 is done.

Step 7    Implement Stages T2, T3, T4 in parallel.

Step 8    Write docs/frontend/FRONTEND_BUILD_RECORD.md (see 5.3).
```

### 5.2 Code Quality Rules

1. **No URL strings outside `endpoints.js`.** Import the constant; never
   inline a path string.

2. **No `localStorage`, `sessionStorage`, or `IndexedDB` anywhere.**

3. **No prop drilling beyond one component boundary.** If data crosses two
   levels, the receiving component reads from context directly.

4. **Every `useEffect` that opens a network resource returns a cleanup.**
   `AbortController` for fetch. `EventSource.close()` for SSE.
   `clearInterval` for polling timers.

5. **Context values are `useMemo`-stabilised. Action functions are
   `useCallback`-stabilised.** Unstabilised context values cause O(n) cascade
   re-renders.

6. **`TaroHttpClient` is instantiated once per app root** inside the
   `ConfigContext` provider. All hooks receive it from `useContext(ConfigContext)`
   — never via `import`.

7. **Recharts imports are named.** `import { LineChart, Line } from "recharts"`.
   No `window.Recharts`.

8. **The shared `useLeaflet` hook is the only Leaflet CDN loader.**
   Neither app reimplements it.

9. **If a TARO API endpoint does not yet exist**, render a clearly labelled
   placeholder and record the gap. Do not silently mock data.

### 5.3 Frontend Build Record

After all stages complete, write `docs/frontend/FRONTEND_BUILD_RECORD.md`:

```
Frontend Build Record — TARO v2
================================
Date        : <ISO date>
Agent       : <session identifier>

Shared Layer (M1)
  Status    : COMPLETE | PARTIAL | FAILED
  Files     : list all created shared/ files

App 1 — TARO Maps
  Status    : COMPLETE | PARTIAL | FAILED
  Stages    : M2=[status], M3=[status], M4=[status], M5=[status]
  Contexts  : RouteContext, MapContext, OperationsContext — [COMPLETE | PARTIAL]
  Temporal badges : recency=[YES/NO] direction=[YES/NO]
                    uncertainty=[YES/NO] granularity=[YES/NO]
  Phase F coverage:
    Feedback     : YES | NO
    Health       : YES | NO
    Quarantine   : YES | NO
    Reload       : YES | NO
    Audit log    : YES | NO
  Known gaps     : <list or "none">

App 2 — TARO Traffic Handler
  Status    : COMPLETE | PARTIAL | FAILED
  Stages    : T1=[status], T2=[status], T3=[status], T4=[status]
  Feed source    : SSE | polling fallback | not determined
  Ingestion panel: YES | NO
  Known gaps     : <list or "none">

Dependency violations : NONE | <list any cross-app imports>
Missing endpoints     : <list any endpoints that returned 404 or are stubbed>
```

---

## 6. Agent Rules For This File

1. **Standalone monolithic files are a build failure.** Output must match
   the directory tree in Section 1.1 exactly.

2. **Do not re-run backend verification or training pipeline protocols as part
   of frontend execution unless the user explicitly asks for backend re-validation.**

3. **Stage M1 and M2 must be fully complete before any component file
   in `maps-app/` is started.** Similarly T1 before any `traffic-app/`
   component.

4. **App 2 must not re-implement any feature already in App 1.**
   Overlap is a design error. Remove it from App 2.

5. **This file is authoritative for the frontend build.** If any other
   document contradicts a requirement here for the frontend scope, this
   file wins.

6. **This file does not override backend truth outside frontend scope.**
   Live API controllers, request/response contracts, and runtime/learning docs
   remain canonical for what the backend actually supports today.
