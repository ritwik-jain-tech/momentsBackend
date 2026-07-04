package com.moments.models;

import java.util.List;

/**
 * Finalize direct-to-GCS uploads: create moments for files already PUT to Cloud Storage.
 * Each moment's {@code media.url} must be the {@code publicUrl} returned by the signed-URL request.
 */
public class FinalizeUploadRequest {

    private List<Moment> moments;

    public List<Moment> getMoments() {
        return moments;
    }

    public void setMoments(List<Moment> moments) {
        this.moments = moments;
    }
}
