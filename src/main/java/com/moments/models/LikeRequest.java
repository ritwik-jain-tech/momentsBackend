package com.moments.models;

public class LikeRequest {
    private String userId;
    private String momentId;

    public LikeRequest() {
    }

    public LikeRequest(String userId, String momentId) {
        this.userId = userId;
        this.momentId = momentId;
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