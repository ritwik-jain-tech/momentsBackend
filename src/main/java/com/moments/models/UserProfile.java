package com.moments.models;


import java.util.List;

public class UserProfile {
    private String userId;
    private String phoneNumber;
    private String name;
    private Role role;
    private List<String> eventIds;
    private List<String> blockedUserIds;
    private List<Event> eventDetails;
    private String fcmToken;
    private String side;

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public List<Event> getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(List<Event> eventDetails) {
        this.eventDetails = eventDetails;
    }

    public List<String> getEventIds() {
        return eventIds;
    }

    public void setEventIds(List<String> eventIds) {
        this.eventIds = eventIds;
    }

    public List<String> getBlockedUserIds() {
        return blockedUserIds;
    }

    public void setBlockedUserIds(List<String> blockedUserIds) {
        this.blockedUserIds = blockedUserIds;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "userId='" + userId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", name='" + name + '\'' +
                ", role=" + role +
                ", eventIds=" + eventIds +
                ", fcmToken='" + fcmToken + '\'' +
                '}';
    }
}
