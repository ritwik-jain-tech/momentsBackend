package com.moments.service;

import com.moments.dao.EventRoleDao;
import com.moments.models.BulkEventRoleRequest;
import com.moments.models.EventRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class EventRoleService {

    @Autowired
    private EventRoleDao eventRoleDao;

    public EventRole createOrUpdateEventRole(String eventId, String userId, String roleName) throws ExecutionException, InterruptedException {
        // If roleName is null or empty, default to "Guest"
        if (roleName == null || roleName.trim().isEmpty()) {
            roleName = "Guest";
        }
        
        String roleId = eventId + "_" + userId;
        EventRole eventRole = new EventRole(roleId, eventId, userId, roleName);
        return eventRoleDao.saveEventRole(eventRole);
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
            
            // createOrUpdateEventRole will handle both creation and update
            // If role already exists, it will update the roleName
            EventRole eventRole = createOrUpdateEventRole(item.getEventId(), item.getUserId(), item.getRoleName());
            createdOrUpdatedRoles.add(eventRole);
        }
        
        return createdOrUpdatedRoles;
    }
}
