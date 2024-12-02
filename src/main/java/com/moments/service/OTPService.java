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
            int otp = new Random().nextInt(900000) + 100000; // Generate 6-digit OTP
            long timestamp = System.currentTimeMillis();
            Timestamp expiryTimestamp = Timestamp.ofTimeSecondsAndNanos(
                    (System.currentTimeMillis() + OTP_VALIDATION_MINUTES*60*1000) / 1000, 0);
            otpRequest = new OTPRequest(phoneNumber, otp, timestamp, expiryTimestamp);
            otpDao.saveOtp(otpRequest);
        }
        // Send OTP via Gupshup API
        String message = String.format("Your OTP for Meesho login is %d and is valid for 10 mins. Please DO NOT share this OTP with anyone to keep your account safe. %d Meesho.", otpRequest.getOtp(),otpRequest.getOtp());
        String url = "http://enterprise.smsgupshup.com/GatewayAPI/rest";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/x-www-form-urlencoded");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("userid", "2000193518");
        body.add("password", "vpYW6Z9A");
        body.add("send_to", phoneNumber);
        body.add("method", "sendMessage");
        body.add("msg", message);
        body.add("msg_type", "Text");
        body.add("format", "JSON");
        body.add("auth_scheme", "plain");
        body.add("mask", "MEESHO");
        body.add("v", "1.1");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send SMS");
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

