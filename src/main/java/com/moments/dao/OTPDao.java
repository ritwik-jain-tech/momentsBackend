package com.moments.dao;

import com.moments.models.OTPRequest;

public interface OTPDao {
    void saveOtp(OTPRequest otpRequest);
    OTPRequest getOtp(String phoneNumber);
    void deleteOtp(String phoneNumber);
    OTPRequest getOtpByPhoneNumber(String phoneNumber);
}
