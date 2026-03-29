import {
  BrowserRouter,
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
} from 'react-router-dom'
import { AdminDashboard } from './pages/AdminDashboard'
import { RouteWorkspace } from './pages/RouteWorkspace'
import { usePersistentState } from './lib/usePersistentState'

type IndexedIds = Record<string, string[]>

export interface ShellContextValue {
  apiBase: string
  callerId: string
  trackedJobIds: string[]
  recentRouteIds: string[]
  setApiBase: (value: string) => void
  setCallerId: (value: string) => void
  trackJob: (jobId: string) => void
  untrackJob: (jobId: string) => void
  rememberRouteResult: (resultSetId: string) => void
}

function AppShell({
  apiBase,
  callerId,
  trackedJobIds,
  recentRouteIds,
  setApiBase,
  setCallerId,
  trackJob,
  untrackJob,
  rememberRouteResult,
}: ShellContextValue) {
  return (
    <div className="app-shell">
      <header className="shell-hero">
        <div className="shell-hero__copy">
          <p className="eyebrow">TARO Control Surface</p>
          <h1>Train, publish, and query one caller-scoped routing system.</h1>
          <p className="hero-text">
            The admin workspace controls telemetry export, retraining, and model
            publication. The route workspace stays thin and only asks for start
            and end points.
          </p>
        </div>
        <div className="shell-hero__controls">
          <label className="field">
            <span>Caller ID</span>
            <input
              value={callerId}
              onChange={(event) => setCallerId(event.target.value)}
              placeholder="caller-a"
            />
          </label>
          <label className="field">
            <span>API Base</span>
            <input
              value={apiBase}
              onChange={(event) => setApiBase(event.target.value)}
              placeholder="/api or http://127.0.0.1:8080"
            />
          </label>
          <p className="field-hint">
            Default development flow: keep this field at `/api`, run Spring
            Boot on `8080`, then run this app on `5173`. Use a full origin only
            when you want the frontend to bypass the proxy.
          </p>
        </div>
      </header>

      <div className="shell-nav">
        <NavLink
          className={({ isActive }) =>
            isActive ? 'shell-nav__link is-active' : 'shell-nav__link'
          }
          to="/admin"
        >
          Admin command deck
        </NavLink>
        <NavLink
          className={({ isActive }) =>
            isActive ? 'shell-nav__link is-active' : 'shell-nav__link'
          }
          to="/query"
        >
          End-user route workspace
        </NavLink>
      </div>

      <main className="shell-main">
        <Outlet
          context={{
            apiBase,
            callerId,
            trackedJobIds,
            recentRouteIds,
            setApiBase,
            setCallerId,
            trackJob,
            untrackJob,
            rememberRouteResult,
          } satisfies ShellContextValue}
        />
      </main>
    </div>
  )
}

function dedupeAndCap(ids: string[], nextId: string): string[] {
  const trimmed = nextId.trim()
  if (!trimmed) {
    return ids
  }
  const deduped = [trimmed, ...ids.filter((existing) => existing !== trimmed)]
  return deduped.slice(0, 8)
}

export default function App() {
  const [apiBase, setApiBase] = usePersistentState('taro.api-base', '/api')
  const [callerId, setCallerId] = usePersistentState('taro.caller-id', 'caller-a')
  const [trackedJobsByCaller, setTrackedJobsByCaller] =
    usePersistentState<IndexedIds>('taro.tracked-jobs', {})
  const [recentRouteIdsByCaller, setRecentRouteIdsByCaller] =
    usePersistentState<IndexedIds>('taro.recent-results', {})

  const callerKey = callerId.trim() || '__anonymous__'
  const trackedJobIds = trackedJobsByCaller[callerKey] ?? []
  const recentRouteIds = recentRouteIdsByCaller[callerKey] ?? []

  const trackJob = (jobId: string) => {
    setTrackedJobsByCaller((current) => ({
      ...current,
      [callerKey]: dedupeAndCap(current[callerKey] ?? [], jobId),
    }))
  }

  const untrackJob = (jobId: string) => {
    setTrackedJobsByCaller((current) => ({
      ...current,
      [callerKey]: (current[callerKey] ?? []).filter((existing) => existing !== jobId),
    }))
  }

  const rememberRouteResult = (resultSetId: string) => {
    setRecentRouteIdsByCaller((current) => ({
      ...current,
      [callerKey]: dedupeAndCap(current[callerKey] ?? [], resultSetId),
    }))
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route
          element={
            <AppShell
              apiBase={apiBase}
              callerId={callerId}
              trackedJobIds={trackedJobIds}
              recentRouteIds={recentRouteIds}
              setApiBase={setApiBase}
              setCallerId={setCallerId}
              trackJob={trackJob}
              untrackJob={untrackJob}
              rememberRouteResult={rememberRouteResult}
            />
          }
        >
          <Route index element={<Navigate replace to="/admin" />} />
          <Route path="/admin" element={<AdminDashboard />} />
          <Route path="/query" element={<RouteWorkspace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
