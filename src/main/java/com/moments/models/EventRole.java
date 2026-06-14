package com.moments.models;

public class EventRole {
    private String roleId;  // Firestore document ID: eventId_userId
    private String eventId;
    private String userId;
    private String roleName;  // Legacy free-form string ("admin", "Guest", "Photographer") — kept for the moments feed.
    private String roleType;  // EventRoleType: COUPLE | GUEST | AGENCY
    private String agencyRole; // AgencyRole when roleType == AGENCY (CAMERAMAN/EDITOR/...), else null

    // Default constructor
    public EventRole() {
    }

    // Constructor with all fields
    public EventRole(String roleId, String eventId, String userId, String roleName) {
        this.roleId = roleId;
        this.eventId = eventId;
        this.userId = userId;
        this.roleName = roleName;
    }

    // Getters and Setters
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getAgencyRole() {
        return agencyRole;
    }

    public void setAgencyRole(String agencyRole) {
        this.agencyRole = agencyRole;
    }
}
