# Local development (backend + face-tagging)

## Ports (suggested)

| Service        | Port | Command / notes |
|----------------|------|-----------------|
| Spring Boot    | 8080 | `local` profile → `face.tagging.service.url` on 8081 |
| Face-tagging   | 8081 | Your Python/FastAPI (or other) service |
| Vite (admin UI)| 5173 | `npm run dev` in `moments.github.io` (uses `.env.development`) |

## Bring everything up (three terminals)

1. **Face-tagging** on `8081` (however you usually start that repo).
2. **Backend** (from `momentsBackend`):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

3. **Frontend** (from `moments.github.io`):

```bash
npm run dev
```

Open the URL Vite prints (e.g. `http://127.0.0.1:5173`).

## Debug Spring Boot from the IDE

**Option A — Launch:** open `momentsBackend` in VS Code / Cursor, run **Spring Boot (local profile)** from Run and Debug (uses `.vscode/launch.json`).

**Option B — Attach:** start the JVM with JDWP, then use **Attach to Spring Boot (JDWP 5005)**:

```bash
cd momentsBackend
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

Set breakpoints in controllers/services, then trigger the flow from the Vite app.

## Override face-tagging URL

Without editing `application-local.properties`:

```bash
export FACE_TAGGING_SERVICE_URL=http://127.0.0.1:5000
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

`application-local.properties` sets `face.tagging.service.url=http://127.0.0.1:8081` by default. Production `application.properties` is unchanged and still points at Cloud Run when you do **not** use the `local` profile.

## CORS

`/api/files` allows `http://localhost:*` and `http://127.0.0.1:*` so uploads from Vite work. Global CORS in `SecurityConfig` already permits local origins.

## Firebase / GCP

Local runs still use your `serviceAccountKey.json` and Firestore project from `application.properties` unless you override. There is no separate “mock” database in this setup.

## Google Drive import

**Local (`application-local.properties`):** Drive uses **`google.drive.prefer-service-account=true`** and `src/main/resources/serviceAccountKey.json`. Share the Drive folder with that JSON’s **`client_email`** (Viewer is enough).

If you insist on an **API key** instead, set **`google.drive.prefer-service-account=false`** in `application-local.properties` and **`unset GOOGLE_DRIVE_API_KEY`** first — env vars override the file. Keys restricted to **HTTP referrers** fail from the JVM (no browser); use **None** or **IP** restrictions in Google Cloud, or use the service account.

Production can still use **`GOOGLE_DRIVE_API_KEY`** / **`GOOGLE_DRIVE_CREDENTIALS_PATH`** in `application.properties` (see env placeholders there).
