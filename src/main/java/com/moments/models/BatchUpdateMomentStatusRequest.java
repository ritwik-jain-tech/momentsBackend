package com.moments.models;

import java.util.ArrayList;
import java.util.List;

public class BatchUpdateMomentStatusRequest {

    private String eventId;
    private String userId;
    private List<String> momentIds = new ArrayList<>();
    /** Target status for all listed moments ({@link MomentStatus}). */
    private MomentStatus status;

    public BatchUpdateMomentStatusRequest() {
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getMomentIds() {
        return momentIds;
    }

    public void setMomentIds(List<String> momentIds) {
        this.momentIds = momentIds != null ? momentIds : new ArrayList<>();
    }

    public MomentStatus getStatus() {
        return status;
    }

    public void setStatus(MomentStatus status) {
        this.status = status;
    }
}
