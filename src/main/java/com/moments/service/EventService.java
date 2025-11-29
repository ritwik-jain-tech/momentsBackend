package com.moments.service;

import com.moments.dao.EventDao;
import com.moments.dao.UserProfileDao;
import com.moments.models.Event;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class EventService {

    @Autowired
    private EventDao eventDao;
    @Autowired
    private UserProfileDao userProfileDao;// Inject DAO interface
    
    @Autowired
    private UserProfileService userProfileService;
    
    @Autowired
    private EventRoleService eventRoleService;

    // Create or Update an Event
    public String saveEvent(Event event) throws ExecutionException, InterruptedException, InvocationTargetException {
        // Generate event ID if not provided
        boolean isNewEvent = (event.getEventId() == null || event.getEventId().isEmpty());
        if (isNewEvent) {
            String eventId = String.format("%06d", (int)(Math.random() * 1000000));
            event.setEventId(eventId);
        }
        
        // Set default times if not provided
        if (event.getStartTime() == null) {
            event.setStartTime(Instant.now().toEpochMilli());
        }
        if (event.getEndTime() == null) {
            // Default to 48 hours from start time
            event.setEndTime(event.getStartTime() + (48 * 60 * 60 * 1000));
        }
        
        // Initialize user list if not provided
        if (event.getUserIds() == null) {
            List<String> userIDs = new ArrayList<>();
            userIDs.add(event.getCreatorId());
            event.setUserIds(userIDs);
        }
        
        String result = eventDao.saveEvent(event);
        
        // Ensure creator has admin role for this event
        if (event.getCreatorId() != null && !event.getCreatorId().isEmpty()) {
            try {
                eventRoleService.createOrUpdateEventRole(event.getEventId(), event.getCreatorId(), "admin");
            } catch (Exception e) {
                // Log error but don't fail event creation if role creation fails
                // This ensures event creation succeeds even if role creation has issues
                System.err.println("Warning: Failed to create admin role for event creator: " + e.getMessage());
            }
        }
        
        return result;
    }

    // Get an Event by ID
    public Event getEventById(String id) throws ExecutionException, InterruptedException {
        Event event = eventDao.getEventById(id);
        if("123456".equals(id)){
            // Promotion event: users should see themselves and specific userIds
           List<String> userIds = new ArrayList<>();
            // Add the requesting user
            userIds.add("11");
            userIds.add("10");
            userIds.add("23");
            userIds.add("37");
            userIds.add("46");
            event.setUserIds(userIds);
       }
        return event;

    }

    // Get all Events
    public List<Event> getAllEvents() throws ExecutionException, InterruptedException {
        return eventDao.getAllEvents();
    }

    // Delete an Event by ID
    public void deleteEvent(String id) throws ExecutionException, InterruptedException {
        eventDao.deleteEvent(id);
    }

    public Event addUserToEvent(String userId, String eventId, Boolean isGroomSide) throws ExecutionException, InterruptedException {
        return addUserToEvent(userId, eventId, isGroomSide, null);
    }
    
    public Event addUserToEvent(String userId, String eventId, Boolean isGroomSide, String roleName) throws ExecutionException, InterruptedException {
        userProfileService.addUserToEvent(userId, eventId, isGroomSide, roleName);
        Event event = eventDao.addUserToEvent(eventId, userId, isGroomSide);
        return event;
    }

    public List<UserProfile> getAllUserProfilesInEvent(String eventId, String userId) throws ExecutionException, InterruptedException {
        List<String> userIds;
        
        // Special handling for promotion event (eventId: "123456")
        if ("123456".equals(eventId)  ) {
            // Promotion event: users should see themselves and specific userIds
            userIds = new ArrayList<>();
            if(userId != null)
                userIds.add(userId);
            // Add the requesting user
            userIds.add("11");
            userIds.add("10");
            userIds.add("23");
            userIds.add("37");
            userIds.add("46");
        } else {
            userIds = eventDao.getUserIdsInEvent(eventId);
        }
        
        return userProfileDao.getUserProfiles(userIds);
    }
    
    public Map<String, List<UserProfile>> getAllUserProfilesInEventGroupedByRole(String eventId) throws ExecutionException, InterruptedException {
        List<String> userIds = eventDao.getUserIdsInEvent(eventId);
        List<UserProfile> userProfiles = userProfileDao.getUserProfiles(userIds);
        
        // Group users by their role names
        Map<String, List<UserProfile>> usersByRole = new HashMap<>();
        
        for (UserProfile userProfile : userProfiles) {
            String roleName = eventRoleService.getRoleName(eventId, userProfile.getUserId());
            usersByRole.computeIfAbsent(roleName, k -> new ArrayList<>()).add(userProfile);
        }
        
        return usersByRole;
    }
    
    public Map<String, List<UserProfile>> getUsersByRoleName(String eventId, String roleName) throws ExecutionException, InterruptedException {
        List<String> userIds = eventDao.getUserIdsInEvent(eventId);
        List<UserProfile> userProfiles = userProfileDao.getUserProfiles(userIds);
        
        // Filter users by the specified role name
        List<UserProfile> usersWithRole = new ArrayList<>();
        for (UserProfile userProfile : userProfiles) {
            String userRoleName = eventRoleService.getRoleName(eventId, userProfile.getUserId());
            if (roleName != null && roleName.equalsIgnoreCase(userRoleName)) {
                usersWithRole.add(userProfile);
            }
        }
        
        // Return as a map with roleName as key
        Map<String, List<UserProfile>> result = new HashMap<>();
        result.put(roleName, usersWithRole);
        
        return result;
    }
    
    public List<UserProfile> getAllUserProfilesInEventWithRoles(String eventId, String userId) throws ExecutionException, InterruptedException {
        String userRoleName = null;
        
        // If userId is provided, fetch their role for this event
        if (userId != null && !userId.isEmpty()) {
            try {
                userRoleName = eventRoleService.getRoleName(eventId, userId);
            } catch (Exception e) {
                // If error fetching role, default to "Guest"
                userRoleName = "Guest";
            }
        } else {
            // If userId is not provided, default to "Guest"
            userRoleName = "Guest";
        }
        
        List<String> userIds;
        
        // Special handling for promotion event (eventId: "123456")
        if ("123456".equals(eventId)) {
            userIds = new ArrayList<>();
            if (userId != null) {
                userIds.add(userId);
            }
            userIds.add("11");
            userIds.add("10");
            userIds.add("23");
            userIds.add("37");
            userIds.add("46");
        } else {
            userIds = eventDao.getUserIdsInEvent(eventId);
        }
        
        List<UserProfile> userProfiles = userProfileDao.getUserProfiles(userIds);
        
        // Filter and set role names based on requesting user's role
        List<UserProfile> filteredUsers = new ArrayList<>();
        for (UserProfile userProfile : userProfiles) {
            String profileRoleName = eventRoleService.getRoleName(eventId, userProfile.getUserId());
            userProfile.setEventRoleName(profileRoleName);
            
            // If requesting user is admin, include all users
            // Otherwise, only include users with the same role as the requesting user
            if (userRoleName != null && userRoleName.equalsIgnoreCase("admin")) {
                filteredUsers.add(userProfile);
            } else if (userRoleName != null && userRoleName.equalsIgnoreCase(profileRoleName)) {
                filteredUsers.add(userProfile);
            } else if (userRoleName == null) {
                // If no userId provided, show all users
                filteredUsers.add(userProfile);
            }
        }
        
        return filteredUsers;
    }
}

