package com.moments.service;

import com.moments.dao.MomentDao;
import com.moments.dao.LikeDao;
import com.moments.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;

@Service
public class MomentService {

    @Autowired
    private MomentDao momentDao;
    
    @Autowired
    private LikeDao likeDao;

    // Create or Update a Moment
    public String saveMoment(Moment moment) throws ExecutionException, InterruptedException {
        moment.setUploadTime(Instant.now().toEpochMilli());
        if(moment.getCreationTime()==null){
            moment.setCreationTime(moment.getUploadTime());
        }
        moment.setStatus(MomentStatus.APPROVED);
        double aspectRatio = 0.4 + (Math.random() * 0.7);
        moment.setAspectRatio(Double.doubleToLongBits(aspectRatio));
        moment.setCreationTimeText(epocToString(moment.getCreationTime()));
        moment.setUploadTimeText(epocToString(moment.getUploadTime()));
        moment.setMomentId(generateMomentId(moment.getCreatorId()));
        return momentDao.saveMoment(moment);
    }


    public List<String> saveMoments(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> ids = new ArrayList<>();
        for (Moment moment : moments) {
            String id = saveMoment(moment); // Reuse the single save logic
            ids.add(id);
        }
        return ids;
    }

    public Boolean reportMoment(ReportRequest reportRequest) throws ExecutionException, InterruptedException {
       return momentDao.reportMoment(reportRequest);
    }

    private String generateMomentId(String creatorId) {
        return creatorId+"_"+epocToString(Instant.now().toEpochMilli());
    }

    // Get a Moment by ID
    public Moment getMomentById(String id) throws ExecutionException, InterruptedException {
        return momentDao.getMomentById(id);
    }

    // Get all Moments
    public List<Moment> getAllMoments() throws ExecutionException, InterruptedException {
        return momentDao.getAllMoments();
    }

    // Delete a Moment by ID
    public void deleteMoment(String id) throws ExecutionException, InterruptedException {
        momentDao.deleteMoment(id);
    }

    public MomentsResponse findMoments(String eventId, MomentFilter filter, Cursor cursor, String userId) throws ExecutionException, InterruptedException {
        int limit = cursor == null ? 20 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        String creatorId = filter == null ? null: filter.getCreatedById();
        String source = filter == null ? null: filter.getSource();
        List<Moment> moments = Objects.equals(source, "web") ?momentDao.getAllMoments(eventId)
                :momentDao.getMomentsFeed(creatorId, eventId, offset, limit);
        for (Moment moment : moments) {
            moment.setIsLiked(moment.getLikedBy() != null && moment.getLikedBy().contains(userId));
        }
        int totalCount = momentDao.getTotalCount(creatorId, eventId);
        boolean isLastPage= moments.size()<limit;
        Long  lastMomentCreationTime = moments.isEmpty() ? null : moments.get(moments.size()-1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset+moments.size(), limit,lastMomentCreationTime, isLastPage);
        return new MomentsResponse(moments, cursorOut);
    }

    private String epocToString(Long epoc){
        return Instant.ofEpochSecond(epoc/1000)
                .atZone(ZoneId.of("Asia/Kolkata")) // Use IST time zone
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd::HH:mm:ss"));
    }

    public String updateMomentStatus(String momentId, MomentStatus status) throws ExecutionException, InterruptedException {
        return momentDao.updateMomentStatus(momentId, status);
    }

    public boolean likeMoment(LikeRequest likeRequest) throws ExecutionException, InterruptedException {
        String userId = likeRequest.getUserId();
        String momentId = likeRequest.getMomentId();
        
        // Check if moment exists and get its eventId
        Moment moment = momentDao.getMomentById(momentId);
        if (moment == null) {
            throw new RuntimeException("Moment not found with ID: " + momentId);
        }
        
        // Check if user has already liked this moment
        boolean alreadyLiked = likeDao.isLikedByUser(userId, momentId);
        
        if (alreadyLiked) {
            // Unlike: remove like and update moment
            likeDao.deleteLike(userId, momentId);
            likeDao.updateMomentLikedBy(momentId, userId, false);
            return false; // Return false to indicate unliked
        } else {
            // Like: create like and update moment
            Like like = new Like(userId, momentId, moment.getEventId(), moment.getCreationTime());
            likeDao.saveLike(like);
            likeDao.updateMomentLikedBy(momentId, userId, true);
            return true; // Return true to indicate liked
        }
    }

    public MomentsResponse getLikedMomentsFeed(String userId, String eventId, Cursor cursor) throws ExecutionException, InterruptedException {
        int limit = cursor == null ? 20 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        
        // Get likes by user for the specific event
        List<Like> likes = likeDao.getLikesByUser(userId, eventId, offset, limit);
        
        // Get the moments for these likes
        List<Moment> likedMoments = new ArrayList<>();

        try {
            List<String> likedMomentIds = likes.stream()
                    .map(Like::getLikedMoment)
                    .collect(Collectors.toList());
            likedMoments = momentDao.getMomentsByIds(likedMomentIds);
            for (Moment moment : likedMoments) {
                moment.setIsLiked(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        int totalCount = likeDao.getTotalLikesByUser(userId, eventId);
        boolean isLastPage = likedMoments.size() < limit;
        Long lastMomentCreationTime = likedMoments.isEmpty() ? null : likedMoments.get(likedMoments.size() - 1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset + likedMoments.size(), limit, lastMomentCreationTime, isLastPage);
        
        return new MomentsResponse(likedMoments, cursorOut);
    }

}



