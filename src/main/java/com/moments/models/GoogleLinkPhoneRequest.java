package com.moments.models;

/**
 * Second stage of the Google staged sign-in: the verified Firebase ID token plus
 * the phone number and OTP the user just entered. The email/uid to link onto the
 * phone-resolved profile is derived server-side from {@code idToken}, never trusted
 * from the client.
 */
public class GoogleLinkPhoneRequest {

    private String idToken;
    private String phoneNumber;
    /** 4-digit OTP the user entered (as a string; {@code otp} kept for numeric clients). */
    private String otpCode;
    private int otp;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public int getOtp() {
        return otp;
    }

    public void setOtp(int otp) {
        this.otp = otp;
    }

    /** Resolves the OTP the user submitted, preferring the string form. */
    public String resolveOtpCode() {
        String code = (otpCode != null && !otpCode.isBlank()) ? otpCode : String.valueOf(otp);
        return code != null && code.length() > 4 ? code.substring(0, 4) : code;
    }
}
