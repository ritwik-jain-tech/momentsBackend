package com.moments.models;

/**
 * A person on a studio/agency's team. Agency-scoped: every member belongs to the
 * studio owner identified by {@code agencyId} (the owner's userId). Tasks are
 * assigned to members via {@code memberId}.
 */
public class TeamMember {
    private String memberId;   // Firestore document ID
    private String agencyId;   // owner userId that this member belongs to
    private String name;
    private String email;
    private String phone;
    private String role;       // "Cameraman", "Editor", "Reviewer", "Retoucher", "Manager", ...
    private String avatarUrl;
    private String status;     // "ACTIVE", "INVITED", "INACTIVE"
    private String linkedUserId; // optional: links member to an actual UserProfile
    private Long createdAt;    // epoch millis

    public TeamMember() {
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(String linkedUserId) {
        this.linkedUserId = linkedUserId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
