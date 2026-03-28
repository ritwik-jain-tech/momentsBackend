package com.moments.models;

/**
 * Returned after Firebase (or similar) sign-in: app JWT plus hydrated user profile.
 */
public class AuthSessionResponse {

    private String token;
    private UserProfile userProfile;

    public AuthSessionResponse() {
    }

    public AuthSessionResponse(String token, UserProfile userProfile) {
        this.token = token;
        this.userProfile = userProfile;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }
}
