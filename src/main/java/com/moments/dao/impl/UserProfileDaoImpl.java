package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class UserProfileDaoImpl implements UserProfileDao {

    private static final String COLLECTION_NAME = "UserProfile";
    private final Firestore db;

    @Autowired
    public UserProfileDaoImpl(Firestore db) {
        this.db = db;
    }

    @Override
    public Long createUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException {
        Long userId = getNextUserId();
        userProfile.setUserId(String.valueOf(userId));
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userProfile.getUserId());
        ApiFuture<WriteResult> result = docRef.set(userProfile);
        result.get();// Wait for the operation to complete
       return userId;
    }
    private synchronized Long getNextUserId() throws ExecutionException, InterruptedException {
        DocumentReference counterDocRef = db.collection("Counters").document("UserProfileCounter");

        // Run a transaction to increment the counter atomically
        ApiFuture<Long> nextUserIdTransaction = db.runTransaction(trx -> {
            DocumentSnapshot snapshot = trx.get(counterDocRef).get();
            Long lastUserId = snapshot.getLong("lastUserId");
            Long newUserId = lastUserId != null ? lastUserId + 1 : 1;

            // Update the counter in Firestore
            trx.update(counterDocRef, "lastUserId", newUserId);
            return newUserId;
        });

        return nextUserIdTransaction.get();
    }

    @Override
    public UserProfile getUserProfile(String userId)  {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                return document.toObject(UserProfile.class);
            } else {
                return null; // or throw an exception
            }
        } catch (Exception e ){
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<UserProfile> getUserProfiles(List<String> userIds) throws ExecutionException, InterruptedException {
        List<UserProfile> userProfiles = new ArrayList<>();

        if (userIds == null || userIds.isEmpty()) {
            return userProfiles; // Return an empty list if input is null or empty
        }

        try {
            // Create document references for all userIds
            List<DocumentReference> docRefs = new ArrayList<>();
            for (String userId : userIds) {
                docRefs.add(db.collection(COLLECTION_NAME).document(userId));
            }

            // Fetch all documents in a batch
            ApiFuture<List<DocumentSnapshot>> future = db.getAll(docRefs.toArray(new DocumentReference[0]));
            List<DocumentSnapshot> documents = future.get();

            // Process each document
            for (DocumentSnapshot document : documents) {
                if (document.exists()) {
                    UserProfile userProfile = document.toObject(UserProfile.class);
                    if (userProfile != null) {
                        userProfiles.add(userProfile);
                    }
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace(); // Log the exception
        }

        return userProfiles;
    }


    @Override
    public void updateUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userProfile.getUserId());
        ApiFuture<WriteResult> result = docRef.set(userProfile);
        result.get(); // Wait for the operation to complete
    }

    @Override
    public void deleteUserProfile(String userId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);
        ApiFuture<WriteResult> result = docRef.delete();
        result.get(); // Wait for the operation to complete
    }

    @Override
    public UserProfile addUserToEvent(String userId, String eventId) throws ExecutionException, InterruptedException {
        UserProfile userProfile = getUserProfile(userId);
        if(userProfile!=null && !userProfile.getEventIds().contains(eventId)){
            userProfile.getEventIds().add(eventId);
            updateUserProfile(userProfile);
        }
        return userProfile;
    }

    @Override
    public void blockUser(String blockingUserId, String blockedUserId) throws ExecutionException, InterruptedException {
        UserProfile userProfile = getUserProfile(blockingUserId);
        if(userProfile!=null && userProfile.getBlockedUserIds()!=null && !userProfile.getBlockedUserIds().contains(blockedUserId)){
            userProfile.getBlockedUserIds().add(blockedUserId);
            updateUserProfile(userProfile);
        }
        if (userProfile.getBlockedUserIds()==null){
            userProfile.setBlockedUserIds(new ArrayList<>());
            userProfile.getBlockedUserIds().add(blockedUserId);
            updateUserProfile(userProfile);
        }

    }

    @Override
    public void unblockUser(String unblockingUserId, String blockedUserId) throws ExecutionException, InterruptedException {
        UserProfile userProfile = getUserProfile(unblockingUserId);
        if(userProfile!=null && userProfile.getBlockedUserIds()!=null&& userProfile.getBlockedUserIds().contains(blockedUserId)){
            userProfile.getBlockedUserIds().remove(blockedUserId);
            updateUserProfile(userProfile);
        }
    }


    @Override
    public UserProfile findByPhoneNumber(String phoneNumber) {
        try {
            CollectionReference userProfiles = db.collection("UserProfile");
            Query query = userProfiles.whereEqualTo("phoneNumber", phoneNumber);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();

            if (querySnapshot.get().isEmpty()) {
                return null;
            }

            DocumentSnapshot document = querySnapshot.get().getDocuments().get(0);
            UserProfile userProfile = document.toObject(UserProfile.class);
            return userProfile;

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }
}
