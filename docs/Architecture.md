# momentsBackend — Architecture

Backend design reference for the Moments platform. Pairs with the root `CLAUDE.md` (commands,
key design decisions) and `docs/LOCAL_DEV.md` (running the full local stack).

## 1. Overview

`momentsBackend` is a **Spring Boot 3.2 / Java 17** REST API (Maven) backing two products that
share one data store:

- **Moments.live** — consumer guest app for events (couples + guests share/view photos).
- **studio.moments.live** — B2B studio dashboard for photographers/media agencies (projects,
  storage, team management).

It manages **events**, **moments** (photos/videos), **users**, **event roles**, **face tagging**,
and the **agency team board**. Production runs on **Google Cloud Run** (`moments-38b77`).

```
HTTP clients ─▶ Spring controllers ─▶ services ─▶ DAOs ─▶ Firestore
                                          │
                                          ├─▶ Google Cloud Storage (media, CDN: images.moments.live)
                                          ├─▶ Face-tagging service (Python/FastAPI, separate Cloud Run)
                                          ├─▶ Firebase Auth (Google/email token verification)
                                          └─▶ MessageCentral (phone OTP)
```

## 2. Module map (`com.moments`)

| Package | Responsibility |
|---------|----------------|
| `config/` | Spring beans: `SecurityConfig` (JWT toggle + CORS), `FirestoreConfig`, `FirebaseConfig`, `CloudStorageConfig`, `GoogleCredentialsConfig`, `AsyncConfig` (taskExecutor), `HttpClientConfig`, `SwaggerConfig`, `DriveImportProperties`. |
| `controller/` | REST endpoints (`/api/...`). One controller per domain. |
| `service/` | Business logic. Controllers stay thin; services own orchestration. |
| `dao/` + `dao/impl/` | Firestore data access. Interface + `@Repository` impl per collection. |
| `models/` | DTOs / domain objects (Firestore POJOs need a no-arg ctor + getters/setters). |
| `filter/` | `JwtAuthenticationFilter` (runs only when `auth.enabled=true`). |
| `utils/` | `JwtUtil`, `IdentityUtils`. |

## 3. Data stores

**Firestore** (project `moments-38b77`) is the sole database — no SQL/JPA. Collections:

| Collection | Doc ID | Notes |
|------------|--------|-------|
| `UserProfile` | numeric `userId` (counter) | One per human; deduped by phone/email/firebaseUid (§5). |
| `Counters` | `UserProfileCounter` | Atomic transaction-incremented `lastUserId`. |
| `events` | 6-digit `eventId` | `userIds`, `groomSide`/`brideSide`, `teamMemberIds`, `aggregatedStorage`, `guestApp`. |
| `EventRole` | `eventId_userId` | Per-event role; legacy `roleName` + structured `roleType`/`agencyRole` (§6). |
| `moments` | moment id (Drive imports: `drv` + SHA-256 of `eventId+driveFileId`) | `creatorRole` resolved from `EventRole`. |
| `teamMembers` | auto id | Agency roster (§7). `agencyId` = owner `userId`. |
| `teamTasks` | auto id | Agency task board (§7). |
| `likes`, `otpVerificationMappings`, `uploadRecords` | — | Engagement, OTP state, Drive/computer upload tracking. |

**Google Cloud Storage** (bucket `momentslive`, CDN `images.moments.live`): object keys
`events/{eventId}/{file}`, `uploads/unscoped/`, `drive-import/{eventId}/{driveFileId}`. Uploads use
resumable 256 KB channels to bound heap usage.

## 4. Request flows

**Event create** (`POST /api/event` → `EventService.saveEvent`): assigns a 6-digit id, defaults
times, merges `creatorId` + `teamMemberIds` into `userIds`, persists, then grants the creator an
**AGENCY** `EventRole` (legacy `roleName="admin"`).

**Moment save** (`MomentService`): resolves `creatorRole` from `EventRole` unless preset (Drive
imports hardcode `Photographer`), stores the moment, adjusts `Event.aggregatedStorage`, then calls
the **face-tagging service** over HTTP — `@Async` fire-and-forget with exponential-backoff retry
locally, synchronous during Cloud Run Drive imports (`K_SERVICE` present) to avoid post-response
CPU throttling. The face service later writes back optimized/thumbnail sizes.

**Admin feed**: cursor pagination via Firestore `startAfter` (`anchorMomentId`); filters by
`creatorRole` — which is why the role taxonomy below stays backward-compatible.

**Team board** (`TeamController`, §7): agency-scoped CRUD for members and tasks + a stats endpoint.

## 5. Identity model — one human, one profile

`userId` is a **numeric counter** doc-id (`UserProfileDaoImpl.getNextUserId`, atomic Firestore
transaction). It is *not* the phone/email — those are **natural keys** used for deduplication so a
person who signs in via different methods is never duplicated.

`UserProfileService.resolveOrCreateProfile(firebaseUid, email, phone, name)` is the single source
of truth, used by every auth path. It resolves in order **firebaseUid → email → phone**
(`UserProfileDaoImpl.findByFirebaseUid/findByEmailId/findByPhoneNumber`), **backfills** any missing
identity key onto the match, and creates a minimal profile only when nothing matches. Inputs are
normalized via `IdentityUtils` (email lowercased; phone reduced to last 10 digits).

## 6. Auth flows

Two flows, both minting the **same in-house JWT** whose **subject is always the numeric `userId`**
(`JwtUtil`):

- **Google / email (Firebase)** — `POST /api/auth/firebase` with a Firebase `idToken`
  (+ optional Google `accessToken`). `FirebaseAuthService` verifies the token, extracts
  email/name/phone (phone from token claims or the Google People API), then delegates to
  `resolveOrCreateProfile`. **Agency/studio users always use Gmail.**
- **Phone OTP** — `POST /api/otp/send` then `/verify` (MessageCentral). `OTPService.verifyOtp`
  only confirms verification; `OTPController` issues the JWT against the resolved `userId`. (OTP
  never mints a phone-subject token.)

**Authorization toggle:** `SecurityConfig` reads `auth.enabled` (default `false`). When `false`
all requests are permitted; when `true` the `JwtAuthenticationFilter` enforces JWT on all paths
except OTP/auth/swagger and the currently-public guest/admin paths. CORS allows
`studio.moments.live`, `*.github.io`, and localhost.

## 7. Event roles & agency team

**Event role taxonomy** (`EventRole`): with respect to an event a user is one of
`EventRoleType { COUPLE, GUEST, AGENCY }`. AGENCY members carry an
`AgencyRole { CAMERAMAN, EDITOR, REVIEWER, RETOUCHER, MANAGER }`.

The change is **additive**: the legacy free-form `roleName` string is preserved (the moments feed
filters on it) while `roleType`/`agencyRole` are layered on. `EventRoleService` keeps both in sync:

- `createOrUpdateEventRole(eventId, userId, roleName)` — legacy path; derives `roleType` via
  `EventRoleType.fromLegacy(...)` (and `agencyRole` when the name is an agency sub-role).
- `createOrUpdateEventRole(eventId, userId, roleType, agencyRole)` — structured path; writes the
  legacy `roleName` via `legacyName(...)`.
- `POST /api/event/roles/bulk` accepts either form per item (`roleType` preferred, `roleName`
  fallback). `getRoleName` is unchanged for downstream consumers.

**Agency team** (`teamMembers` / `teamTasks`, `TeamController` at `/api/team`) is **agency-wide**:
`agencyId` = the studio owner's `userId`. Members have a `role` from the same agency vocabulary;
tasks are a JIRA-style board (`TODO/IN_PROGRESS/IN_REVIEW/DONE`, priority, assignee, optional
linked `eventId`). `GET /api/team/stats` returns per-status counts and per-member completion.

## 8. Configuration & environment

| Key | Default | Purpose |
|-----|---------|---------|
| `app.environment` | `PROD` | `DEV` loads `serviceAccountKey.json`; `PROD` uses ADC (Cloud Run SA). |
| `auth.enabled` | `false` | Toggles JWT enforcement. |
| `jwt.secret` / `jwt.expiration` | env / 30d | HS256 signing + token TTL (seconds). |
| `spring.cloud.gcp.firestore.project-id` | `moments-38b77` | Firestore project. |
| `face.tagging.service.url` | Cloud Run URL | Python face service (local profile → `127.0.0.1:8081`). |
| `messagecentral.*` | — | OTP provider credentials. |
| `spring.servlet.multipart.max-file-size` | 100MB | Upload cap. |

The `local` Spring profile (`application-local.properties`) sets `app.environment=DEV` and points
face-tagging at localhost.

## 9. Deployment

Multi-stage `Dockerfile` (Maven build → `eclipse-temurin:17-jdk` runtime). The runtime entrypoint
is shell-form so Cloud Run's injected `$PORT` is honored (`--server.port=${PORT:-8080}`). Pushing
to the default branch (`main`) auto-deploys via the repo's Cloud Build trigger. Local build:
`mvn package -DskipTests`; run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

## 10. Conventions

- All endpoints return `BaseResponse { message, status, data }`.
- Services throw `ExecutionException`/`InterruptedException` from Firestore; controllers map them to
  HTTP via try/catch and SLF4J logging (no `System.out`/`System.err`).
- Firestore POJOs: no-arg constructor + getters/setters; convert NumPy/native types before write.
- New domains follow `model → Dao → DaoImpl(@Repository) → Service(@Service) → Controller`.
