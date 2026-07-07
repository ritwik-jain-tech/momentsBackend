package com.moments.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moments.dao.UploadRecordDao;
import com.moments.models.GoogleDriveImportRequest;
import com.moments.models.UploadRecord;

@Service
public class UploadRecordService {

    private static final Logger log = LoggerFactory.getLogger(UploadRecordService.class);

    @Autowired
    private UploadRecordDao uploadRecordDao;

    public String createStartedForDriveImport(String userId, String eventId, String creatorName, String driveLink)
            throws ExecutionException, InterruptedException {
        UploadRecord r = new UploadRecord();
        r.setUserId(userId);
        r.setEventId(eventId);
        r.setCreatorName(creatorName != null && !creatorName.isBlank() ? creatorName.trim() : "Photographer");
        r.setDriveLink(driveLink);
        r.setSource(UploadRecord.SOURCE_GOOGLE_DRIVE);
        r.setTotalCount(0);
        r.setProgress(0);
        r.setFailedCount(0);
        r.setStatus(UploadRecord.STATUS_STARTED);
        r.setErrorMessage(null);
        r.setPauseRequested(Boolean.FALSE);
        String id = uploadRecordDao.create(r);
        log.info("UploadRecord {} STARTED user={} event={}", id, userId, eventId);
        return id;
    }

    private static final int MAX_SESSION_FILES = 5000;

    /**
     * Create or update a "upload from computer" session record so the admin activity feed reflects the
     * live session (IN_PROGRESS), a pause that survives a browser refresh (PAUSED), or the finished
     * result (DONE / STOPPED / FAILED).
     *
     * <p>When {@code recordId} is provided the matching (user-owned) record is merged in place; otherwise
     * a new record is created. A null/blank {@code status} defaults to DONE so legacy finalize-only
     * callers keep their previous behaviour.</p>
     *
     * @return the record id (existing when updating, freshly minted when creating)
     */
    public String upsertComputerUploadSession(String userId, String recordId, String eventId,
            String creatorName, int totalCount, int uploadedCount, int failedCount, String status)
            throws ExecutionException, InterruptedException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (uploadedCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }
        String resolvedStatus = normalizeComputerStatus(status);
        int total = totalCount > 0 ? totalCount : (uploadedCount + failedCount);
        if (total > MAX_SESSION_FILES) {
            throw new IllegalArgumentException("Too many files in one session (max " + MAX_SESSION_FILES + ")");
        }

        // Update an existing live/paused record in place (keeps a single row across the whole session).
        if (recordId != null && !recordId.isBlank()) {
            UploadRecord existing = uploadRecordDao.getById(recordId.trim());
            if (existing == null) {
                throw new IllegalArgumentException("Upload record not found");
            }
            if (!userId.trim().equals(existing.getUserId())) {
                throw new IllegalArgumentException("Not allowed to modify this upload");
            }
            Map<String, Object> m = new HashMap<>();
            m.put("progress", uploadedCount);
            m.put("failedCount", failedCount);
            if (totalCount > 0) {
                m.put("totalCount", total);
            }
            m.put("status", resolvedStatus);
            m.put("pauseRequested", Boolean.FALSE);
            if (creatorName != null && !creatorName.isBlank()) {
                m.put("creatorName", creatorName.trim());
            }
            uploadRecordDao.mergeFields(existing.getUploadRecordId(), m);
            log.info("UploadRecord {} COMPUTER session {} progress={}/{} fail={}",
                    existing.getUploadRecordId(), resolvedStatus, uploadedCount, total, failedCount);
            return existing.getUploadRecordId();
        }

        // No id yet: create the session record (start of upload, or a legacy one-shot finalize).
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (total <= 0) {
            throw new IllegalArgumentException("At least one file must be accounted for");
        }
        UploadRecord r = new UploadRecord();
        r.setUserId(userId.trim());
        r.setEventId(eventId.trim());
        r.setCreatorName(creatorName != null && !creatorName.isBlank() ? creatorName.trim() : "Photographer");
        r.setDriveLink(null);
        r.setSource(UploadRecord.SOURCE_COMPUTER);
        r.setTotalCount(total);
        r.setProgress(uploadedCount);
        r.setFailedCount(failedCount);
        r.setStatus(resolvedStatus);
        r.setErrorMessage(null);
        r.setPauseRequested(Boolean.FALSE);
        String id = uploadRecordDao.create(r);
        log.info("UploadRecord {} COMPUTER session created {} user={} event={} ok={} fail={} total={}",
                id, resolvedStatus, userId, eventId, uploadedCount, failedCount, total);
        return id;
    }

    /** Only the states a computer session can legitimately be in; unknown/blank falls back to DONE. */
    private static String normalizeComputerStatus(String status) {
        if (status == null || status.isBlank()) {
            return UploadRecord.STATUS_DONE;
        }
        switch (status.trim().toUpperCase()) {
            case UploadRecord.STATUS_STARTED:
            case UploadRecord.STATUS_IN_PROGRESS:
                return UploadRecord.STATUS_IN_PROGRESS;
            case UploadRecord.STATUS_PAUSED:
                return UploadRecord.STATUS_PAUSED;
            case UploadRecord.STATUS_STOPPED:
                return UploadRecord.STATUS_STOPPED;
            case UploadRecord.STATUS_FAILED:
                return UploadRecord.STATUS_FAILED;
            case UploadRecord.STATUS_DONE:
            default:
                return UploadRecord.STATUS_DONE;
        }
    }

    public void afterDriveListing(String recordId, int totalImageCount) throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("totalCount", totalImageCount);
        m.put("status", UploadRecord.STATUS_IN_PROGRESS);
        uploadRecordDao.mergeFields(recordId, m);
    }

    public void updateDriveImportProgress(String recordId, int momentsImported, int failedSoFar)
            throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("progress", momentsImported);
        m.put("failedCount", failedSoFar);
        m.put("status", UploadRecord.STATUS_IN_PROGRESS);
        uploadRecordDao.mergeFields(recordId, m);
    }

    public void markDriveImportDone(String recordId, int momentsImported, int failedCount)
            throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("progress", momentsImported);
        m.put("failedCount", failedCount);
        m.put("status", UploadRecord.STATUS_DONE);
        m.put("errorMessage", null);
        m.put("pauseRequested", Boolean.FALSE);
        uploadRecordDao.mergeFields(recordId, m);
        log.info("UploadRecord {} DONE progress={} failed={}", recordId, momentsImported, failedCount);
    }

    public void markDriveImportFailed(String recordId, String errorMessage)
            throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("status", UploadRecord.STATUS_FAILED);
        m.put("errorMessage", errorMessage != null ? errorMessage : "Unknown error");
        m.put("pauseRequested", Boolean.FALSE);
        uploadRecordDao.mergeFields(recordId, m);
        log.warn("UploadRecord {} FAILED: {}", recordId, errorMessage);
    }

    public List<UploadRecord> listForUserNewestFirst(String userId) throws ExecutionException, InterruptedException {
        return uploadRecordDao.listByUserIdNewestFirst(userId);
    }

    public UploadRecord getById(String recordId) throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return null;
        }
        return uploadRecordDao.getById(recordId.trim());
    }

    /**
     * Running import cooperatively checks this between batches and then calls {@link #acknowledgePause}.
     */
    public boolean isPauseRequested(String recordId) throws ExecutionException, InterruptedException {
        UploadRecord r = getById(recordId);
        return r != null && Boolean.TRUE.equals(r.getPauseRequested());
    }

    public void requestPause(String recordId, String actingUserId) throws ExecutionException, InterruptedException {
        UploadRecord r = requireOwnedRecord(recordId, actingUserId);
        String st = r.getStatus();
        if (!UploadRecord.STATUS_STARTED.equals(st) && !UploadRecord.STATUS_IN_PROGRESS.equals(st)) {
            throw new IllegalStateException("Only an active import can be paused.");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("pauseRequested", Boolean.TRUE);
        uploadRecordDao.mergeFields(r.getUploadRecordId(), m);
        log.info("UploadRecord {} pause requested by user", r.getUploadRecordId());
    }

    public void acknowledgePause(String recordId, int progress, int failedCount)
            throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("status", UploadRecord.STATUS_PAUSED);
        m.put("pauseRequested", Boolean.FALSE);
        m.put("progress", progress);
        m.put("failedCount", failedCount);
        m.put("errorMessage", null);
        uploadRecordDao.mergeFields(recordId.trim(), m);
        log.info("UploadRecord {} PAUSED progress={} failed={}", recordId, progress, failedCount);
    }

    /**
     * Cancel a not-yet-finished upload record (marks it STOPPED). Used by the Uploads tab's
     * per-row Cancel action for both Drive imports and computer sessions. A completed (DONE)
     * record can't be cancelled.
     */
    public void cancel(String recordId, String actingUserId) throws ExecutionException, InterruptedException {
        UploadRecord r = requireOwnedRecord(recordId, actingUserId);
        if (UploadRecord.STATUS_DONE.equals(r.getStatus())) {
            throw new IllegalStateException("A completed upload can't be cancelled.");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("status", UploadRecord.STATUS_STOPPED);
        m.put("pauseRequested", Boolean.FALSE);
        m.put("errorMessage", null);
        uploadRecordDao.mergeFields(r.getUploadRecordId(), m);
        log.info("UploadRecord {} cancelled (STOPPED) by user", r.getUploadRecordId());
    }

    /**
     * Validates ownership and status before retrigger. Call {@link #commitRetriggerAndBuildRequest} only after
     * the Drive link is confirmed accessible.
     */
    public UploadRecord assertRetriggerEligible(String recordId, String actingUserId)
            throws ExecutionException, InterruptedException {
        UploadRecord r = requireOwnedRecord(recordId, actingUserId);
        String st = r.getStatus();
        if (!UploadRecord.STATUS_PAUSED.equals(st) && !UploadRecord.STATUS_FAILED.equals(st)
                && !UploadRecord.STATUS_DONE.equals(st)) {
            throw new IllegalStateException(
                    "Only paused, failed, or finished imports can be restarted. Pause a running import first if needed.");
        }
        if (UploadRecord.SOURCE_COMPUTER.equals(r.getSource())) {
            throw new IllegalStateException("Computer uploads cannot be restarted from this screen.");
        }
        if (r.getDriveLink() == null || r.getDriveLink().isBlank()) {
            throw new IllegalStateException("This record has no Drive link.");
        }
        return r;
    }

    public GoogleDriveImportRequest commitRetriggerAndBuildRequest(UploadRecord r)
            throws ExecutionException, InterruptedException {
        if (r == null || r.getUploadRecordId() == null) {
            throw new IllegalArgumentException("Invalid upload record");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("status", UploadRecord.STATUS_STARTED);
        m.put("pauseRequested", Boolean.FALSE);
        m.put("errorMessage", null);
        uploadRecordDao.mergeFields(r.getUploadRecordId(), m);

        GoogleDriveImportRequest req = new GoogleDriveImportRequest();
        req.setFolderUrl(r.getDriveLink().trim());
        req.setEventId(r.getEventId() != null ? r.getEventId().trim() : "");
        req.setCreatorId(r.getUserId() != null ? r.getUserId().trim() : "");
        req.setCreatorUserName(r.getCreatorName());
        req.setUploadRecordId(r.getUploadRecordId());
        log.info("UploadRecord {} retrigger scheduled", r.getUploadRecordId());
        return req;
    }

    private UploadRecord requireOwnedRecord(String recordId, String actingUserId)
            throws ExecutionException, InterruptedException {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required");
        }
        if (actingUserId == null || actingUserId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        UploadRecord r = uploadRecordDao.getById(recordId.trim());
        if (r == null) {
            throw new IllegalArgumentException("Upload record not found");
        }
        if (!actingUserId.trim().equals(r.getUserId())) {
            throw new IllegalArgumentException("Not allowed to modify this import");
        }
        return r;
    }
}
