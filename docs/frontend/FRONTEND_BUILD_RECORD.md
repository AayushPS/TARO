Frontend Workspace Build Record
===============================

Date: 2026-03-29
Workspace: `taro-frontend/`

Summary
-------

The repository now includes an additive frontend workspace aligned to the current
TARO backend architecture. It follows the `AGENTS.md` split:

- `shared/` for transport, hooks, transforms, and shared configuration
- `maps-app/` for the live operational UI
- `traffic-app/` for the planned traffic/infra console surface

Current status
--------------

- Shared layer: COMPLETE
- Maps app: COMPLETE against currently implemented backend contracts
- Traffic app: PARTIAL by design; capability-gated placeholders only
- IntelliJ plugin manifest: ADDED via `.idea/externalDependencies.xml`

Backend-aligned live frontend capabilities
------------------------------------------

- route submission
- retained route summary/detail retrieval
- route feedback submission
- health polling
- metrics polling
- governance polling
- future-route geometry rendering from backend `pathPoints`

Capability-gated placeholder surfaces
-------------------------------------

- quarantine registry / mutation APIs
- topology validate / publish APIs
- traffic stream / recent traffic APIs
- instance registry APIs
- routing rule APIs
- rate-limit APIs
- ingestion status APIs

Architecture notes
------------------

- `shared/api/TaroHttpClient.js` is the single HTTP boundary
- `shared/api/transforms.js` is the only backend-envelope normalization layer
- `maps-app/` and `traffic-app/` depend on `shared/` only
- no cross-app imports are allowed
- the frontend does not fabricate missing backend data; unavailable capabilities
  render explicit placeholder states instead

Verification commands
---------------------

```bash
cd taro-frontend
npm test
npm run build
```
