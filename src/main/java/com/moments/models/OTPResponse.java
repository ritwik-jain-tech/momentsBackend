package com.moments.models;

public class OTPResponse {
    private boolean success;
    private String token;
    private UserProfile userProfile;
    private Boolean UserInEvent;
    private String message;

    public OTPResponse(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getUserInEvent() {
        return UserInEvent;
    }

    public void setUserInEvent(Boolean userInEvent) {
        UserInEvent = userInEvent;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
} 