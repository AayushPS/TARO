# TARO Frontend

This workspace is the frontend for TARO's current client training-to-serving slice.

## Surfaces

- `/admin`
  - telemetry export preview
  - retraining job create / start / complete / publish
  - active-model metadata
  - health / metrics / governance snapshot

- `/query`
  - thin route UI that only asks for start and end
  - retained result lookup
  - Expected ETA, Robust / P90, and alternatives display
  - route feedback submission

## Run

Start the backend first:

```bash
cd /home/aayushps/projects/TARO
mvn spring-boot:run
```

Then start the frontend:

```bash
cd /home/aayushps/projects/TARO/taro-frontend
npm install
npm run dev
```

Open:

- `http://127.0.0.1:5173/admin`
- `http://127.0.0.1:5173/query`

The default `API Base` is `/api`, which works with the Vite proxy to `http://127.0.0.1:8080`.

## Checks

```bash
npm test
npm run lint
npm run build
```

## Current Gaps

- no dataset upload UI yet because the backend intake API is not implemented
- no auth flow yet because caller/project identity is still header-based
- no tenant-specific runtime activation in the Java route path yet
