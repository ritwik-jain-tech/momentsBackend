package com.moments.dao;

import com.moments.models.OTPRequest;
import com.moments.models.OTPVerificationMapping;

public interface OTPDao {
    void saveOtp(OTPRequest otpRequest);
    OTPRequest getOtp(String phoneNumber);
    void deleteOtp(String phoneNumber);
    OTPRequest getOtpByPhoneNumber(String phoneNumber);
    void saveVerificationMapping(OTPVerificationMapping mapping);
    OTPVerificationMapping getVerificationMappingByPhoneNumber(String phoneNumber);
}
