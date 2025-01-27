package com.moments.models;


import com.google.cloud.Timestamp;

public class OTPRequest {
    private String phoneNumber;
    private int otp;
    private long timestamp;
    private Timestamp expiryTimestamp;
    private String eventId;

    public Timestamp getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(Timestamp expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public OTPRequest(String phoneNumber, int otp, long timestamp, Timestamp expiryTimestamp) {
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.timestamp = timestamp;
        this.expiryTimestamp = expiryTimestamp;
    }

    public OTPRequest() {
    }

    // Getters and setters
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public int getOtp() {
        return otp;
    }

    public void setOtp(int otp) {
        this.otp = otp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

