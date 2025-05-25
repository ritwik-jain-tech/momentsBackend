package com.moments.models;

public class UpdateMomentStatusRequest {
    private String momentId;
    private MomentStatus status;

    public UpdateMomentStatusRequest() {
    }

    public UpdateMomentStatusRequest(String momentId, MomentStatus status) {
        this.momentId = momentId;
        this.status = status;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public MomentStatus getStatus() {
        return status;
    }

    public void setStatus(MomentStatus status) {
        this.status = status;
    }
} 