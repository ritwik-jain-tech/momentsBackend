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
        private String roleName;

        public EventRoleItem() {
        }

        public EventRoleItem(String userId, String eventId, String roleName) {
            this.userId = userId;
            this.eventId = eventId;
            this.roleName = roleName;
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
