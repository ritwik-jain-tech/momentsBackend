package com.moments.models;

public class OTPVerificationResponse {
    private boolean isValidOTP;
    private UserProfile userProfile;
    public OTPVerificationResponse(boolean isValidOTP, UserProfile userProfile) {
        this.isValidOTP = isValidOTP;
        this.userProfile = userProfile;
    }
    public boolean isValidOTP() {
        return isValidOTP;
    }
    public UserProfile getUserProfile() {
        return userProfile;
    }


}
