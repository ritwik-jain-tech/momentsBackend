package com.moments.models;

import java.util.ArrayList;
import java.util.List;

public class GoogleDriveImportResponse {
    private String uploadRecordId;

    private int imageFilesFound;
    private int momentsCreated;
    private int momentsSkipped;
    private int failed;
    /** True when the job stopped early because the user requested pause. */
    private boolean paused;
    private List<String> errors = new ArrayList<>();

    public String getUploadRecordId() {
        return uploadRecordId;
    }

    public void setUploadRecordId(String uploadRecordId) {
        this.uploadRecordId = uploadRecordId;
    }

    public int getImageFilesFound() {
        return imageFilesFound;
    }

    public void setImageFilesFound(int imageFilesFound) {
        this.imageFilesFound = imageFilesFound;
    }

    public int getMomentsCreated() {
        return momentsCreated;
    }

    public void setMomentsCreated(int momentsCreated) {
        this.momentsCreated = momentsCreated;
    }

    public int getMomentsSkipped() {
        return momentsSkipped;
    }

    public void setMomentsSkipped(int momentsSkipped) {
        this.momentsSkipped = momentsSkipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
