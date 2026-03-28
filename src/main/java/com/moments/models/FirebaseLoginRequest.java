package com.moments.models;

/**
 * Client sends Firebase ID token; optional Google OAuth access token for People API (phone numbers).
 */
public class FirebaseLoginRequest {

    private String idToken;
    /** From {@code GoogleAuthProvider.credentialFromResult}; optional. */
    private String accessToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
