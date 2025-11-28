package com.moments.models;

public class MomentNotificationRequest {
    private String momentId;
    private String userId;

    public MomentNotificationRequest() {
    }

    public MomentNotificationRequest(String momentId, String userId) {
        this.momentId = momentId;
        this.userId = userId;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

