package com.moments.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.auth.FirebaseAuthException;
import com.moments.models.BaseResponse;
import com.moments.models.FirebaseLoginRequest;
import com.moments.models.GoogleAuthResponse;
import com.moments.models.GoogleLinkPhoneRequest;
import com.moments.service.FirebaseAuthService;

/**
 * Google-first <em>staged</em> sign-in. Unlike {@code POST /api/auth/firebase}
 * (which resolves-or-creates a profile in one shot), this flow gates onboarding:
 * <ol>
 *   <li>{@code /start} — Google token in → log in if the email is known, else ask for a phone.</li>
 *   <li>client sends the OTP via {@code /api/otp/send}.</li>
 *   <li>{@code /link-phone} — OTP in → log in and link the email if the phone is known,
 *       else tell the client to show the "activate free trial" form (lead capture only).</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    @Autowired
    private FirebaseAuthService firebaseAuthService;

    @PostMapping("/start")
    public ResponseEntity<BaseResponse> start(@RequestBody FirebaseLoginRequest body) {
        if (body == null || body.getIdToken() == null || body.getIdToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("idToken is required", HttpStatus.BAD_REQUEST, null));
        }
        try {
            GoogleAuthResponse result = firebaseAuthService.startGoogleAuth(body.getIdToken());
            return ResponseEntity.ok(new BaseResponse(result.getStatus(), HttpStatus.OK, result));
        } catch (FirebaseAuthException e) {
            log.warn("Google start: invalid token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BaseResponse("Invalid or expired Google token", HttpStatus.UNAUTHORIZED, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (Exception e) {
            log.error("Google start error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/link-phone")
    public ResponseEntity<BaseResponse> linkPhone(@RequestBody GoogleLinkPhoneRequest body) {
        if (body == null || body.getIdToken() == null || body.getIdToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("idToken is required", HttpStatus.BAD_REQUEST, null));
        }
        if (body.getPhoneNumber() == null || body.getPhoneNumber().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("phoneNumber is required", HttpStatus.BAD_REQUEST, null));
        }
        try {
            GoogleAuthResponse result = firebaseAuthService.linkPhoneToGoogle(
                    body.getIdToken(), body.getPhoneNumber(), body.resolveOtpCode());
            if (GoogleAuthResponse.OTP_FAILED.equals(result.getStatus())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new BaseResponse(result.getMessage(), HttpStatus.UNAUTHORIZED, result));
            }
            return ResponseEntity.ok(new BaseResponse(result.getStatus(), HttpStatus.OK, result));
        } catch (FirebaseAuthException e) {
            log.warn("Google link-phone: invalid token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BaseResponse("Invalid or expired Google token", HttpStatus.UNAUTHORIZED, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (Exception e) {
            log.error("Google link-phone error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}
