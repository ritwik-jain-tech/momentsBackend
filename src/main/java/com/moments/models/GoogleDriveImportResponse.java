package com.moments.models;

import java.util.ArrayList;
import java.util.List;

public class GoogleDriveImportResponse {
    private int imageFilesFound;
    private int momentsCreated;
    private int failed;
    private List<String> errors = new ArrayList<>();

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
}
