package com.moments.dao.impl;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.EventDao;
import com.moments.models.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
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
    public Event addUserToEvent(String eventId, String userId) throws ExecutionException, InterruptedException {
        try {
            Event event = getEventById(eventId);
            List<String> userIds = event.getUserIds();
            if(userIds==null){
                userIds = new ArrayList<>();
            }
            if (!userIds.contains(userId)) {
                userIds.add(userId);
                event.setUserIds(userIds);
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
}


