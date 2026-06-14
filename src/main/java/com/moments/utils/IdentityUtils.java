package com.moments.utils;

/**
 * Shared normalization for the natural identity keys of a {@code UserProfile}:
 * email (lowercased) and phone (last 10 digits). A single human is deduplicated
 * across login methods (Google/email, OTP/phone) by these keys plus firebaseUid.
 */
public final class IdentityUtils {

    private IdentityUtils() {
    }

    /** Lowercased, trimmed email; {@code null} when blank. */
    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String e = raw.trim().toLowerCase();
        return e.isEmpty() ? null : e;
    }

    /**
     * Keeps the last 10 digits when longer (common for {@code +91…}); aligns with
     * existing OTP validation. {@code null} when no digits.
     */
    public static String normalizeTenDigitPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() >= 10) {
            return d.substring(d.length() - 10);
        }
        return d.isEmpty() ? null : d;
    }

    public static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
