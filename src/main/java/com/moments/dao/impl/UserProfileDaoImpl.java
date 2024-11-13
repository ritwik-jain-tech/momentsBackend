package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

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
    public void createUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException {
        Long userId = getNextUserId();
        userProfile.setUserId(String.valueOf(userId));
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userProfile.getUserId());
        ApiFuture<WriteResult> result = docRef.set(userProfile);
        result.get(); // Wait for the operation to complete
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
