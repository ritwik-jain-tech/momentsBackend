package com.moments.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.moments.models.AuthSessionResponse;
import com.moments.models.GoogleAuthResponse;
import com.moments.models.OTPResponse;
import com.moments.models.UserProfile;
import com.moments.utils.IdentityUtils;
import com.moments.utils.JwtUtil;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    private static final String PEOPLE_ME = "https://people.googleapis.com/v1/people/me?personFields=phoneNumbers";

    private final FirebaseAuth firebaseAuth;
    private final UserProfileService userProfileService;
    private final OTPService otpService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public FirebaseAuthService(FirebaseAuth firebaseAuth,
            UserProfileService userProfileService,
            OTPService otpService,
            JwtUtil jwtUtil,
            ObjectMapper objectMapper) {
        this.firebaseAuth = firebaseAuth;
        this.userProfileService = userProfileService;
        this.otpService = otpService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    public AuthSessionResponse signInWithFirebase(String idToken, String googleAccessToken)
            throws ExecutionException, InterruptedException, FirebaseAuthException {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken is required");
        }

        FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
        String uid = decoded.getUid();
        String email = decoded.getEmail();
        String emailLower = email != null ? email.trim().toLowerCase() : null;
        String name = decoded.getName();
        if (name == null || name.isBlank()) {
            Object n = decoded.getClaims().get("name");
            if (n != null) {
                name = n.toString();
            }
        }

        String phoneFromToken = extractPhoneFromFirebaseClaims(decoded.getClaims());
        String phoneFromPeople = null;
        if (googleAccessToken != null && !googleAccessToken.isBlank()) {
            try {
                phoneFromPeople = fetchPrimaryPhoneFromPeopleApi(googleAccessToken);
            } catch (Exception e) {
                log.warn("People API phone fetch skipped: {}", e.getMessage());
            }
        }

        String normalizedPhone = IdentityUtils.normalizeTenDigitPhone(
                IdentityUtils.firstNonBlank(phoneFromToken, phoneFromPeople));

        // One human = one profile: resolve/merge by firebaseUid → email → phone.
        UserProfile profile = userProfileService.resolveOrCreateProfile(uid, emailLower, normalizedPhone, name);

        String jwt = jwtUtil.generateToken(profile.getUserId());
        return new AuthSessionResponse(jwt, profile);
    }

    /**
     * Stage 1 of the Google staged sign-in. Verifies the Firebase ID token and
     * resolves an existing profile by firebaseUid → email only (never phone, never
     * creating one). If the email is already known the user is logged in; otherwise
     * the client is told to collect a phone number + OTP next.
     */
    public GoogleAuthResponse startGoogleAuth(String idToken)
            throws ExecutionException, InterruptedException, FirebaseAuthException {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken is required");
        }
        FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
        String uid = decoded.getUid();
        String emailLower = IdentityUtils.normalizeEmail(decoded.getEmail());
        String name = extractName(decoded);

        UserProfile existing = userProfileService.findByFirebaseUidOrEmail(uid, emailLower);
        if (existing != null) {
            // Backfill the firebaseUid so subsequent logins resolve instantly by uid.
            UserProfile hydrated = userProfileService.linkGoogleIdentity(existing, uid, emailLower, name);
            String jwt = jwtUtil.generateToken(hydrated.getUserId());
            return GoogleAuthResponse.loggedIn(jwt, hydrated);
        }
        return GoogleAuthResponse.needsPhone(emailLower, name);
    }

    /**
     * Stage 2 of the Google staged sign-in. Re-verifies the Firebase ID token (the
     * email/uid to link are taken from it, never the client) and verifies the OTP for
     * {@code phoneNumber}. On a valid OTP: if a profile exists for that phone, the
     * Google email/uid are linked onto it and the user is logged in; otherwise the
     * client is told to show the "activate free trial" form.
     */
    public GoogleAuthResponse linkPhoneToGoogle(String idToken, String phoneNumber, String otpCode)
            throws Exception {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
        String uid = decoded.getUid();
        String emailLower = IdentityUtils.normalizeEmail(decoded.getEmail());
        String name = extractName(decoded);

        OTPResponse otp = otpService.verifyOtp(phoneNumber, otpCode);
        if (otp == null || !otp.isSuccess()) {
            return GoogleAuthResponse.otpFailed(otp != null ? otp.getMessage() : "Invalid OTP");
        }

        UserProfile profile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
        if (profile != null) {
            UserProfile hydrated = userProfileService.linkGoogleIdentity(profile, uid, emailLower, name);
            String jwt = jwtUtil.generateToken(hydrated.getUserId());
            return GoogleAuthResponse.loggedIn(jwt, hydrated);
        }
        return GoogleAuthResponse.needsSignup(emailLower, name,
                IdentityUtils.normalizeTenDigitPhone(phoneNumber));
    }

    /** Best-effort display name from the standard claim then the raw {@code name} claim. */
    private static String extractName(FirebaseToken decoded) {
        String name = decoded.getName();
        if (name == null || name.isBlank()) {
            Object n = decoded.getClaims().get("name");
            if (n != null) {
                name = n.toString();
            }
        }
        return name;
    }

    private static String extractPhoneFromFirebaseClaims(Map<String, Object> claims) {
        Object p = claims.get("phone_number");
        return p != null ? p.toString() : null;
    }

    private String fetchPrimaryPhoneFromPeopleApi(String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PEOPLE_ME))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            log.debug("People API HTTP {}: {}", res.statusCode(), res.body());
            return null;
        }
        JsonNode root = objectMapper.readTree(res.body());
        JsonNode phones = root.path("phoneNumbers");
        if (!phones.isArray() || phones.isEmpty()) {
            return null;
        }
        JsonNode first = phones.get(0);
        return first.path("value").asText(null);
    }
}
