package com.moments.models;

import java.util.List;

public class FaceEmbedding {
    private String embeddingId;
    private String userId;
    private String momentId;
    private List<Double> embedding;
    private Long createdAt;
    private String imageUrl;
    private String faceId; // Unique identifier for this specific face in the image

    public FaceEmbedding() {
    }

    public FaceEmbedding(String userId, String momentId, List<Double> embedding, String imageUrl, String faceId) {
        this.userId = userId;
        this.momentId = momentId;
        this.embedding = embedding;
        this.imageUrl = imageUrl;
        this.faceId = faceId;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getFaceId() {
        return faceId;
    }

    public void setFaceId(String faceId) {
        this.faceId = faceId;
    }
}
