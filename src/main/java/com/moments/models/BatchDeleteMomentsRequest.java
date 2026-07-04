package com.moments.models;

import java.util.ArrayList;
import java.util.List;

public class BatchDeleteMomentsRequest {

    private String eventId;
    private String userId;
    private List<String> momentIds = new ArrayList<>();

    public BatchDeleteMomentsRequest() {
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
}
