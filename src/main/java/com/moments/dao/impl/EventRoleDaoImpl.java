package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.EventRoleDao;
import com.moments.models.EventRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class EventRoleDaoImpl implements EventRoleDao {

    private static final String COLLECTION_NAME = "EventRole";
    private final Firestore db;

    @Autowired
    public EventRoleDaoImpl(Firestore db) {
        this.db = db;
    }

    @Override
    public EventRole saveEventRole(EventRole eventRole) throws ExecutionException, InterruptedException {
        // Generate roleId as eventId_userId if not provided
        if (eventRole.getRoleId() == null || eventRole.getRoleId().isEmpty()) {
            String roleId = eventRole.getEventId() + "_" + eventRole.getUserId();
            eventRole.setRoleId(roleId);
        }
        
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(eventRole.getRoleId());
        ApiFuture<WriteResult> result = docRef.set(eventRole);
        result.get(); // Wait for the operation to complete
        return eventRole;
    }

    @Override
    public EventRole getEventRoleByEventIdAndUserId(String eventId, String userId) throws ExecutionException, InterruptedException {
        String roleId = eventId + "_" + userId;
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(roleId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                return document.toObject(EventRole.class);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void deleteEventRole(String roleId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(roleId);
        ApiFuture<WriteResult> result = docRef.delete();
        result.get(); // Wait for the operation to complete
    }
}
