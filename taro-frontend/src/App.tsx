import type { ReactNode } from 'react'
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useParams,
} from 'react-router-dom'
import { StatusPill } from './components/StatusPill'
import { usePersistentState } from './lib/usePersistentState'
import { AdminDashboard } from './pages/AdminDashboard'
import { RouteWorkspace } from './pages/RouteWorkspace'

type IndexedIds = Record<string, string[]>

function dedupeAndCap(ids: string[], nextId: string): string[] {
  const trimmed = nextId.trim()
  if (!trimmed) {
    return ids
  }
  const deduped = [trimmed, ...ids.filter((existing) => existing !== trimmed)]
  return deduped.slice(0, 8)
}

function normalizeWorkspaceId(value: string | undefined, fallback: string): string {
  const trimmed = (value ?? '').trim()
  return trimmed || fallback
}

function displayWorkspaceName(value: string): string {
  return value.replace(/[-_]+/g, ' ')
}

function PublicShell({
  workspaceId,
  children,
}: {
  workspaceId: string
  children: ReactNode
}) {
  return (
    <div className="app-shell app-shell--public">
      <header className="shell-hero shell-hero--public">
        <div className="shell-hero__copy">
          <p className="eyebrow">TARO Route Guide</p>
          <h1>Route planning for travelers, not operators.</h1>
          <p className="hero-text">
            This public planner only asks for places people recognize. Training,
            publishing, and operational controls live in a separate admin
            workspace.
          </p>
        </div>
        <div className="shell-hero__controls">
          <StatusPill label={`Workspace ${workspaceId}`} tone="neutral" />
          <p className="callout">
            Public workspace: <strong>{displayWorkspaceName(workspaceId)}</strong>
          </p>
          <p className="field-hint">
            Travelers can enter city names, hubs, or road segments without ever
            seeing internal node ids.
          </p>
        </div>
      </header>

      <main className="shell-main">{children}</main>
    </div>
  )
}

function AdminShell({
  apiBase,
  callerId,
  publicPlannerPath,
  setApiBase,
  setCallerId,
  children,
}: {
  apiBase: string
  callerId: string
  publicPlannerPath: string
  setApiBase: (value: string) => void
  setCallerId: (value: string) => void
  children: ReactNode
}) {
  return (
    <div className="app-shell app-shell--admin">
      <header className="shell-hero shell-hero--admin">
        <div className="shell-hero__copy">
          <p className="eyebrow">TARO Admin Workspace</p>
          <h1>Training, publication, and oversight stay completely separate.</h1>
          <p className="hero-text">
            Operators manage datasets and model rollout here. End users should
            only visit the public planner URL for their workspace.
          </p>
        </div>
        <div className="shell-hero__controls">
          <label className="field">
            <span>Workspace ID</span>
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
          <div className="admin-link-card">
            <span>Public planner URL</span>
            <strong>{publicPlannerPath}</strong>
            <Link className="button button--ghost" to={publicPlannerPath}>
              Preview public planner
            </Link>
          </div>
        </div>
      </header>

      <main className="shell-main">{children}</main>
    </div>
  )
}

function PublicPlannerRoute({
  apiBase,
  defaultWorkspaceId,
  rememberRouteResult,
}: {
  apiBase: string
  defaultWorkspaceId: string
  rememberRouteResult: (workspaceId: string, resultSetId: string) => void
}) {
  const { workspaceId } = useParams()
  const resolvedWorkspaceId = normalizeWorkspaceId(workspaceId, defaultWorkspaceId)

  return (
    <PublicShell workspaceId={resolvedWorkspaceId}>
      <RouteWorkspace
        apiBase={apiBase}
        callerId={resolvedWorkspaceId}
        rememberRouteResult={(resultSetId) =>
          rememberRouteResult(resolvedWorkspaceId, resultSetId)
        }
      />
    </PublicShell>
  )
}

export default function App() {
  const [apiBase, setApiBase] = usePersistentState('taro.api-base', '/api')
  const [callerId, setCallerId] = usePersistentState('taro.caller-id', 'caller-a')
  const [trackedJobsByCaller, setTrackedJobsByCaller] =
    usePersistentState<IndexedIds>('taro.tracked-jobs', {})
  const [, setRecentRouteIdsByCaller] =
    usePersistentState<IndexedIds>('taro.recent-results', {})

  const normalizedCallerId = callerId.trim() || 'caller-a'
  const trackedJobIds = trackedJobsByCaller[normalizedCallerId] ?? []
  const publicPlannerPath = `/plan/${encodeURIComponent(normalizedCallerId)}`

  const trackJob = (workspaceId: string, jobId: string) => {
    setTrackedJobsByCaller((current) => ({
      ...current,
      [workspaceId]: dedupeAndCap(current[workspaceId] ?? [], jobId),
    }))
  }

  const untrackJob = (workspaceId: string, jobId: string) => {
    setTrackedJobsByCaller((current) => ({
      ...current,
      [workspaceId]: (current[workspaceId] ?? []).filter(
        (existing) => existing !== jobId,
      ),
    }))
  }

  const rememberRouteResult = (workspaceId: string, resultSetId: string) => {
    setRecentRouteIdsByCaller((current) => ({
      ...current,
      [workspaceId]: dedupeAndCap(current[workspaceId] ?? [], resultSetId),
    }))
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route index element={<Navigate replace to={publicPlannerPath} />} />
        <Route path="/plan" element={<Navigate replace to={publicPlannerPath} />} />
        <Route
          path="/plan/:workspaceId"
          element={
            <PublicPlannerRoute
              apiBase={apiBase}
              defaultWorkspaceId={normalizedCallerId}
              rememberRouteResult={rememberRouteResult}
            />
          }
        />
        <Route path="/query" element={<Navigate replace to={publicPlannerPath} />} />
        <Route
          path="/admin"
          element={
            <AdminShell
              apiBase={apiBase}
              callerId={callerId}
              publicPlannerPath={publicPlannerPath}
              setApiBase={setApiBase}
              setCallerId={setCallerId}
            >
              <AdminDashboard
                apiBase={apiBase}
                callerId={normalizedCallerId}
                trackedJobIds={trackedJobIds}
                trackJob={(jobId) => trackJob(normalizedCallerId, jobId)}
                untrackJob={(jobId) => untrackJob(normalizedCallerId, jobId)}
              />
            </AdminShell>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}
