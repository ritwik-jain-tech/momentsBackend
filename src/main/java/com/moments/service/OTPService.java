package com.moments.service;

import com.google.cloud.Timestamp;
import com.moments.dao.OTPDao;
import com.moments.models.MessageCentralVerifyResponse;
import com.moments.models.OTPRequest;
import com.moments.models.OTPResponse;
import com.moments.models.OTPVerificationMapping;
import com.moments.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OTPService {

    @Autowired
    private OTPDao otpDao;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MessageCentralService messageCentralService;

    public void sendOtp(String phoneNumber) throws Exception {
        // Extract country code and mobile number if phone number includes country code
        String countryCode = "91";
        String mobileNumber = phoneNumber;
        
        // If phone number starts with country code (e.g., "919876543210"), extract it
        // For now, assuming phone number is passed without country code prefix
        // You can enhance this logic based on your requirements
        
        // Call MessageCentral API to send OTP
        String verificationId = messageCentralService.sendOtp(mobileNumber, countryCode);
        
        // Store phoneNumber to verificationId mapping in Firestore
        OTPVerificationMapping verificationMapping = new OTPVerificationMapping();
        verificationMapping.setPhoneNumber(phoneNumber);
        verificationMapping.setVerificationId(verificationId);
        verificationMapping.setCreatedAt(Timestamp.now());
        otpDao.saveVerificationMapping(verificationMapping);
    }

    public OTPResponse verifyOtp(String phoneNumber, String otpCode) throws Exception {
        // Retrieve verificationId from Firestore
        OTPVerificationMapping mapping = otpDao.getVerificationMappingByPhoneNumber(phoneNumber);
        
        if (mapping == null || mapping.getVerificationId() == null) {
            return new OTPResponse(false, null, "OTP Expired!");
        }
        
        // Extract country code and mobile number
        String countryCode = null;
        String mobileNumber = phoneNumber;
        
        // Call MessageCentral API to verify OTP
        MessageCentralVerifyResponse response = messageCentralService.verifyOtp(mobileNumber, mapping.getVerificationId(), otpCode, countryCode);
        
        if (response!=null && response.getData()!=null && "VERIFICATION_COMPLETED".equals(response.getData().getVerificationStatus())) {
            // Generate JWT token
            String token = jwtUtil.generateToken(phoneNumber);
            return new OTPResponse(true, token, "VERIFICATION_COMPLETED");
        }
        
        return new OTPResponse(false, null, response.getMessage());
    }
}

