# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

**Build:**
```bash
mvn package -DskipTests
```

**Run locally (local profile — face-tagging points to localhost:8081):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Run with debug attach (JDWP port 5005):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

**Docker build:**
```bash
docker build -t moments-backend .
docker run -p 8080:8080 moments-backend
```

There are no automated tests in this repository (the `target/test-classes` directory exists but contains no test sources). `mvn package -DskipTests` is the standard build.

**Swagger UI:** http://localhost:8080/swagger-ui.html (v3 API docs at `/v3/api-docs`)

## Architecture

This is a Spring Boot 3.2 REST API (Java 17, Maven) that backs the Moments photo-sharing platform. It manages events, photos (called "moments"), users, and face tagging. Production runs on Google Cloud Run.

**Three services form the full local stack** (see `docs/LOCAL_DEV.md` and `scripts/run-local-stack.sh`):
- This backend — port 8080
- Python/FastAPI face-tagging service (`momentsFaceTagging`) — port 8081
- Vite admin UI (`moments.github.io`) — port 5173

**Package structure:**
```
com.moments/
  config/       Spring config beans (security, GCS, Firebase, async, Drive, credentials)
  controller/   REST endpoints
  dao/          Firestore data-access interfaces + impl/
  filter/       JWT auth filter
  models/       DTOs and domain objects
  service/      Business logic
  utils/        JwtUtil
```

**Data stores:**
- **Firestore** (project `moments-38b77`) — sole database. Collections: `moments`, `events`, `eventRoles`, `likes`, `userProfiles`, `otpVerificationMappings`, `uploadRecords`. No SQL, no JPA.
- **Google Cloud Storage** (bucket `momentslive`) — stores uploaded media. Public URLs served through CDN domain `images.moments.live`. Object key pattern: `events/{eventId}/{sanitizedFilename}` for scoped uploads, `uploads/unscoped/` for cover images, `drive-import/{eventId}/{driveFileId}` for Google Drive imports.

**Credentials / environment split:**
- `app.environment=PROD` → uses Application Default Credentials (Cloud Run service account). Set automatically; no service account JSON needed in production.
- `app.environment=DEV` (default when running locally) → loads `src/main/resources/serviceAccountKey.json`. This file must exist for local runs; it is gitignored.
- The `local` Spring profile (`application-local.properties`) sets `app.environment=DEV` and redirects the face-tagging URL to `http://127.0.0.1:8081`.

## Key Design Decisions

**Authentication is togglable and currently disabled in production.** `auth.enabled=false` in `application.properties` causes `SecurityConfig` to permit all requests. The JWT filter and Firebase auth are wired but not enforced. To enforce auth, set `auth.enabled=true` — it will require JWT on all paths except OTP, auth, and Swagger endpoints. There are two separate auth flows: phone OTP (via MessageCentral, issues in-house JWT) and Firebase ID token exchange (`POST /api/auth/firebase`, issues the same JWT).

**Face tagging is a separate HTTP service**, not embedded. `FaceTaggingService` calls the Python service over HTTP after every moment is saved. Two call modes:
- `@Async` (default for local) — fire-and-forget with exponential backoff retry (up to 5 attempts). Runs on the `taskExecutor` thread pool (core 2, max 5).
- Synchronous (`processMomentsBatchSync`) — used during Google Drive imports on Cloud Run to avoid CPU throttling after the HTTP response ends. `DriveImportProperties` auto-selects synchronous mode when `K_SERVICE` env var is present (Cloud Run indicator).

**Google Drive import is idempotent.** The Firestore moment ID for a Drive-imported file is a deterministic SHA-256 hash of `eventId + driveFileId` (prefix `drv`). The GCS object name is also deterministic (`drive-import/{eventId}/{driveFileId}`). Retries skip files where the moment already exists in Firestore or the GCS blob already exists.

**Firestore pagination is offset-based, not cursor-based.** The DAO fetches all matching documents from Firestore and applies in-memory offset/limit. This means large events incur full collection reads for deeper pages. The admin feed (`source=web`) uses Firestore `startAfter` with a document snapshot anchor (`anchorMomentId`) for true cursor pagination.

**Storage accounting is maintained as an aggregate on the `Event` document.** `Event.aggregatedStorage` (a `MomentMemoryUsage` sub-object) tracks total original, optimised, and thumbnail byte counts. `MomentService.adjustEventStorageForMoment` increments/decrements this on save and delete. The face-tagging service callback (`applyBatchFaceTaggingStorageUpdates`) further adjusts the event aggregate when it writes back optimised/thumbnail sizes after processing.

**Special event IDs have hardcoded behavior:**
- `"123456"` — promotion event: users see only their own moments plus a fixed allowlist of creator IDs.
- `"123457"` — sorted ascending by `creationTime` (all other events sort descending).

**GCS uploads use resumable channels** (256 KB chunks) throughout to avoid heap pressure on large files and "Error writing request body" errors.

**The `creatorRole` field on a moment** is resolved from the `EventRole` collection at save time if not already set. Drive imports hardcode `"Photographer"`. Guests and other roles are written based on the uploading user's role in the event.

**Drive import supports pause/resume.** The client can call `POST /api/files/upload-records/{recordId}/pause` during a running import. The service checks `uploadRecordService.isPauseRequested(rid)` after each batch of 25 files and stops cooperatively. `POST /api/files/upload-records/{recordId}/retrigger` resumes from the same record, skipping already-imported files.

**Canon CR3 RAW files** are handled specially: browsers misreport them as `image/jpeg`. `GoogleCloudStorageService.effectiveImageContentType` stores them as `application/octet-stream` instead. Drive import falls back to `390x844` dimensions for CR3 files when image dimension probing fails.
