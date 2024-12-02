package com.moments.controller;



import com.moments.models.BaseResponse;
import com.moments.models.OTPVerificationResponse;
import com.moments.models.UserProfile;
import com.moments.service.OTPService;
import com.moments.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
public class OTPController {

    @Autowired
    private OTPService otpService;

    @Autowired
    private UserProfileService userProfileService;

    @PostMapping("/send")
    public ResponseEntity<String> sendOtp(@RequestParam String phoneNumber) {
        otpService.sendOtp(phoneNumber);
        return ResponseEntity.ok("OTP sent successfully.");
    }

    @PostMapping("/verify")
    public ResponseEntity<OTPVerificationResponse> verifyOtp(@RequestParam String phoneNumber,
                                                             @RequestParam int otp,
                                                            @RequestParam String eventId) {
        boolean isVerified = otpService.verifyOtp(phoneNumber, otp);
        if(isVerified){
            UserProfile userProfile = null;
            try {
                userProfile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
                if(eventId!=null && userProfile!=null){
                   userProfile = userProfileService.addUserToEvent(userProfile.getUserId(), eventId);
                }
            } catch (Exception e){
                System.out.println(e.getMessage());
            }
            return ResponseEntity.ok(new OTPVerificationResponse(true, userProfile));
        }

        return ResponseEntity.ok(new OTPVerificationResponse(false, null));
    }
}

