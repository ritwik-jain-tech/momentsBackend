package com.moments.models;

import java.util.List;

public class FaceEmbeddingResult {
    private boolean success;
    private String message;
    private List<Double> embedding;
    private int facesDetected;

    public FaceEmbeddingResult() {
    }

    public FaceEmbeddingResult(boolean success, String message, List<Double> embedding, int facesDetected) {
        this.success = success;
        this.message = message;
        this.embedding = embedding;
        this.facesDetected = facesDetected;
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

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public int getFacesDetected() {
        return facesDetected;
    }

    public void setFacesDetected(int facesDetected) {
        this.facesDetected = facesDetected;
    }
}
