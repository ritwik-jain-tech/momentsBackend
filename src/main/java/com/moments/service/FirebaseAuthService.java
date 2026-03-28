package com.moments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.moments.dao.UserProfileDao;
import com.moments.models.AuthSessionResponse;
import com.moments.models.UserProfile;
import com.moments.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    private static final String PEOPLE_ME =
            "https://people.googleapis.com/v1/people/me?personFields=phoneNumbers";

    private final FirebaseAuth firebaseAuth;
    private final UserProfileService userProfileService;
    private final UserProfileDao userProfileDao;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public FirebaseAuthService(FirebaseAuth firebaseAuth,
            UserProfileService userProfileService,
            UserProfileDao userProfileDao,
            JwtUtil jwtUtil,
            ObjectMapper objectMapper) {
        this.firebaseAuth = firebaseAuth;
        this.userProfileService = userProfileService;
        this.userProfileDao = userProfileDao;
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

        String normalizedPhone = normalizeTenDigitPhone(firstNonBlank(phoneFromToken, phoneFromPeople));

        UserProfile profile = userProfileService.getUserProfileByFirebaseUid(uid);
        if (profile == null && emailLower != null) {
            profile = userProfileService.getUserProfileByEmailId(emailLower);
            if (profile != null && (profile.getFirebaseUid() == null || profile.getFirebaseUid().isBlank())) {
                profile.setFirebaseUid(uid);
            }
        }

        if (profile == null) {
            UserProfile seed = new UserProfile();
            seed.setFirebaseUid(uid);
            seed.setEmailId(emailLower);
            if (name != null && !name.isBlank()) {
                seed.setName(name);
            }
            if (normalizedPhone != null) {
                seed.setPhoneNumber(normalizedPhone);
            }
            profile = userProfileService.createMinimalStudioUser(seed);
        } else {
            applyFirebaseUpdates(profile, uid, emailLower, name, normalizedPhone);
            userProfileDao.updateUserProfile(profile);
            profile = userProfileService.getUser(profile.getUserId());
        }

        String jwt = jwtUtil.generateToken(profile.getUserId());
        return new AuthSessionResponse(jwt, profile);
    }

    private static void applyFirebaseUpdates(UserProfile profile, String uid, String emailLower, String name,
            String normalizedPhone) {
        profile.setFirebaseUid(uid);
        if (emailLower != null) {
            profile.setEmailId(emailLower);
        }
        if (name != null && !name.isBlank()) {
            profile.setName(name);
        }
        if (normalizedPhone != null
                && (profile.getPhoneNumber() == null || profile.getPhoneNumber().isBlank())) {
            profile.setPhoneNumber(normalizedPhone);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String extractPhoneFromFirebaseClaims(Map<String, Object> claims) {
        Object p = claims.get("phone_number");
        return p != null ? p.toString() : null;
    }

    /**
     * Keeps last 10 digits when longer (common for +91…); aligns with existing OTP validation.
     */
    static String normalizeTenDigitPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() >= 10) {
            return d.substring(d.length() - 10);
        }
        return d.isEmpty() ? null : d;
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
