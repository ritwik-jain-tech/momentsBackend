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
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.moments.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.moments.dao.EventDao;
import com.moments.dao.LikeDao;
import com.moments.dao.MomentDao;

import org.springframework.security.access.AccessDeniedException;

@Service
public class MomentService {

    private static final Logger logger = LoggerFactory.getLogger(MomentService.class);

    @Autowired
    private MomentDao momentDao;

    @Autowired
    private EventDao eventDao;

    @Autowired
    private LikeDao likeDao;

    @Autowired
    private FaceTaggingService faceTaggingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserProfileService userProfileService;
    
    @Autowired
    private EventRoleService eventRoleService;

    @Autowired
    private GoogleCloudStorageService googleCloudStorageService;
    
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;
    
    // Scheduled executor for retries
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);
    
    // Constants for retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 500;
    private static final long MAX_RETRY_DELAY_MS = 10000;

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
        
        // Set creatorRole from event membership unless already specified (e.g. Drive import as Photographer)
        if ((moment.getCreatorRole() == null || moment.getCreatorRole().isBlank())
                && moment.getEventId() != null && moment.getCreatorId() != null) {
            String roleName = eventRoleService.getRoleName(moment.getEventId(), moment.getCreatorId());
            moment.setCreatorRole(roleName);
        }

        String momentId = momentDao.saveMoment(moment);

        logger.info("Successfully saved moment {} to database, triggering face tagging", momentId);

        adjustEventStorageForMoment(moment, 1);

        // Trigger face tagging service call (async with fail safety) - non-blocking
        faceTaggingService.processMomentsBatchAsync(Collections.singletonList(moment));

        return momentId;
    }

    public List<String> saveMoments(List<Moment> moments, boolean sendNotification) throws ExecutionException, InterruptedException {
        return saveMoments(moments, sendNotification, false);
    }

    /**
     * @param synchronousFaceTagging when true, call the face-tagging service on the current thread after save (needed
     *            on Cloud Run where post-response {@code @Async} work may not get CPU).
     */
    public List<String> saveMoments(List<Moment> moments, boolean sendNotification, boolean synchronousFaceTagging)
            throws ExecutionException, InterruptedException {
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
                if (moment.getMomentId() == null || moment.getMomentId().isBlank()) {
                    moment.setMomentId(generateMomentId(moment.getCreatorId()));
                }
                
                // Set creatorRole from event unless already set on the moment
                if ((moment.getCreatorRole() == null || moment.getCreatorRole().isBlank())
                        && moment.getEventId() != null && moment.getCreatorId() != null) {
                    String roleName = null;
                    if (i == 0) {
                        roleName = eventRoleService.getRoleName(moment.getEventId(), moment.getCreatorId());
                    }
                    if (roleName == null && !validMoments.isEmpty()) {
                        roleName = validMoments.get(0).getCreatorRole();
                    }
                    moment.setCreatorRole(roleName);
                }

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
            return saveMomentsInBatches(validMoments, synchronousFaceTagging);
        }

        // Use batch operation for atomicity
        List<String> results = momentDao.saveMomentsBatch(validMoments);

        logger.info("Successfully saved {} moments to database, triggering face tagging", results.size());

        triggerFaceTaggingAfterSave(new ArrayList<>(validMoments), synchronousFaceTagging);

        if(sendNotification) {
            CompletableFuture.runAsync(() -> {
                try {
                    String eventId = moments.get(0).getEventId();
                    java.util.Map<String, String> data = notificationService.convertMomentToDataMap(moments.get(0));
                    notificationService.sendNotificationToEvent(eventId, null, "New moments created", null, data);
                } catch (Exception e) {
                    logger.error("Error triggering Notiifcation: {}", e.getMessage(), e);
                }
            });
        }

        return results;
    }

    private void triggerFaceTaggingAfterSave(List<Moment> moments, boolean synchronous) {
        if (moments == null || moments.isEmpty()) {
            return;
        }
        if (synchronous) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            faceTaggingService.processMomentsBatchSync(moments);
        } else {
            triggerFaceTaggingWithRetry(moments, 0);
        }
    }

    // Helper method to split large batches into smaller ones
    private List<String> saveMomentsInBatches(List<Moment> moments, boolean synchronousFaceTagging)
            throws ExecutionException, InterruptedException {
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

                for (Moment m : batch) {
                    adjustEventStorageForMoment(m, 1);
                }

                // Trigger face tagging for this batch with robust retry mechanism
                // Create a copy for async processing to avoid any potential issues
                triggerFaceTaggingAfterSave(new ArrayList<>(batch), synchronousFaceTagging);
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

    public boolean momentExists(String momentId) throws ExecutionException, InterruptedException {
        return momentDao.momentExists(momentId);
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
        try {
            Moment existing = momentDao.getMomentById(id);
            googleCloudStorageService.deleteMediaObjects(existing.getMedia());
        } catch (RuntimeException e) {
            if (e.getMessage() == null || !e.getMessage().contains("not found")) {
                throw e;
            }
            // Document already absent — still delete Firestore row for idempotency
        }
        momentDao.deleteMoment(id);
    }

    /**
     * Returns storage for one event. User must be a member ({@code userIds} contains {@code userId}).
     * Prefers {@link Event#getAggregatedStorage()}; falls back to summing moments if unset.
     */
    public EventStorageSummary getEventStorageSummary(String eventId, String userId)
            throws ExecutionException, InterruptedException {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        assertUserMemberOfEvent(eventId, userId);
        Event event = eventDao.getEventById(eventId);
        return buildEventStorageSummary(event);
    }

    /**
     * All events the user is a member of ({@code event.userIds} contains {@code userId}), with totals
     * and per-event breakdown. Uses event membership, not only {@link UserProfile#getEventIds()} (those
     * lists can drift).
     */
    public UserStorageOverview getUserStorageOverview(String userId)
            throws ExecutionException, InterruptedException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        UserProfile profile = userProfileService.getUser(userId);
        if (profile == null) {
            throw new IllegalArgumentException("User not found");
        }
        List<String> eventIds = eventDao.findEventIdsWhereUserIsMember(userId);
        if (eventIds.isEmpty()) {
            return new UserStorageOverview(0L, 0L, 0L, 0, new ArrayList<>());
        }
        List<Event> eventList = eventDao.getEventsByDocumentIds(eventIds);
        long sumO = 0L;
        long sumOp = 0L;
        long sumTh = 0L;
        int sumMoments = 0;
        List<EventStorageSummary> rows = new ArrayList<>();
        for (Event ev : eventList) {
            EventStorageSummary row = buildEventStorageSummary(ev);
            rows.add(row);
            sumO += row.getTotalOriginalSizeBytes();
            sumOp += row.getTotalOptimisedSizeBytes();
            sumTh += row.getTotalThumbnailSizeBytes();
            sumMoments += row.getMomentCount();
        }
        return new UserStorageOverview(sumO, sumOp, sumTh, sumMoments, rows);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private void adjustEventStorageForMoment(Moment moment, int sign) {
        if (moment == null || moment.getEventId() == null || moment.getEventId().isBlank()) {
            return;
        }
        MomentMemoryUsage u = moment.getMemoryUsage();
        long o = u == null ? 0L : nz(u.getOriginalUploadSizeBytes());
        long op = u == null ? 0L : nz(u.getOptimisedSizeBytes());
        long th = u == null ? 0L : nz(u.getThumbnailSizeBytes());
        if (o == 0L && op == 0L && th == 0L) {
            return;
        }
        try {
            eventDao.adjustAggregatedStorage(moment.getEventId(), sign * o, sign * op, sign * th);
        } catch (Exception e) {
            logger.warn("adjustAggregatedStorage failed for event {}: {}", moment.getEventId(), e.getMessage());
        }
    }

    private void assertUserMemberOfEvent(String eventId, String userId)
            throws ExecutionException, InterruptedException {
        Event event = eventDao.getEventById(eventId);
        List<String> ids = event.getUserIds();
        if (ids == null || !ids.contains(userId)) {
            throw new AccessDeniedException("User is not a member of this event");
        }
    }

    private boolean hasMeaningfulAggregate(MomentMemoryUsage agg) {
        if (agg == null) {
            return false;
        }
        return agg.getOriginalUploadSizeBytes() != null
                || agg.getOptimisedSizeBytes() != null
                || agg.getThumbnailSizeBytes() != null;
    }

    private EventStorageSummary buildEventStorageSummary(Event event)
            throws ExecutionException, InterruptedException {
        String eventId = event.getEventId();
        String eventName = event.getEventName();
        MomentMemoryUsage agg = event.getAggregatedStorage();
        if (hasMeaningfulAggregate(agg)) {
            long to = nz(agg.getOriginalUploadSizeBytes());
            long top = nz(agg.getOptimisedSizeBytes());
            long tth = nz(agg.getThumbnailSizeBytes());
            int mc = Math.max(0, event.getTotalMoments());
            return new EventStorageSummary(eventId, eventName, to, top, tth, mc);
        }
        List<Moment> moments = momentDao.getAllMoments(eventId, null);
        long totalOriginal = 0L;
        long totalOptimised = 0L;
        long totalThumbnail = 0L;
        for (Moment m : moments) {
            MomentMemoryUsage u = m.getMemoryUsage();
            if (u == null) {
                continue;
            }
            totalOriginal += nz(u.getOriginalUploadSizeBytes());
            totalOptimised += nz(u.getOptimisedSizeBytes());
            totalThumbnail += nz(u.getThumbnailSizeBytes());
        }
        return new EventStorageSummary(eventId, eventName, totalOriginal, totalOptimised, totalThumbnail,
                moments.size());
    }

    public MomentsResponse findMoments(String eventId, MomentFilter filter, Cursor cursor, String userId)
            throws ExecutionException, InterruptedException {
        int limit = cursor == null ? 20 : cursor.getLimit();
        int offset = cursor == null ? 0 : cursor.getOffset();
        String creatorId = filter == null ? null : filter.getCreatedById();
        String taggedUserId = filter == null ? null : filter.getTaggedUserId();
        String source = filter == null ? null : filter.getSource();

        // Check user's role for this event and determine if we need to filter by creatorRole
        String creatorRoleFilter = null;
//        if (userId != null && eventId != null && !"123456".equals(eventId) && !Objects.equals(source, "web")) {
//            try {
//                String userRoleName = eventRoleService.getRoleName(eventId, userId);
//                // If roleName is not "admin" (case-insensitive), filter by creatorRole
//                if (userRoleName != null && !userRoleName.equalsIgnoreCase("admin")) {
//                    creatorRoleFilter = userRoleName;
//                }
//            } catch (Exception e) {
//                logger.warn("Error fetching role for userId: {} and eventId: {}. Error: {}", userId, eventId, e.getMessage());
//                // If we can't fetch the role, default to filtering by "Guest" for safety
//            }
//        }

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
            moments = momentDao.getAllMoments(eventId, creatorRoleFilter);
            totalCount = moments.size();
        } else if (taggedUserId != null && !taggedUserId.isEmpty()) {
            // Use tagged user filter (creatorId and taggedUserId are mutually exclusive)
            moments = momentDao.getMomentsFeedByTaggedUser(taggedUserId, eventId, offset, limit, creatorRoleFilter);
            totalCount = momentDao.getTotalCountByTaggedUser(taggedUserId, eventId, creatorRoleFilter);
        } else if (creatorId != null && !creatorId.isEmpty()) {
            // Use creator filter (default feed with creator filter)
            moments = momentDao.getMomentsFeed(creatorId, eventId, offset, limit, creatorRoleFilter);
            totalCount = momentDao.getTotalCount(creatorId, eventId, creatorRoleFilter);
        } else {
            // Default feed (no filters)
            moments = momentDao.getMomentsFeed(null, eventId, offset, limit, creatorRoleFilter);
            totalCount = momentDao.getTotalCount(null, eventId, creatorRoleFilter);
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
    
    public int updateAllMomentsCreatorRoleForEvent(String eventId, String creatorRole) throws ExecutionException, InterruptedException {
        if (creatorRole == null || creatorRole.trim().isEmpty()) {
            creatorRole = "Guest";
        }
        return momentDao.updateAllMomentsCreatorRoleForEvent(eventId, creatorRole);
    }
    
    /**
     * Robust method to trigger face tagging with exponential backoff retry logic
     * This ensures the face tagging service is always called, even if there are transient failures
     */
    private void triggerFaceTaggingWithRetry(List<Moment> moments, int attemptNumber) {
        if (moments == null || moments.isEmpty()) {
            logger.warn("Skipping face tagging trigger: moments list is null or empty");
            return;
        }
        
        // Calculate delay with exponential backoff, but capped at maximum
        long delayMs = Math.min(INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attemptNumber), MAX_RETRY_DELAY_MS);
        
        // Use the configured task executor for better resource management
        CompletableFuture.runAsync(() -> {
            try {
                // First attempt: wait a bit for DB commit
                if (attemptNumber == 0) {
                    Thread.sleep(200);
                } else {
                    // Subsequent retries: use exponential backoff
                    Thread.sleep(delayMs);
                }
                
                logger.info("Attempting to trigger face tagging (attempt {}/{}) for {} moments", 
                    attemptNumber + 1, MAX_RETRY_ATTEMPTS, moments.size());
                
                // Trigger the face tagging service
                faceTaggingService.processMomentsBatchAsync(moments);
                
                logger.info("Successfully triggered batch face tagging for {} moments", moments.size());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread interrupted while triggering face tagging (attempt {}): {}", 
                    attemptNumber + 1, e.getMessage(), e);
                
                // Retry on interrupt if we haven't exceeded max attempts
                if (attemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                    scheduleRetry(moments, attemptNumber + 1);
                } else {
                    logger.error("Max retry attempts reached for face tagging after interruption. Giving up.");
                }
                
            } catch (Exception e) {
                logger.error("Error triggering batch face tagging (attempt {}): {}", 
                    attemptNumber + 1, e.getMessage(), e);
                
                // Retry if we haven't exceeded max attempts
                if (attemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                    logger.info("Scheduling retry {} for face tagging in {} ms", 
                        attemptNumber + 2, delayMs);
                    scheduleRetry(moments, attemptNumber + 1);
                } else {
                    logger.error("Max retry attempts ({}) reached for face tagging. Failed to trigger for {} moments. " +
                        "Error: {}", MAX_RETRY_ATTEMPTS, moments.size(), e.getMessage(), e);
                }
            }
        }, taskExecutor).exceptionally(ex -> {
            // Handle any uncaught exceptions in the CompletableFuture
            logger.error("Unexpected exception in face tagging trigger CompletableFuture (attempt {}): {}", 
                attemptNumber + 1, ex.getMessage(), ex);
            
            // Still retry if possible
            if (attemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                scheduleRetry(moments, attemptNumber + 1);
            } else {
                logger.error("Max retry attempts reached. Cannot retry face tagging trigger.");
            }
            return null;
        });
    }
    
    /**
     * Schedule a retry attempt with exponential backoff
     */
    private void scheduleRetry(List<Moment> moments, int nextAttempt) {
        long delayMs = Math.min(INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, nextAttempt), MAX_RETRY_DELAY_MS);
        
        retryExecutor.schedule(() -> {
            logger.info("Executing retry attempt {} for face tagging", nextAttempt + 1);
            triggerFaceTaggingWithRetry(moments, nextAttempt);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

}
