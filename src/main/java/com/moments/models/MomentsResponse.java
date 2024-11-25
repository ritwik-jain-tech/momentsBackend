package com.moments.models;

import java.util.List;

public class MomentsResponse {
    private List<Moment> moments;
    private Cursor cursor;

    public MomentsResponse(List<Moment> moments, Cursor cursor) {
        this.moments = moments;
        this.cursor = cursor;
    }

    public List<Moment> getMoments() {
        return moments;
    }

    public void setMoments(List<Moment> moments) {
        this.moments = moments;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }
}


