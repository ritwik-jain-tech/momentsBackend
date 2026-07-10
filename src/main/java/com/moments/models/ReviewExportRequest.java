package com.moments.models;

/** Photographer request to export approved moments to the client review page. */
public class ReviewExportRequest {
    private String userId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
