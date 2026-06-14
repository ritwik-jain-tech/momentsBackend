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
import com.moments.models.UserProfile;
import com.moments.utils.IdentityUtils;
import com.moments.utils.JwtUtil;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    private static final String PEOPLE_ME = "https://people.googleapis.com/v1/people/me?personFields=phoneNumbers";

    private final FirebaseAuth firebaseAuth;
    private final UserProfileService userProfileService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public FirebaseAuthService(FirebaseAuth firebaseAuth,
            UserProfileService userProfileService,
            JwtUtil jwtUtil,
            ObjectMapper objectMapper) {
        this.firebaseAuth = firebaseAuth;
        this.userProfileService = userProfileService;
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
