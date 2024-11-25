package com.moments.dao;

import com.moments.models.Moment;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface MomentDao {
    String saveMoment(Moment moment) throws ExecutionException, InterruptedException;

    Moment getMomentById(String id) throws ExecutionException, InterruptedException;

    List<Moment> getAllMoments() throws ExecutionException, InterruptedException;

    void deleteMoment(String id) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsFeed(String creatorUserId, String eventId, int offset, int limit) throws ExecutionException, InterruptedException;

    int getTotalCount(String creatorUserId, String eventId) throws ExecutionException, InterruptedException;
}



