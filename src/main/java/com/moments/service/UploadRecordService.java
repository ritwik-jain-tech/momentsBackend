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

    /**
     * Persists a finished “upload from computer” session for the admin activity feed (immediate DONE).
     */
    public String createCompletedComputerUpload(String userId, String eventId, String creatorName,
            int uploadedCount, int failedCount) throws ExecutionException, InterruptedException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (uploadedCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }
        int total = uploadedCount + failedCount;
        if (total <= 0) {
            throw new IllegalArgumentException("At least one file must be accounted for");
        }
        if (total > 500) {
            throw new IllegalArgumentException("Too many files in one session (max 500)");
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
        r.setStatus(UploadRecord.STATUS_DONE);
        r.setErrorMessage(null);
        r.setPauseRequested(Boolean.FALSE);
        String id = uploadRecordDao.create(r);
        log.info("UploadRecord {} COMPUTER session DONE user={} event={} ok={} fail={}",
                id, userId, eventId, uploadedCount, failedCount);
        return id;
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
