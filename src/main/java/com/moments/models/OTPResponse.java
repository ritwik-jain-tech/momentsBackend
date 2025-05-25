package com.moments.models;

public class OTPResponse {
    private boolean success;
    private String token;
    private UserProfile userProfile;

    public OTPResponse(boolean success, String token) {
        this.success = success;
        this.token = token;
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