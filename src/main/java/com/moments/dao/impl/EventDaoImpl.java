package com.moments.dao.impl;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.EventDao;
import com.moments.models.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Repository
public class EventDaoImpl implements EventDao {

    @Autowired
    private Firestore firestore;

    private static final String COLLECTION_NAME = "events";

    @Override
    public String saveEvent(Event event) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = event.getEventId() == null || event.getEventId().isEmpty()
                ? firestore.collection(COLLECTION_NAME).document()
                : firestore.collection(COLLECTION_NAME).document(event.getEventId());

        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            event.setEventId(documentReference.getId());
        }

        ApiFuture<WriteResult> future = documentReference.set(event);
        return future.get().getUpdateTime().toString();
    }

    @Override
    public Event getEventById(String id) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.toObject(Event.class);
        } else {
            throw new RuntimeException("Event not found with ID: " + id);
        }
    }

    @Override
    public List<Event> getAllEvents() throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> future = collection.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Event> events = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            events.add(document.toObject(Event.class));
        }
        return events;
    }

    @Override
    public void deleteEvent(String id) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<WriteResult> future = documentReference.delete();
        future.get();
    }

    @Override
    public Event addUserToEvent(String eventId, String userId, Boolean isGroomSide) throws ExecutionException, InterruptedException {
        try {
            Event event = getEventById(eventId);
            List<String> userIds = event.getUserIds()==null?new ArrayList<>():event.getUserIds();
            List<String> groomSide = event.getGroomSide()==null?new ArrayList<>():event.getGroomSide();
            List<String> brideSide = event.getBrideSide()==null?new ArrayList<>():event.getBrideSide();

            if (!userIds.contains(userId)) {
                userIds.add(userId);
                event.setUserIds(userIds);
                saveEvent(event);
            }
            if(isGroomSide && !groomSide.contains(userId)){
                groomSide.add(userId);
                event.setGroomSide(groomSide);
                saveEvent(event);
            }
            if(!isGroomSide && !brideSide.contains(userId)){
                brideSide.add(userId);
                event.setBrideSide(brideSide);
                saveEvent(event);
            }

            return event;
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public List<String> getUserIdsInEvent(String eventId) throws ExecutionException, InterruptedException {
        return getEventById(eventId).getUserIds();
    }

    
    @Override
    public List<Event> getEventsByIds(List<String> eventIds) throws ExecutionException, InterruptedException {
        List<Event> events = new ArrayList<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return events;
        }
        CollectionReference collection = firestore.collection(COLLECTION_NAME);

        // Firestore "in" queries are limited to 10 items per query
        int batchSize = 10;
        Map<String, Event> eventMap = new HashMap<>();
        for (int i = 0; i < eventIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, eventIds.size());
            List<String> batch = eventIds.subList(i, endIndex);

            Query query = collection.whereIn("eventId", batch);
            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                Event event = document.toObject(Event.class);
                if (event != null) {
                    eventMap.put(event.getEventId(), event);
                }
            }
        }
        // Maintain the same order as eventIds
        for (String id : eventIds) {
            Event event = eventMap.get(id);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    @Override
    public List<Event> getEventsByDocumentIds(List<String> documentIds) throws ExecutionException, InterruptedException {
        List<Event> ordered = new ArrayList<>();
        if (documentIds == null || documentIds.isEmpty()) {
            return ordered;
        }
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Map<String, Event> byDocId = new HashMap<>();
        final int batchSize = 10;
        for (int i = 0; i < documentIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documentIds.size());
            List<String> batch = documentIds.subList(i, end);
            List<DocumentReference> refs = new ArrayList<>(batch.size());
            for (String id : batch) {
                refs.add(collection.document(id));
            }
            ApiFuture<List<DocumentSnapshot>> future = firestore.getAll(refs.toArray(new DocumentReference[0]));
            List<DocumentSnapshot> snaps = future.get();
            for (DocumentSnapshot snap : snaps) {
                if (snap.exists()) {
                    Event event = snap.toObject(Event.class);
                    if (event != null) {
                        byDocId.put(snap.getId(), event);
                    }
                }
            }
        }
        for (String id : documentIds) {
            Event e = byDocId.get(id);
            if (e != null) {
                ordered.add(e);
            }
        }
        return ordered;
    }

    @Override
    public List<String> findEventIdsWhereUserIsMember(String userId) throws ExecutionException, InterruptedException {
        if (userId == null || userId.isBlank()) {
            return new ArrayList<>();
        }
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Set<String> ids = new LinkedHashSet<>();
        appendEventIdsFromUserIdsQuery(collection.whereArrayContains("userIds", userId).get(), ids);
        if (userId.chars().allMatch(Character::isDigit)) {
            try {
                long asLong = Long.parseLong(userId);
                appendEventIdsFromUserIdsQuery(collection.whereArrayContains("userIds", asLong).get(), ids);
            } catch (NumberFormatException ignored) {
                // not a usable long
            }
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private static void appendEventIdsFromUserIdsQuery(ApiFuture<QuerySnapshot> future, Set<String> out)
            throws ExecutionException, InterruptedException {
        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            out.add(document.getId());
        }
    }

    @Override
    public void deleteUserForEvents(String userId, List<String> eventIds) throws ExecutionException, InterruptedException {
       List<Event> events = getEventsByIds(eventIds);
       for (Event event : events) {
           event.getUserIds().remove(userId);
           saveEvent(event);
       }
    }

    @Override
    public void adjustAggregatedStorage(String eventId, long deltaOriginal, long deltaOptimised, long deltaThumbnail)
            throws ExecutionException, InterruptedException {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        if (deltaOriginal == 0L && deltaOptimised == 0L && deltaThumbnail == 0L) {
            return;
        }
        DocumentReference ref = firestore.collection(COLLECTION_NAME).document(eventId);
        Map<String, Object> updates = new HashMap<>();
        if (deltaOriginal != 0L) {
            updates.put("aggregatedStorage.originalUploadSizeBytes", FieldValue.increment(deltaOriginal));
        }
        if (deltaOptimised != 0L) {
            updates.put("aggregatedStorage.optimisedSizeBytes", FieldValue.increment(deltaOptimised));
        }
        if (deltaThumbnail != 0L) {
            updates.put("aggregatedStorage.thumbnailSizeBytes", FieldValue.increment(deltaThumbnail));
        }
        ref.update(updates).get();
    }


    public List<Event> getEventsByTimeRange(Long startTime, Long endTime) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.orderBy("startTime", Query.Direction.ASCENDING);
        
        if (startTime != null) {
            query = query.whereGreaterThanOrEqualTo("startTime", startTime);
        }
        if (endTime != null) {
            query = query.whereLessThanOrEqualTo("startTime", endTime);
        }
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Event> events = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            events.add(document.toObject(Event.class));
        }
        return events;
    }


    public List<Event> getOngoingEvents(Long currentTime) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.whereLessThanOrEqualTo("startTime", currentTime)
                               .whereGreaterThanOrEqualTo("endTime", currentTime)
                               .orderBy("startTime", Query.Direction.DESCENDING);
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Event> events = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            events.add(document.toObject(Event.class));
        }
        return events;
    }


    public List<Event> getPastEvents(Long currentTime) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        Query query = collection.whereLessThan("endTime", currentTime)
                               .orderBy("endTime", Query.Direction.DESCENDING);
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Event> events = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            events.add(document.toObject(Event.class));
        }
        return events;
    }
}


