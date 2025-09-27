package com.moments.models;

public class MomentsRequest {

    MomentFilter filter;
    Cursor cursor;
    String eventId;
    String userId;


    public MomentFilter getFilter() {
        return filter;
    }

    public void setFilter(MomentFilter filter) {
        this.filter = filter;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
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
}
