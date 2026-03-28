package com.moments.controller;

import com.google.firebase.auth.FirebaseAuthException;
import com.moments.models.AuthSessionResponse;
import com.moments.models.BaseResponse;
import com.moments.models.FirebaseLoginRequest;
import com.moments.service.FirebaseAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private FirebaseAuthService firebaseAuthService;

    @PostMapping("/firebase")
    public ResponseEntity<BaseResponse> firebaseSession(@RequestBody FirebaseLoginRequest body) {
        if (body == null || body.getIdToken() == null || body.getIdToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("idToken is required", HttpStatus.BAD_REQUEST, null));
        }
        try {
            AuthSessionResponse session = firebaseAuthService.signInWithFirebase(
                    body.getIdToken(), body.getAccessToken());
            return ResponseEntity.ok(new BaseResponse("Signed in", HttpStatus.OK, session));
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token invalid: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BaseResponse("Invalid or expired Firebase token", HttpStatus.UNAUTHORIZED, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (Exception e) {
            log.error("Firebase session error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Legacy query-style call (same as older admin client expecting GET with idToken).
     */
    @GetMapping("/firebase")
    public ResponseEntity<BaseResponse> firebaseSessionGet(
            @RequestParam String idToken,
            @RequestParam(required = false) String accessToken) {
        FirebaseLoginRequest req = new FirebaseLoginRequest();
        req.setIdToken(idToken);
        req.setAccessToken(accessToken);
        return firebaseSession(req);
    }
}
