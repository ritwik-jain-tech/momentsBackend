package com.moments.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.firestore.annotation.Exclude;

/**
 * Tracks a Google Drive folder import job for the admin uploads UI.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadRecord {

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    /** Firestore document id; not written as a field on the document. */
    @Exclude
    private String uploadRecordId;

    private String userId;
    private String eventId;
    private String creatorName;
    private String driveLink;

    /** Number of image files discovered in Drive (set after listing). */
    private int totalCount;

    /** Moments successfully saved so far (and final count when DONE). */
    private int progress;

    /** Per-file failures during import (final when DONE). */
    private int failedCount;

    private String status;
    private String errorMessage;

    private Long createdAt;
    private Long updatedAt;

    public String getUploadRecordId() {
        return uploadRecordId;
    }

    public void setUploadRecordId(String uploadRecordId) {
        this.uploadRecordId = uploadRecordId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getDriveLink() {
        return driveLink;
    }

    public void setDriveLink(String driveLink) {
        this.driveLink = driveLink;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
