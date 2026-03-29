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
        r.setTotalCount(0);
        r.setProgress(0);
        r.setFailedCount(0);
        r.setStatus(UploadRecord.STATUS_STARTED);
        r.setErrorMessage(null);
        String id = uploadRecordDao.create(r);
        log.info("UploadRecord {} STARTED user={} event={}", id, userId, eventId);
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
}
