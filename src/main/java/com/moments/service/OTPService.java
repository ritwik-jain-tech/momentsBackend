package com.moments.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

import com.moments.dao.OTPDao;
import com.moments.models.OTPRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import com.google.cloud.Timestamp;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class OTPService {

    @Autowired
    private OTPDao otpDao;
    private static final int OTP_VALIDATION_MINUTES =10;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOtp(String phoneNumber) {

        OTPRequest otpRequest = otpDao.getOtpByPhoneNumber(phoneNumber);
        if(otpRequest == null) {

           // int otp = new Random().nextInt(900000) + 100000; // Generate 6-digit OTP
            int otp = Integer.parseInt(phoneNumber.substring(phoneNumber.length()-6));
            long timestamp = System.currentTimeMillis();
            Timestamp expiryTimestamp = Timestamp.ofTimeSecondsAndNanos(
                    (System.currentTimeMillis() + OTP_VALIDATION_MINUTES*60*1000) / 1000, 0);
            otpRequest = new OTPRequest(phoneNumber, otp, timestamp, expiryTimestamp);
            otpDao.saveOtp(otpRequest);
        }
    }

    public boolean verifyOtp(String phoneNumber, int otp) {
        OTPRequest otpRequest = otpDao.getOtpByPhoneNumber(phoneNumber);
        if (otpRequest != null && otpRequest.getOtp() == otp) {
           return true;
        }
        return false;
    }

}

