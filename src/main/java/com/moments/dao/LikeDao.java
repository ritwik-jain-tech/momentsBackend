package com.moments.dao;

import com.moments.models.Like;
import com.moments.models.Moment;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface LikeDao {
    String saveLike(Like like) throws ExecutionException, InterruptedException;
    
    boolean deleteLike(String userId, String momentId) throws ExecutionException, InterruptedException;
    
    boolean isLikedByUser(String userId, String momentId) throws ExecutionException, InterruptedException;
    
    List<Like> getLikesByUser(String userId, String eventId, int offset, int limit) throws ExecutionException, InterruptedException;
    
    int getTotalLikesByUser(String userId, String eventId) throws ExecutionException, InterruptedException;
    
    void updateMomentLikedBy(String momentId, String userId, boolean add) throws ExecutionException, InterruptedException;
} 