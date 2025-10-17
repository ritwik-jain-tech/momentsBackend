package com.moments.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.moments.dao.LikeDao;
import com.moments.dao.MomentDao;
import com.moments.models.Cursor;
import com.moments.models.FaceEmbeddingResponse;
import com.moments.models.Like;
import com.moments.models.LikeRequest;
import com.moments.models.Moment;
import com.moments.models.MomentFilter;
import com.moments.models.MomentStatus;
import com.moments.models.MomentsResponse;
import com.moments.models.ReportRequest;

@Service
public class MomentService {

    @Autowired
    private MomentDao momentDao;

    @Autowired
    private LikeDao likeDao;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    // Create or Update a Moment
    public String saveMoment(Moment moment) throws ExecutionException, InterruptedException {
        moment.setUploadTime(Instant.now().toEpochMilli());
        if (moment.getCreationTime() == null) {
            moment.setCreationTime(moment.getUploadTime());
        }
        moment.setStatus(MomentStatus.APPROVED);
        moment.setCreationTimeText(epocToString(moment.getCreationTime()));
        moment.setUploadTimeText(epocToString(moment.getUploadTime()));
        moment.setMomentId(generateMomentId(moment.getCreatorId()));
        return momentDao.saveMoment(moment);
    }

    public List<String> saveMoments(List<Moment> moments) throws ExecutionException, InterruptedException {
        // Use atomic batch operation by default
        return saveMomentsBatch(moments);
    }

    // Non-atomic bulk save method that allows partial failures
    public List<String> saveMomentsNonAtomic(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> ids = new ArrayList<>();

        for (Moment moment : moments) {
            String id = saveMoment(moment);
            ids.add(id);
        }

        return ids;
    }

    // Atomic bulk save method using Firestore batch operations for better
    // transaction handling
    public List<String> saveMomentsBatch(List<Moment> moments) throws ExecutionException, InterruptedException {
        if (moments == null || moments.isEmpty()) {
            return new ArrayList<>();
        }

        // Prepare all moments first
        for (Moment moment : moments) {
            moment.setUploadTime(Instant.now().toEpochMilli());
            if (moment.getCreationTime() == null) {
                moment.setCreationTime(moment.getUploadTime());
            }
            moment.setStatus(MomentStatus.APPROVED);
            moment.setCreationTimeText(epocToString(moment.getCreationTime()));
            moment.setUploadTimeText(epocToString(moment.getUploadTime()));
            moment.setMomentId(generateMomentId(moment.getCreatorId()));
        }

        // If batch size is 50 or more, split into smaller batches
        if (moments.size() >= 50) {
            return saveMomentsInBatches(moments);
        }

        // Use batch operation for atomicity
        return momentDao.saveMomentsBatch(moments);
    }

    // Helper method to split large batches into smaller ones
    private List<String> saveMomentsInBatches(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> allIds = new ArrayList<>();
        int batchSize = 49; // Keep under 50 limit

        for (int i = 0; i < moments.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, moments.size());
            List<Moment> batch = moments.subList(i, endIndex);

            List<String> batchIds = momentDao.saveMomentsBatch(batch);
            allIds.addAll(batchIds);
        }

        return allIds;
    }

    public Boolean reportMoment(ReportRequest reportRequest) throws ExecutionException, InterruptedException {
        return momentDao.reportMoment(reportRequest);
    }

    private String generateMomentId(String creatorId) {
        return creatorId + "_" + epocToString(Instant.now().toEpochMilli());
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

    public MomentsResponse findMoments(String eventId, MomentFilter filter, Cursor cursor, String userId)
            throws ExecutionException, InterruptedException {
        int limit = cursor == null ? 20 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        String creatorId = filter == null ? null : filter.getCreatedById();
        String source = filter == null ? null : filter.getSource();
        List<Moment> moments = Objects.equals(source, "web") ? momentDao.getAllMoments(eventId)
                : momentDao.getMomentsFeed(creatorId, eventId, offset, limit);
        for (Moment moment : moments) {
            moment.setIsLiked(moment.getLikedBy() != null && moment.getLikedBy().contains(userId));
        }
        int totalCount = momentDao.getTotalCount(creatorId, eventId);
        boolean isLastPage = moments.size() < limit;
        Long lastMomentCreationTime = moments.isEmpty() ? null : moments.get(moments.size() - 1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset + moments.size(), limit, lastMomentCreationTime, isLastPage);
        return new MomentsResponse(moments, cursorOut);
    }

    private String epocToString(Long epoc) {
        return Instant.ofEpochSecond(epoc / 1000)
                .atZone(ZoneId.of("Asia/Kolkata")) // Use IST time zone
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd::HH:mm:ss"));
    }

    public String updateMomentStatus(String momentId, MomentStatus status)
            throws ExecutionException, InterruptedException {
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

    public MomentsResponse getLikedMomentsFeed(String userId, String eventId, Cursor cursor)
            throws ExecutionException, InterruptedException {
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
        Long lastMomentCreationTime = likedMoments.isEmpty() ? null
                : likedMoments.get(likedMoments.size() - 1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset + likedMoments.size(), limit, lastMomentCreationTime,
                isLastPage);

        return new MomentsResponse(likedMoments, cursorOut);
    }

    // Process moment with face recognition
    public String saveMomentWithFaceRecognition(Moment moment, MultipartFile imageFile)
            throws ExecutionException, InterruptedException {
        // First save the moment
        String momentId = saveMoment(moment);

        // Process face recognition if image is provided
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                FaceEmbeddingResponse faceResponse = faceRecognitionService.processMomentImage(imageFile, momentId);
                if (faceResponse.isSuccess() && !faceResponse.getTaggedUserIds().isEmpty()) {
                    // Update moment with tagged users
                    moment.setTaggedUserIds(faceResponse.getTaggedUserIds());
                    momentDao.saveMoment(moment);

                    // Log the results
                    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MomentService.class);
                    logger.info("Face recognition completed for moment {}: {} faces detected, {} users tagged",
                            momentId, faceResponse.getFacesDetected(), faceResponse.getTaggedUserIds().size());
                }
            } catch (Exception e) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MomentService.class);
                logger.error("Face recognition failed for moment {}: {}", momentId, e.getMessage(), e);
                // Continue without face recognition - don't fail the moment creation
            }
        }

        return momentId;
    }
}
