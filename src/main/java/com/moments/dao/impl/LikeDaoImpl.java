package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.LikeDao;
import com.moments.models.Like;
import com.moments.models.Moment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class LikeDaoImpl implements LikeDao {

    @Autowired
    private Firestore firestore;

    private static final String LIKES_COLLECTION = "likes";
    private static final String MOMENTS_COLLECTION = "moments";

    @Override
    public String saveLike(Like like) throws ExecutionException, InterruptedException {
        like.setLikeId(like.getLikedBy()+"_"+like.getLikedMoment());
        DocumentReference documentReference = firestore.collection(LIKES_COLLECTION).document(like.getLikeId());

        
        ApiFuture<WriteResult> future = documentReference.set(like);
        future.get();
        
        return like.getLikeId();
    }

    @Override
    public boolean deleteLike(String userId, String momentId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(LIKES_COLLECTION);
        Query query = collection.whereEqualTo("likedBy", userId)
                               .whereEqualTo("likedMoment", momentId);
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        if (!documents.isEmpty()) {
            DocumentReference docRef = documents.get(0).getReference();
            ApiFuture<WriteResult> deleteFuture = docRef.delete();
            deleteFuture.get();
            return true;
        }
        return false;
    }

    @Override
    public boolean isLikedByUser(String userId, String momentId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(LIKES_COLLECTION);
        Query query = collection.whereEqualTo("likedBy", userId)
                               .whereEqualTo("likedMoment", momentId);
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        return !documents.isEmpty();
    }

    @Override
    public List<Like> getLikesByUser(String userId, String eventId, int offset, int limit) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(LIKES_COLLECTION);
        Query query;
        
        if (eventId != null && !eventId.isEmpty()) {
            // Query with both userId and eventId
            query = collection.whereEqualTo("likedBy", userId)
                             .whereEqualTo("eventId", eventId)
                             .orderBy("momentCreationTime", Query.Direction.DESCENDING);
        } else {
            // Query with only userId
            query = collection.whereEqualTo("likedBy", userId)
                             .orderBy("momentCreationTime", Query.Direction.DESCENDING);
        }
        
        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        List<Like> likes = new ArrayList<>();
        int startIndex = Math.min(offset, documents.size());
        int endIndex = Math.min(startIndex + limit, documents.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            likes.add(documents.get(i).toObject(Like.class));
        }
        
        return likes;
    }

    @Override
    public int getTotalLikesByUser(String userId, String eventId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(LIKES_COLLECTION);
        Query query;
        
        if (eventId != null && !eventId.isEmpty()) {
            query = collection.whereEqualTo("likedBy", userId)
                             .whereEqualTo("eventId", eventId);
        } else {
            query = collection.whereEqualTo("likedBy", userId);
        }
        
        ApiFuture<QuerySnapshot> future = query.get();
        return future.get().size();
    }

    @Override
    public void updateMomentLikedBy(String momentId, String userId, boolean add) throws ExecutionException, InterruptedException {
        DocumentReference momentRef = firestore.collection(MOMENTS_COLLECTION).document(momentId);
        
        if (add) {
            // Add userId to likedBy set
            momentRef.update("likedBy", FieldValue.arrayUnion(userId)).get();
        } else {
            // Remove userId from likedBy set
            momentRef.update("likedBy", FieldValue.arrayRemove(userId)).get();
        }
    }
} 