package com.moments.dao;


import com.moments.models.Event;
import com.moments.models.UserProfile;

import java.util.List;
import java.util.concurrent.ExecutionException;
public interface EventDao {
    String saveEvent(Event event) throws ExecutionException, InterruptedException;

    Event getEventById(String id) throws ExecutionException, InterruptedException;

    List<Event> getAllEvents() throws ExecutionException, InterruptedException;

    void deleteEvent(String id) throws ExecutionException, InterruptedException;

    Event addUserToEvent(String eventId, String userId, Boolean isGroomSide) throws ExecutionException, InterruptedException;

    List<String> getUserIdsInEvent(String eventId) throws ExecutionException, InterruptedException;

    List<Event> getEventsByIds(List<String> eventIds) throws ExecutionException, InterruptedException;
}

