package com.moments.service;

import com.moments.dao.EventDao;
import com.moments.dao.UserProfileDao;
import com.moments.models.Event;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
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
        String eventId = String.format("%06d", (int)(Math.random() * 1000000));
        event.setEventId(eventId);
        List<String> userIDs = new ArrayList<>();
        userIDs.add(event.getCreatorId());
        event.setUserIds(userIDs);
        return eventDao.saveEvent(event);
    }

    // Get an Event by ID
    public Event getEventById(String id) throws ExecutionException, InterruptedException {
        return eventDao.getEventById(id);
    }

    // Get all Events
    public List<Event> getAllEvents() throws ExecutionException, InterruptedException {
        return eventDao.getAllEvents();
    }

    // Delete an Event by ID
    public void deleteEvent(String id) throws ExecutionException, InterruptedException {
        eventDao.deleteEvent(id);
    }

    public Event addUserToEvent(String userId, String eventId) throws ExecutionException, InterruptedException {
        UserProfile userProfile = userProfileDao.addUserToEvent(userId, eventId);
        Event event = eventDao.addUserToEvent(eventId, userId);
        return event;
    }

    public List<UserProfile> getAllUserProfilesInEvent(String eventId) throws ExecutionException, InterruptedException {
        List<String> userIds = eventDao.getUserIdsInEvent(eventId);
        return userProfileDao.getUserProfiles(userIds);
    }
}

