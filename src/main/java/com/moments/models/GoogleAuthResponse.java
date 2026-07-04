package com.moments.models;

/**
 * Result of a stage in the Google-first staged sign-in flow (see
 * {@code /api/auth/google/**}). The {@code status} tells the client which UI
 * stage to render next:
 * <ul>
 *   <li>{@code LOGGED_IN} — profile resolved; {@code token} + {@code userProfile} are set.</li>
 *   <li>{@code NEEDS_PHONE} — no profile for this Google email; collect a phone number + OTP.</li>
 *   <li>{@code NEEDS_SIGNUP} — phone verified but no account exists; show the "activate free trial" form.</li>
 *   <li>{@code OTP_FAILED} — the submitted OTP was invalid/expired.</li>
 * </ul>
 * The Google identity ({@code email}, {@code name}) is echoed back so the client can
 * prefill later stages without trusting its own copy.
 */
public class GoogleAuthResponse {

    public static final String LOGGED_IN = "LOGGED_IN";
    public static final String NEEDS_PHONE = "NEEDS_PHONE";
    public static final String NEEDS_SIGNUP = "NEEDS_SIGNUP";
    public static final String OTP_FAILED = "OTP_FAILED";

    private String status;
    private String token;
    private UserProfile userProfile;
    private String email;
    private String name;
    private String phoneNumber;
    private String message;

    public GoogleAuthResponse() {
    }

    public static GoogleAuthResponse loggedIn(String token, UserProfile userProfile) {
        GoogleAuthResponse r = new GoogleAuthResponse();
        r.status = LOGGED_IN;
        r.token = token;
        r.userProfile = userProfile;
        r.email = userProfile != null ? userProfile.getEmailId() : null;
        r.name = userProfile != null ? userProfile.getName() : null;
        r.phoneNumber = userProfile != null ? userProfile.getPhoneNumber() : null;
        return r;
    }

    public static GoogleAuthResponse needsPhone(String email, String name) {
        GoogleAuthResponse r = new GoogleAuthResponse();
        r.status = NEEDS_PHONE;
        r.email = email;
        r.name = name;
        return r;
    }

    public static GoogleAuthResponse needsSignup(String email, String name, String phoneNumber) {
        GoogleAuthResponse r = new GoogleAuthResponse();
        r.status = NEEDS_SIGNUP;
        r.email = email;
        r.name = name;
        r.phoneNumber = phoneNumber;
        return r;
    }

    public static GoogleAuthResponse otpFailed(String message) {
        GoogleAuthResponse r = new GoogleAuthResponse();
        r.status = OTP_FAILED;
        r.message = message;
        return r;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
