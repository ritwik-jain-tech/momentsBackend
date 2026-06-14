package com.moments.service;

import com.moments.dao.EventRoleDao;
import com.moments.models.AgencyRole;
import com.moments.models.BulkEventRoleRequest;
import com.moments.models.EventRole;
import com.moments.models.EventRoleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class EventRoleService {

    @Autowired
    private EventRoleDao eventRoleDao;

    /**
     * Legacy entry point: persists by free-form {@code roleName} and derives the structured
     * {@code roleType} (and {@code agencyRole}, when the name is an agency sub-role) so existing
     * callers (event creator "admin", Drive import "Photographer", guest adds) auto-populate the
     * new taxonomy. {@code roleName} is preserved verbatim for the moments-feed consumers.
     */
    public EventRole createOrUpdateEventRole(String eventId, String userId, String roleName) throws ExecutionException, InterruptedException {
        // If roleName is null or empty, default to "Guest"
        if (roleName == null || roleName.trim().isEmpty()) {
            roleName = "Guest";
        }

        EventRoleType roleType = EventRoleType.fromLegacy(roleName);
        AgencyRole agencyRole = roleType == EventRoleType.AGENCY ? AgencyRole.fromString(roleName) : null;

        String roleId = eventId + "_" + userId;
        EventRole eventRole = new EventRole(roleId, eventId, userId, roleName);
        eventRole.setRoleType(roleType.name());
        eventRole.setAgencyRole(agencyRole != null ? agencyRole.name() : null);
        return eventRoleDao.saveEventRole(eventRole);
    }

    /**
     * Structured entry point: persists by {@code roleType} (+ optional {@code agencyRole}) and
     * keeps the legacy {@code roleName} in sync so downstream consumers keep working.
     */
    public EventRole createOrUpdateEventRole(String eventId, String userId, EventRoleType roleType, AgencyRole agencyRole)
            throws ExecutionException, InterruptedException {
        if (roleType == null) {
            roleType = EventRoleType.GUEST;
        }
        if (roleType != EventRoleType.AGENCY) {
            agencyRole = null;
        }

        String roleId = eventId + "_" + userId;
        EventRole eventRole = new EventRole(roleId, eventId, userId, legacyName(roleType, agencyRole));
        eventRole.setRoleType(roleType.name());
        eventRole.setAgencyRole(agencyRole != null ? agencyRole.name() : null);
        return eventRoleDao.saveEventRole(eventRole);
    }

    /**
     * Bridges the structured taxonomy back to a legacy {@code roleName} string. AGENCY members
     * map to their capitalized sub-role (e.g. "Editor") or "Agency"; COUPLE/GUEST map to
     * "Couple"/"Guest". Keeps the moments feed's role filtering working unchanged.
     */
    static String legacyName(EventRoleType roleType, AgencyRole agencyRole) {
        if (roleType == EventRoleType.AGENCY) {
            if (agencyRole == null) {
                return "Agency";
            }
            String s = agencyRole.name().toLowerCase();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        if (roleType == EventRoleType.COUPLE) {
            return "Couple";
        }
        return "Guest";
    }

    public EventRole getEventRole(String eventId, String userId) throws ExecutionException, InterruptedException {
        return eventRoleDao.getEventRoleByEventIdAndUserId(eventId, userId);
    }
    
    public String getRoleName(String eventId, String userId) throws ExecutionException, InterruptedException {
        EventRole eventRole = getEventRole(eventId, userId);
        if (eventRole == null) {
            return "Guest";
        }
        return eventRole.getRoleName();
    }
    
    public List<EventRole> bulkCreateOrUpdateEventRoles(BulkEventRoleRequest request) throws ExecutionException, InterruptedException {
        List<EventRole> createdOrUpdatedRoles = new ArrayList<>();
        
        if (request == null || request.getEventRoles() == null || request.getEventRoles().isEmpty()) {
            return createdOrUpdatedRoles;
        }
        
        for (BulkEventRoleRequest.EventRoleItem item : request.getEventRoles()) {
            if (item.getUserId() == null || item.getEventId() == null) {
                // Skip invalid items (missing userId or eventId)
                continue;
            }
            
            // Prefer the structured taxonomy when the caller supplies roleType; otherwise fall
            // back to the legacy roleName path (which still derives roleType under the hood).
            EventRole eventRole;
            if (item.getRoleType() != null && !item.getRoleType().isBlank()) {
                EventRoleType roleType = EventRoleType.valueOf(item.getRoleType().trim().toUpperCase());
                AgencyRole agencyRole = AgencyRole.fromString(item.getAgencyRole());
                eventRole = createOrUpdateEventRole(item.getEventId(), item.getUserId(), roleType, agencyRole);
            } else {
                eventRole = createOrUpdateEventRole(item.getEventId(), item.getUserId(), item.getRoleName());
            }
            createdOrUpdatedRoles.add(eventRole);
        }
        
        return createdOrUpdatedRoles;
    }
}
