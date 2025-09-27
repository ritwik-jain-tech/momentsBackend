package com.moments.models;

import org.springframework.web.multipart.MultipartFile;

public class FaceEmbeddingRequest {
    private MultipartFile imageFile;
    private String userId;
    private String momentId;

    public FaceEmbeddingRequest() {
    }

    public FaceEmbeddingRequest(MultipartFile imageFile, String userId, String momentId) {
        this.imageFile = imageFile;
        this.userId = userId;
        this.momentId = momentId;
    }

    public MultipartFile getImageFile() {
        return imageFile;
    }

    public void setImageFile(MultipartFile imageFile) {
        this.imageFile = imageFile;
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
}
