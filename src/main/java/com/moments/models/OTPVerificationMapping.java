package com.moments.models;

import com.google.cloud.Timestamp;

public class OTPVerificationMapping {
    private String phoneNumber;
    private String verificationId;
    private Timestamp createdAt;

    public OTPVerificationMapping() {
    }

    public OTPVerificationMapping(String phoneNumber, String verificationId, Timestamp createdAt) {
        this.phoneNumber = phoneNumber;
        this.verificationId = verificationId;
        this.createdAt = createdAt;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

