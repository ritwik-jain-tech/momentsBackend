package com.moments.models;

public class NotificationRequest {
    private String userId;
    private String title;
    private String body;
    private String[] userIds; // For sending to multiple users

    public NotificationRequest() {
    }

    public NotificationRequest(String userId, String title, String body) {
        this.userId = userId;
        this.title = title;
        this.body = body;
    }

    public NotificationRequest(String[] userIds, String title, String body) {
        this.userIds = userIds;
        this.title = title;
        this.body = body;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String[] getUserIds() {
        return userIds;
    }

    public void setUserIds(String[] userIds) {
        this.userIds = userIds;
    }
}
