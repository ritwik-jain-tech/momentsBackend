package com.moments.models;

public class LikedMomentsRequest {
    private String userId;
    private String eventId;
    private Cursor cursor;

    public LikedMomentsRequest() {
    }

    public LikedMomentsRequest(String userId, String eventId, Cursor cursor) {
        this.userId = userId;
        this.eventId = eventId;
        this.cursor = cursor;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }
} 