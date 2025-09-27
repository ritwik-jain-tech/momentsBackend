package com.moments.models;

import java.util.List;

public class FaceEmbeddingResponse {
    private boolean success;
    private String message;
    private List<String> taggedUserIds;
    private int facesDetected;
    private int facesMatched;

    public FaceEmbeddingResponse() {
    }

    public FaceEmbeddingResponse(boolean success, String message, List<String> taggedUserIds, int facesDetected, int facesMatched) {
        this.success = success;
        this.message = message;
        this.taggedUserIds = taggedUserIds;
        this.facesDetected = facesDetected;
        this.facesMatched = facesMatched;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getTaggedUserIds() {
        return taggedUserIds;
    }

    public void setTaggedUserIds(List<String> taggedUserIds) {
        this.taggedUserIds = taggedUserIds;
    }

    public int getFacesDetected() {
        return facesDetected;
    }

    public void setFacesDetected(int facesDetected) {
        this.facesDetected = facesDetected;
    }

    public int getFacesMatched() {
        return facesMatched;
    }

    public void setFacesMatched(int facesMatched) {
        this.facesMatched = facesMatched;
    }
}
