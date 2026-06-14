package com.moments.models;

/**
 * Sub-role of an {@link EventRoleType#AGENCY} member within an agency. Shares the
 * same vocabulary as {@code TeamMember.role} and the studio dashboard's role filter
 * (Cameraman / Editor / Reviewer / Retoucher / Manager).
 */
public enum AgencyRole {
    CAMERAMAN,
    EDITOR,
    REVIEWER,
    RETOUCHER,
    MANAGER;

    /** Lenient parse from any-case string; null when unrecognized/blank. */
    public static AgencyRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgencyRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
