package com.moments.dao;

import com.moments.models.EventRole;
import java.util.concurrent.ExecutionException;

public interface EventRoleDao {
    EventRole saveEventRole(EventRole eventRole) throws ExecutionException, InterruptedException;
    
    EventRole getEventRoleByEventIdAndUserId(String eventId, String userId) throws ExecutionException, InterruptedException;
    
    void deleteEventRole(String roleId) throws ExecutionException, InterruptedException;
}
