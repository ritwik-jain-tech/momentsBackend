package com.moments.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ComputerUploadSessionRequest {

    private String eventId;
    private int uploadedCount;
    private int failedCount;
    private String creatorName;

    /** When present, update this existing live session record instead of creating a new one. */
    private String uploadRecordId;
    /** Total files in the session (lets a live/paused record show progress out of the full set). */
    private int totalCount;
    /** Desired lifecycle state; null defaults to DONE for legacy finalize-only callers. */
    private String status;

    public String getUploadRecordId() {
        return uploadRecordId;
    }

    public void setUploadRecordId(String uploadRecordId) {
        this.uploadRecordId = uploadRecordId;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public int getUploadedCount() {
        return uploadedCount;
    }

    public void setUploadedCount(int uploadedCount) {
        this.uploadedCount = uploadedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }
}
