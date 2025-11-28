package com.moments.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.moments.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moments.dao.LikeDao;
import com.moments.dao.MomentDao;

@Service
public class MomentService {

    private static final Logger logger = LoggerFactory.getLogger(MomentService.class);

    @Autowired
    private MomentDao momentDao;

    @Autowired
    private LikeDao likeDao;

    @Autowired
    private FaceTaggingService faceTaggingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private GoogleCloudStorageService googleCloudStorageService;

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

        logger.info("Successfully saved moment {} to database, triggering face tagging and feed compression", momentId);

        // Trigger face tagging service call (async with fail safety) - non-blocking
        faceTaggingService.processMomentsBatchAsync(Collections.singletonList(moment));

        // Trigger feed compression (async) - non-blocking
        compressAndSetFeedUrlAsync(moment);

        return momentId;
    }

    public List<String> saveMoments(List<Moment> moments) throws ExecutionException, InterruptedException {
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

        // Trigger face tagging service call (async with fail safety) for all moments in
        // batch -
        // non-blocking - with small delay to ensure DB commit
        // Create a copy for async processing to avoid any potential issues
        final List<Moment> momentsForAsync = new ArrayList<>(validMoments);
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to ensure database transaction is fully committed
                Thread.sleep(200);

                // Single batch call instead of individual calls
                faceTaggingService.processMomentsBatchAsync(momentsForAsync);
                logger.info("Triggered batch face tagging for {} moments", momentsForAsync.size());
            } catch (Exception e) {
                logger.error("Error triggering batch face tagging: {}", e.getMessage(), e);
            }
        });

        CompletableFuture.runAsync(()->{
            try{
                String eventId= moments.get(0).getEventId();
                java.util.Map<String, String> data = notificationService.convertMomentToDataMap(moments.get(0));
                notificationService.sendNotificationToEvent(eventId,null,"New moments created", null, data);
            } catch(Exception e){
                logger.error("Error triggering Notiifcation: {}", e.getMessage(), e);
            }
        });

        // Trigger feed compression for all moments (async) - non-blocking
        for (Moment moment : validMoments) {
            compressAndSetFeedUrlAsync(moment);
        }

        return results;
    }

    // Helper method to split large batches into smaller ones
    private List<String> saveMomentsInBatches(List<Moment> moments) throws ExecutionException, InterruptedException {
        List<String> allIds = new ArrayList<>();
        int batchSize = 49; // Keep under 50 limit

        logger.info("Processing {} moments in batches of {}", moments.size(), batchSize);

        for (int i = 0; i < moments.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, moments.size());
            // Create a defensive copy instead of using subList() to avoid concurrent modification issues
            List<Moment> batch = new ArrayList<>(moments.subList(i, endIndex));

            logger.info("Processing batch {}: moments {} to {}", (i / batchSize) + 1, i, endIndex - 1);

            try {
                List<String> batchIds = momentDao.saveMomentsBatch(batch);
                
                if (batchIds == null || batchIds.size() != batch.size()) {
                    logger.error("Batch save returned {} IDs but expected {} for batch {}", 
                        batchIds != null ? batchIds.size() : 0, batch.size(), (i / batchSize) + 1);
                }
                
                allIds.addAll(batchIds);

                logger.info("Successfully saved batch of {} moments to database", batchIds.size());

                // Trigger face tagging for this batch after it's saved - with delay to ensure
                // DB commit
                // Create a copy for async processing to avoid any potential issues
                final List<Moment> batchForAsync = new ArrayList<>(batch);
                CompletableFuture.runAsync(() -> {
                    try {
                        // Small delay to ensure database transaction is fully committed
                        Thread.sleep(100);
                        // Single batch call instead of individual calls
                        faceTaggingService.processMomentsBatchAsync(batchForAsync);
                        logger.info("Triggered batch face tagging for {} moments", batchForAsync.size());
                    } catch (Exception e) {
                        logger.error("Error triggering batch face tagging: {}", e.getMessage(), e);
                    }
                });

                // Trigger feed compression for this batch (async) - non-blocking
                for (Moment moment : batch) {
                    compressAndSetFeedUrlAsync(moment);
                }
            } catch (ExecutionException | InterruptedException e) {
                logger.error("Error saving batch {}: {}", (i / batchSize) + 1, e.getMessage(), e);
                // Re-throw ExecutionException and InterruptedException as they are declared exceptions
                throw e;
            } catch (Exception e) {
                logger.error("Error saving batch {}: {}", (i / batchSize) + 1, e.getMessage(), e);
                // Continue with next batch only for non-critical exceptions
            }
        }

        logger.info("Completed batch processing: {} total moments processed", allIds.size());
        return allIds;
    }

    public Boolean reportMoment(ReportRequest reportRequest) throws ExecutionException, InterruptedException {
        return momentDao.reportMoment(reportRequest);
    }

    private String generateMomentId(String creatorId) {
        // Include full epoch millisecond to ensure uniqueness even for moments created in the same second
        // Add a small random component to further reduce collision risk in high-throughput scenarios
        long epochMs = Instant.now().toEpochMilli();
        int randomComponent = (int) (Math.random() * 1000); // 0-999
        return creatorId + "_" + epochMs + "_" + randomComponent;
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

        // Special handling for promotion event (eventId: "123456")
        if ("123456".equals(eventId) && userId != null && taggedUserId==null) {
            // Promotion event: users should only see moments created by themselves or specific userIds
            List<String> allowedCreatorIds = new ArrayList<>();
            allowedCreatorIds.add(userId); // Add the requesting user
            allowedCreatorIds.add("11");
            allowedCreatorIds.add("10");
            allowedCreatorIds.add("23");
            allowedCreatorIds.add("37");
            allowedCreatorIds.add("46");
            
            moments = momentDao.getMomentsFeedByCreatorIds(allowedCreatorIds, eventId, offset, limit);
            totalCount = momentDao.getTotalCountByCreatorIds(allowedCreatorIds, eventId);
        } else if (Objects.equals(source, "web")) {
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
            momentsResponse.setReUploadRequired(totalCount<1);
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
            CompletableFuture.runAsync(()->{
                try{
                    UserProfile userProfile = userProfileService.getUser(userId);
                    java.util.Map<String, String> data = notificationService.convertMomentToDataMap(moment);
                    notificationService.sendNotification(moment.getCreatorId(), null, "♥️" + userProfile.getName() + " added your moment to favourites!", null, data);
                } catch(Exception e){
                    logger.error("Error triggering Notiifcation: {}", e.getMessage(), e);
                }
            });
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
     * Asynchronously compresses image and sets feedUrl for a moment
     * This is done async to keep moment creation response time low
     */
    private void compressAndSetFeedUrlAsync(Moment moment) {
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to ensure database transaction is committed
                Thread.sleep(200);
                
                if (moment == null || moment.getMedia() == null || 
                    moment.getMedia().getUrl() == null || moment.getMedia().getUrl().trim().isEmpty()) {
                    logger.warn("Cannot compress feed image for moment {} - no media URL", moment != null ? moment.getMomentId() : "null");
                    return;
                }
                
                // Only process IMAGE type media
                if (moment.getMedia().getType() != MediaType.IMAGE) {
                    logger.debug("Skipping feed compression for moment {} - not an image", moment.getMomentId());
                    return;
                }
                
                String originalUrl = moment.getMedia().getUrl();
                logger.info("Starting feed compression for moment {}: {}", moment.getMomentId(), originalUrl);
                
                // Compress and upload to /compressed/ folder
                String feedUrl = googleCloudStorageService.compressAndUploadForFeed(originalUrl);
                
                // Update moment's media with feedUrl
                if (moment.getMedia() != null) {
                    moment.getMedia().setFeedUrl(feedUrl);
                }
                momentDao.updateMomentFeedUrl(moment.getMomentId(), feedUrl);
                
                logger.info("Successfully compressed and set feedUrl for moment {}: {}", moment.getMomentId(), feedUrl);
                
            } catch (Exception e) {
                logger.error("Error compressing feed image for moment {}: {}", 
                    moment != null ? moment.getMomentId() : "null", e.getMessage(), e);
                // Don't throw - this is async and shouldn't affect moment creation
            }
        });
    }

}
