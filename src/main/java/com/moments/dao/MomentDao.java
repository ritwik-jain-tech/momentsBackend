package com.moments.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.moments.models.AdminTabCounts;
import com.moments.models.ClientSelection;
import com.moments.models.Moment;
import com.moments.models.MomentStatus;
import com.moments.models.ReportRequest;

public interface MomentDao {
    String saveMoment(Moment moment) throws ExecutionException, InterruptedException;

    Moment getMomentById(String id) throws ExecutionException, InterruptedException;

    List<Moment> getAllMoments() throws ExecutionException, InterruptedException;

    List<Moment> getAllMoments(String eventId) throws ExecutionException, InterruptedException;
    
    List<Moment> getAllMoments(String eventId, String creatorRoleFilter) throws ExecutionException, InterruptedException;

    void deleteMoment(String id) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsFeed(String creatorUserId, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException;
    
    List<Moment> getMomentsFeed(String creatorUserId, String eventId, int offset, int limit, String creatorRoleFilter)
            throws ExecutionException, InterruptedException;

    List<Moment> getMomentsFeedByTaggedUser(String taggedUserId, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException;
    
    List<Moment> getMomentsFeedByTaggedUser(String taggedUserId, String eventId, int offset, int limit, String creatorRoleFilter)
            throws ExecutionException, InterruptedException;

    int getTotalCount(String creatorUserId, String eventId) throws ExecutionException, InterruptedException;
    
    int getTotalCount(String creatorUserId, String eventId, String creatorRoleFilter) throws ExecutionException, InterruptedException;

    int getTotalCountByTaggedUser(String taggedUserId, String eventId)
            throws ExecutionException, InterruptedException;
    
    int getTotalCountByTaggedUser(String taggedUserId, String eventId, String creatorRoleFilter)
            throws ExecutionException, InterruptedException;

    boolean reportMoment(ReportRequest request) throws ExecutionException, InterruptedException;

    String updateMomentStatus(String momentId, MomentStatus status) throws ExecutionException, InterruptedException;

    /** Sets the client (bride/groom) delivery-review selection on a single moment. */
    String updateClientSelection(String momentId, ClientSelection selection) throws ExecutionException, InterruptedException;

    /** Moments the client marked {@code SELECTED}, chronological ascending — the finalized album order. */
    List<Moment> getSelectedMomentsForAlbum(String eventId) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsByIds(List<String> momentIds) throws ExecutionException, InterruptedException;

    List<String> saveMomentsBatch(List<Moment> moments) throws ExecutionException, InterruptedException;

    List<Moment> getMomentsFeedByCreatorIds(List<String> creatorIds, String eventId, int offset, int limit)
            throws ExecutionException, InterruptedException;

    int getTotalCountByCreatorIds(List<String> creatorIds, String eventId) throws ExecutionException, InterruptedException;

    void updateMomentFeedUrl(String momentId, String feedUrl) throws ExecutionException, InterruptedException;

    /**
     * Partial update after face-tagging: optimised/thumbnail URLs and byte sizes.
     * Only non-null arguments are written.
     */
    void updateMomentFaceTaggingStorage(String momentId, String feedUrl, String thumbnailUrl,
            Long optimisedSizeBytes, Long thumbnailSizeBytes) throws ExecutionException, InterruptedException;
    
    int updateAllMomentsCreatorRoleForEvent(String eventId, String creatorRole) throws ExecutionException, InterruptedException;

    /** True if a moment document exists with this id (for idempotent Drive import). */
    boolean momentExists(String momentId) throws ExecutionException, InterruptedException;

    /**
     * Admin (web) feed: paginated moments with optional moderation / media-type / creatorRole filters,
     * ordered by {@code orderField} (+ document id tie-break). Pass {@code anchorMomentId} to continue after that document.
     */
    List<Moment> getAdminMomentsFeedPage(String eventId, MomentStatus moderationStatusOrNullForAllBuckets,
            String firestoreMediaTypeOrNull,
            String firestoreCreatorRoleOrNull,
            String orderField,
            boolean ascending,
            int limit,
            String anchorMomentIdOrNull) throws ExecutionException, InterruptedException;

    long countAdminMomentsMatching(String eventId,
            MomentStatus moderationStatusOrNullForAllBuckets,
            String firestoreMediaTypeOrNull,
            String firestoreCreatorRoleOrNull) throws ExecutionException, InterruptedException;

    AdminTabCounts computeAdminTabCountsNonVideo(String eventId,
            String firestoreCreatorRoleOrNull) throws ExecutionException, InterruptedException;
}
