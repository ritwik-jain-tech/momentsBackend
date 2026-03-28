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

    /**
     * Loads events by Firestore document path (collection document IDs), preserving {@code documentIds} order.
     */
    List<Event> getEventsByDocumentIds(List<String> documentIds) throws ExecutionException, InterruptedException;

    /**
     * Event document IDs where {@code userIds} contains {@code userId} (membership on the event).
     * Aligns with {@link com.moments.service.MomentService#getEventStorageSummary} access rules.
     */
    List<String> findEventIdsWhereUserIsMember(String userId) throws ExecutionException, InterruptedException;

    void deleteUserForEvents(String userId, List<String> eventIds) throws ExecutionException, InterruptedException;

    /**
     * Atomically adjusts {@link com.moments.models.Event#getAggregatedStorage()} using Firestore increments.
     * Negative deltas reduce totals when moments are deleted or sizes shrink.
     */
    void adjustAggregatedStorage(String eventId, long deltaOriginal, long deltaOptimised, long deltaThumbnail)
            throws ExecutionException, InterruptedException;
}

