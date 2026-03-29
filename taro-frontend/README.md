# TARO Frontend

This workspace is the source frontend for TARO's current client training-to-serving slice.
`npm run build` emits the production app into Spring Boot's
`src/main/resources/static/`.

## Surfaces

- `/admin`
  - caller-scoped CSV upload
  - target/feature column selection and temporal trait selection
  - training job create / start / complete / publish
  - admin notifications
  - active-model metadata
  - health / metrics / governance snapshot

- `/query`
  - thin route UI that only asks for start and end
  - blocked until a caller-scoped model is published
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

## Embedded Spring Boot

Build the frontend into Spring's static resources:

```bash
cd /home/aayushps/projects/TARO/taro-frontend
npm run build
```

Then run Spring Boot and open:

- `http://127.0.0.1:8080/admin`
- `http://127.0.0.1:8080/query`

## Checks

```bash
npm test
npm run lint
npm run build
```

## Current Gaps

- no auth flow yet because caller/project identity is still header-based
- no tenant-specific runtime activation in the Java route path yet
- no Java-to-Python training orchestration yet; completion/publish is still control-plane state
