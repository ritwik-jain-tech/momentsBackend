package com.moments.models;

public class GoogleDriveImportRequest {
    /** Share link or folder URL from Google Drive */
    private String folderUrl;
    private String eventId;
    private String creatorId;
    private String creatorUserName;

    /** Set by the server when an {@link UploadRecord} is created for this job. */
    private String uploadRecordId;

    public String getFolderUrl() {
        return folderUrl;
    }

    public void setFolderUrl(String folderUrl) {
        this.folderUrl = folderUrl;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorUserName() {
        return creatorUserName;
    }

    public void setCreatorUserName(String creatorUserName) {
        this.creatorUserName = creatorUserName;
    }

    public String getUploadRecordId() {
        return uploadRecordId;
    }

    public void setUploadRecordId(String uploadRecordId) {
        this.uploadRecordId = uploadRecordId;
    }
}
