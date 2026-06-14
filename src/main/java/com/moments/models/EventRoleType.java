package com.moments.models;

/**
 * The role a user holds with respect to a specific event:
 * <ul>
 *   <li>{@code COUPLE}  — the couple / hosts the event is for (groom/bride side).</li>
 *   <li>{@code GUEST}   — an attendee who joins to view/upload moments.</li>
 *   <li>{@code AGENCY}  — a member of the photography/media agency working the event;
 *       carries an {@link AgencyRole} sub-role.</li>
 * </ul>
 *
 * Layered on top of the legacy free-form {@code EventRole.roleName} string (which the
 * moments feed still filters on) — see {@code EventRoleService} for the bridge.
 */
public enum EventRoleType {
    COUPLE,
    GUEST,
    AGENCY;

    /** Best-effort mapping from the historical {@code roleName} strings. Never null. */
    public static EventRoleType fromLegacy(String roleName) {
        if (roleName == null) {
            return GUEST;
        }
        String r = roleName.trim().toLowerCase();
        switch (r) {
            case "admin":
            case "agency":
            case "photographer":
            case "cameraman":
            case "editor":
            case "reviewer":
            case "retoucher":
            case "manager":
                return AGENCY;
            case "couple":
            case "groom":
            case "bride":
            case "groomside":
            case "brideside":
                return COUPLE;
            default:
                return GUEST;
        }
    }
}
