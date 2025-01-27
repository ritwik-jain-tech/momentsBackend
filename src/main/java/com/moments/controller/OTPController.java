package com.moments.controller;



import com.moments.models.BaseResponse;
import com.moments.models.OTPRequest;
import com.moments.models.OTPVerificationResponse;
import com.moments.models.UserProfile;
import com.moments.service.OTPService;
import com.moments.service.UserProfileService;
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

    @PostMapping("/send")
    public ResponseEntity<BaseResponse> sendOtp(@RequestBody OTPRequest otpRequest) {
        try {
            otpService.sendOtp(otpRequest.getPhoneNumber());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("OTP sent", HttpStatus.OK, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<BaseResponse> verifyOtp(@RequestBody OTPRequest otpRequest) {
        String phoneNumber = otpRequest.getPhoneNumber();
        int otp =otpRequest.getOtp();
        String eventId = otpRequest.getEventId();

        boolean isVerified = otpService.verifyOtp(phoneNumber, otp);
        if(isVerified){
            UserProfile userProfile = null;
            try {
                userProfile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
                if(eventId!=null && userProfile!=null){
                   userProfile = userProfileService.addUserToEvent(userProfile.getUserId(), eventId);
                }
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse("OTP verified",HttpStatus.OK,new OTPVerificationResponse(true, userProfile))
                );
            } catch (Exception e){
                System.out.println(e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
            }

        }
        return ResponseEntity.status(HttpStatus.OK).body(
                new BaseResponse("Invalid OTP",HttpStatus.OK,new OTPVerificationResponse(false, null)));
    }
}

