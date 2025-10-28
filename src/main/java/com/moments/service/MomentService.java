package com.moments.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moments.dao.LikeDao;
import com.moments.dao.MomentDao;
import com.moments.models.Cursor;
import com.moments.models.Like;
import com.moments.models.LikeRequest;
import com.moments.models.Moment;
import com.moments.models.MomentFilter;
import com.moments.models.MomentStatus;
import com.moments.models.MomentsResponse;
import com.moments.models.ReportRequest;

@Service
public class MomentService {

    private static final Logger logger = LoggerFactory.getLogger(MomentService.class);

    @Autowired
    private MomentDao momentDao;

    @Autowired
    private LikeDao likeDao;

    @Autowired
    private FaceTaggingService faceTaggingService;

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

        String momentId = momentDao.saveMoment(moment);

        logger.info("Successfully saved moment {} to database, triggering face tagging", momentId);

        // Trigger face tagging service call (async with fail safety) - non-blocking
        triggerFaceTaggingForMoment(momentId, moment);

        return momentId;
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
            logger.warn("Empty or null moments list provided to saveMomentsBatch");
            return new ArrayList<>();
        }

        logger.info("Starting batch save for {} moments", moments.size());

        // Prepare all moments first
        List<Moment> validMoments = new ArrayList<>();
        for (int i = 0; i < moments.size(); i++) {
            Moment moment = moments.get(i);
            try {
                moment.setUploadTime(Instant.now().toEpochMilli());
                if (moment.getCreationTime() == null) {
                    moment.setCreationTime(moment.getUploadTime());
                }
                moment.setStatus(MomentStatus.APPROVED);
                moment.setCreationTimeText(epocToString(moment.getCreationTime()));
                moment.setUploadTimeText(epocToString(moment.getUploadTime()));
                moment.setMomentId(generateMomentId(moment.getCreatorId()));

                // Validate required fields
                if (moment.getCreatorId() == null || moment.getCreatorId().trim().isEmpty()) {
                    logger.error("Moment {} has null/empty creatorId, skipping", i);
                    continue;
                }
                if (moment.getMomentId() == null || moment.getMomentId().trim().isEmpty()) {
                    logger.error("Moment {} has null/empty momentId after generation, skipping", i);
                    continue;
                }

                validMoments.add(moment);
                logger.debug("Prepared moment {}: id={}, creatorId={}", i, moment.getMomentId(), moment.getCreatorId());
            } catch (Exception e) {
                logger.error("Error preparing moment {}: {}", i, e.getMessage(), e);
            }
        }

        logger.info("Prepared {} valid moments out of {} total moments", validMoments.size(), moments.size());

        if (validMoments.isEmpty()) {
            logger.warn("No valid moments to save after preparation");
            return new ArrayList<>();
        }

        // If batch size is 50 or more, split into smaller batches
        if (validMoments.size() >= 50) {
            return saveMomentsInBatches(validMoments);
        }

        // Use batch operation for atomicity
        List<String> results = momentDao.saveMomentsBatch(validMoments);

        logger.info("Successfully saved {} moments to database, triggering face tagging", results.size());

        // Trigger face tagging service calls (async with fail safety) for each moment -
        // non-blocking - with small delay to ensure DB commit
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to ensure database transaction is fully committed
                Thread.sleep(100);

                for (int i = 0; i < validMoments.size() && i < results.size(); i++) {
                    Moment moment = validMoments.get(i);
                    String momentId = results.get(i);
                    triggerFaceTaggingForMoment(momentId, moment);
                }

                logger.info("Triggered face tagging for {} moments", results.size());
            } catch (Exception e) {
                logger.error("Error triggering face tagging for batch: {}", e.getMessage(), e);
            }
        });

        return results;
    }

    // Helper method to split large batches into smaller ones
    private List<String> saveMomentsInBatches(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> allIds = new ArrayList<>();
        int batchSize = 49; // Keep under 50 limit

        logger.info("Processing {} moments in batches of {}", moments.size(), batchSize);

        for (int i = 0; i < moments.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, moments.size());
            List<Moment> batch = moments.subList(i, endIndex);

            logger.info("Processing batch {}: moments {} to {}", (i / batchSize) + 1, i, endIndex - 1);

            try {
                List<String> batchIds = momentDao.saveMomentsBatch(batch);
                allIds.addAll(batchIds);

                logger.info("Successfully saved batch of {} moments to database", batchIds.size());

                // Trigger face tagging for this batch after it's saved - with delay to ensure
                // DB commit
                CompletableFuture.runAsync(() -> {
                    try {
                        // Small delay to ensure database transaction is fully committed
                        Thread.sleep(100);

                        for (int j = 0; j < batch.size() && j < batchIds.size(); j++) {
                            Moment moment = batch.get(j);
                            String momentId = batchIds.get(j);
                            triggerFaceTaggingForMoment(momentId, moment);
                        }

                        logger.info("Triggered face tagging for batch of {} moments", batchIds.size());
                    } catch (Exception e) {
                        logger.error("Error triggering face tagging for batch: {}", e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error saving batch {}: {}", (i / batchSize) + 1, e.getMessage(), e);
                // Continue with next batch even if this one fails
            }
        }

        logger.info("Completed batch processing: {} total moments processed", allIds.size());
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
        String taggedUserId = filter == null ? null : filter.getTaggedUserId();
        String source = filter == null ? null : filter.getSource();

        List<Moment> moments;
        int totalCount;

        if (Objects.equals(source, "web")) {
            moments = momentDao.getAllMoments(eventId);
            totalCount = moments.size();
        } else if (taggedUserId != null && !taggedUserId.isEmpty()) {
            // Use tagged user filter (creatorId and taggedUserId are mutually exclusive)
            moments = momentDao.getMomentsFeedByTaggedUser(taggedUserId, eventId, offset, limit);
            totalCount = momentDao.getTotalCountByTaggedUser(taggedUserId, eventId);
        } else if (creatorId != null && !creatorId.isEmpty()) {
            // Use creator filter (default feed with creator filter)
            moments = momentDao.getMomentsFeed(creatorId, eventId, offset, limit);
            totalCount = momentDao.getTotalCount(creatorId, eventId);
        } else {
            // Default feed (no filters)
            moments = momentDao.getMomentsFeed(null, eventId, offset, limit);
            totalCount = momentDao.getTotalCount(null, eventId);
        }

        for (Moment moment : moments) {
            moment.setIsLiked(moment.getLikedBy() != null && moment.getLikedBy().contains(userId));
        }

        boolean isLastPage = moments.size() < limit;
        Long lastMomentCreationTime = moments.isEmpty() ? null : moments.get(moments.size() - 1).getCreationTime();
        Cursor cursorOut = new Cursor(totalCount, offset + moments.size(), limit, lastMomentCreationTime, isLastPage);
        MomentsResponse momentsResponse = new MomentsResponse(moments, cursorOut);

        if (taggedUserId != null && !taggedUserId.isEmpty()) {
            momentsResponse.setReUploadRequired(false);
        }
        return momentsResponse;
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

    /**
     * Trigger face tagging service call for a moment (non-blocking)
     * This method runs face tagging in background without affecting the main API
     * response
     */
    private void triggerFaceTaggingForMoment(String momentId, Moment moment) {
        // Validate required fields
        if (momentId == null || momentId.trim().isEmpty()) {
            logger.warn("Invalid momentId provided for face tagging, skipping");
            return;
        }

        if (moment == null) {
            logger.warn("Null moment provided for face tagging, momentId: {}, skipping", momentId);
            return;
        }

        if (moment.getEventId() != null && !moment.getEventId().trim().isEmpty() &&
                moment.getMedia() != null && moment.getMedia().getUrl() != null
                && !moment.getMedia().getUrl().trim().isEmpty()) {

            logger.info("Triggering face tagging for moment: {}, eventId: {}", momentId, moment.getEventId());

            // Fire and forget - runs in background thread
            CompletableFuture.runAsync(() -> {
                try {
                    faceTaggingService.processMomentAsync(momentId, moment.getMedia().getUrl(), moment.getEventId());
                    logger.info("Face tagging service call completed for moment: {}", momentId);
                } catch (Exception e) {
                    // Log error but don't fail the main moment creation process
                    logger.error("Face tagging service call failed for moment, momentId: {}, eventId: {}, error: {}",
                            momentId, moment.getEventId(), e.getMessage(), e);
                }
            });
        } else {
            logger.warn("Missing eventId or imageUrl for moment, skipping face tagging service call for momentId: {}",
                    momentId);
        }
    }
}
