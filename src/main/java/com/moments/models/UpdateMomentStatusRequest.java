package com.moments.models;

public class UpdateMomentStatusRequest {
    private String momentId;
    private String status;

    public UpdateMomentStatusRequest() {
    }

    public UpdateMomentStatusRequest(String momentId, String status) {
        this.momentId = momentId;
        this.status = status;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
} 