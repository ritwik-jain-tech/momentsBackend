# Architecture — momentsBackend

> Auto-derived from codebase knowledge graph (codebase-memory-mcp). 2652 nodes / 7800 edges.
> Hand-written design doc: [`docs/Architecture.md`](docs/Architecture.md). This file = graph-derived structural map.
> Query live graph: `search_graph`, `query_graph`, `trace_path`, `get_architecture`.

## Stack
- **Java** (115 files) + **Spring Boot**, **Maven** (`pom.xml`). Docker (`Dockerfile`).
- Google Cloud Storage (`GoogleCloudStorageService`), Firebase auth, OpenCV (`src/main/resources/opencv`).
- 62 HTTP routes across controllers.

## Package layout (`src/main/java/com/moments/`)
- `controller/` — REST endpoints. e.g. `TeamController` (`/members`, `/tasks`), upload, events, moments controllers.
- `service/` — business logic. `GoogleCloudStorageService` (`uploadFile`, `uploadBytes`, `StoredObject`), face-tagging, event, moment, user, notification services.
- `dao/` — persistence. `UploadRecordDao` (`mergeFields`, `upsertComputerUploadSession`).
- `models/` — DTOs/entities: `Moment`, `MomentsRequest`, `UserProfile`, `ReportRequest`, `FaceTaggingResult` (heavy fan-in — `getMessage` 85, `toString` 12).

## Layers (graph-inferred)
- `main` = entry (outbound only). `pom` = core (high fan-in). API layer = HTTP routes.

## Route groups (of 62)
- **Upload/moments**: `POST /upload`, `/bulk-upload`, `/bulk-upload-moments`, `/bulk-upload-moments-with-details`, `/signed-upload-url`, `/finalize-moments`.
- **Upload records**: `GET /upload-records`, `POST /upload-records/computer-session`, `/{recordId}/pause|cancel|retrigger`.
- **Events/files**: `GET /events/{eventId}/files`, `/events/{eventId}/export`.
- **Google Drive import**: `POST /import-google-drive-folder`.
- **Team**: `POST|GET|PUT|DELETE /members`, `POST|GET /tasks`.

## Key flows (clusters)
- Upload + drive import: `importDriveImageFile`, `saveMoments`, `finalizeMoments`, `bulkUploadFilesAndCreateMomentsWithDetails`, `importGoogleDriveFolder`, `retriggerUploadRecord`, `importFolderAsync`.
- Auth: `linkPhoneToGoogle`, `resolveOrCreateProfile`, `verifyOtp`, `signInWithFirebase`.
- Storage/upload core: `uploadFile`, `effectiveImageContentType`, `uploadBytes`.
- Events: `updateEvent`, `saveEvent`, `addUserToEvent`, `applyEpochMillisToTimes`.
- Team/admin: `createOrUpdateTask`, `createOrUpdateMember`, `getStats` (cohesion 0.98).
- Moments feed: `buildAdminMomentsFeedResponse`, `findMoments`, `getLikedMomentsFeed`.
- Face-tagging storage: `applyBatchFaceTaggingStorageUpdates`, `adjustEventStorageForMoment`, `buildEventStorageSummary`.
- User profiles: `createUserProfile`, `getAllUserProfilesInEventWithRoles`, `createMinimalStudioUser`, `getUserStorageOverview`.

## Related repos
Face recognition offloaded to **momentsFaceTagging** (Python service). Frontends: **moments.github.io** (web), **MomentsApp** (mobile).

## Local dev
`scripts/run-local-stack.sh`, `docs/LOCAL_DEV.md`, `deploy/bucket-cors.json`.
