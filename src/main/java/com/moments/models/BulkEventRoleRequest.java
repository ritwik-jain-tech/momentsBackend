package com.moments.models;

import java.util.List;

public class BulkEventRoleRequest {
    private List<EventRoleItem> eventRoles;

    public BulkEventRoleRequest() {
    }

    public BulkEventRoleRequest(List<EventRoleItem> eventRoles) {
        this.eventRoles = eventRoles;
    }

    public List<EventRoleItem> getEventRoles() {
        return eventRoles;
    }

    public void setEventRoles(List<EventRoleItem> eventRoles) {
        this.eventRoles = eventRoles;
    }

    public static class EventRoleItem {
        private String userId;
        private String eventId;
        private String roleName;   // legacy free-form (optional fallback)
        private String roleType;   // COUPLE | GUEST | AGENCY (preferred)
        private String agencyRole; // CAMERAMAN/EDITOR/... when roleType == AGENCY

        public EventRoleItem() {
        }

        public EventRoleItem(String userId, String eventId, String roleName) {
            this.userId = userId;
            this.eventId = eventId;
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

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }
}
