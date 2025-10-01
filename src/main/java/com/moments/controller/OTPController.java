package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.OTPRequest;
import com.moments.models.OTPResponse;
import com.moments.models.UserProfile;
import com.moments.service.OTPService;
import com.moments.service.UserProfileService;
import com.moments.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
public class OTPController {

    @Autowired
    private OTPService otpService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<BaseResponse> sendOtp(@RequestBody OTPRequest otpRequest) {
        try {
            otpService.sendOtp(otpRequest.getPhoneNumber());
            return ResponseEntity.ok(new BaseResponse("OTP sent successfully", HttpStatus.OK, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to send OTP", HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<BaseResponse> verifyOtp(@RequestBody OTPRequest otpRequest) {
        try {
            OTPResponse otpResponse = otpService.verifyOtp(otpRequest.getPhoneNumber(), otpRequest.getOtp());
            UserProfile userProfile = userProfileService.getUserProfileByPhoneNumber(otpRequest.getPhoneNumber());
            if (otpResponse.isSuccess()) {
                if( userProfile != null) {
                    otpResponse.setUserProfile(userProfile);
                    otpResponse.setUserInEvent(otpRequest.getEventId()!=null && userProfile.getEventIds()!=null && userProfile.getEventIds().contains(otpRequest.getEventId()));
                    return ResponseEntity.ok(new BaseResponse("OTP verified successfully, User Profile Exists", HttpStatus.OK, otpResponse));

                } else {
                    return ResponseEntity.ok(new BaseResponse("OTP verified successfully, userProfile does not Exists", HttpStatus.OK, otpResponse));
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new BaseResponse("Invalid OTP", HttpStatus.UNAUTHORIZED, null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to verify OTP", HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}
