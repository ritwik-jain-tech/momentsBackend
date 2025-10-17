package com.moments.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.moments.models.Moment;
import com.moments.models.MomentStatus;
import com.moments.models.ReportRequest;

public interface MomentDao {
    String saveMoment(Moment moment) throws ExecutionException, InterruptedException;

    Moment getMomentById(String id) throws ExecutionException, InterruptedException;

    List<Moment> getAllMoments() throws ExecutionException, InterruptedException;

    List<Moment> getAllMoments(String eventId) throws ExecutionException, InterruptedException;

    void deleteMoment(String id) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsFeed(String creatorUserId, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException;

    int getTotalCount(String creatorUserId, String eventId) throws ExecutionException, InterruptedException;

    boolean reportMoment(ReportRequest request) throws ExecutionException, InterruptedException;

    String updateMomentStatus(String momentId, MomentStatus status) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsByIds(List<String> momentIds) throws ExecutionException, InterruptedException;

    List<String> saveMomentsBatch(List<Moment> moments) throws ExecutionException, InterruptedException;
}
