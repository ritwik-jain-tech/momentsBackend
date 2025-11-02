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
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class EventService {

    @Autowired
    private EventDao eventDao;
    @Autowired
    private UserProfileDao userProfileDao;// Inject DAO interface

    // Create or Update an Event
    public String saveEvent(Event event) throws ExecutionException, InterruptedException, InvocationTargetException {
        // Generate event ID if not provided
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
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
        
        return eventDao.saveEvent(event);
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
        UserProfile userProfile = userProfileDao.addUserToEvent(userId, eventId);
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
}

