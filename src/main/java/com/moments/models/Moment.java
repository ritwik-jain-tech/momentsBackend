package com.moments.models;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.PropertyName;

public class Moment {
    private String momentId; // Firestore document ID

    private String eventId;

    private Long creationTime;

    private CreatorDetails creatorDetails;

    private String creatorId;

    private String creatorRole;

    private Media media;

    private Long uploadTime;

    private String creationTimeText;

    private String uploadTimeText;

    private MomentStatus status;

    private long aspectRatio;

    private List<String> reportedBy = new ArrayList<>();

    private List<String> likedBy = new ArrayList<>();

    private boolean isLiked;

    private List<String> taggedUserIds = new ArrayList<>();

    private MomentMemoryUsage memoryUsage;

    /**
     * Firestore {@code updated_at} (server timestamp). Must not use Java name {@code updatedAt} or the mapper
     * also binds document key {@code updatedAt}, which may be a numeric (Double) from legacy clients.
     */
    @PropertyName("updated_at")
    private Timestamp serverUpdatedAt;

    /** Legacy camelCase field; often a numeric epoch from older writes — keep as Double to avoid cast errors. */
    @PropertyName("updatedAt")
    private Double legacyUpdatedAtNumber;

    // Getters and Setters
    public long getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(long aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public List<String> getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(List<String> reportedBy) {
        this.reportedBy = reportedBy;
    }

    public List<String> getLikedBy() {
        return likedBy;
    }

    public void setLikedBy(List<String> likedBy) {
        this.likedBy = likedBy;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorRole() {
        return creatorRole;
    }

    public void setCreatorRole(String creatorRole) {
        this.creatorRole = creatorRole;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Long creationTime) {
        this.creationTime = creationTime;
    }

    public CreatorDetails getCreatorDetails() {
        return creatorDetails;
    }

    public void setCreatorDetails(CreatorDetails creatorDetails) {
        this.creatorDetails = creatorDetails;
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
    }

    public Long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Long uploadTime) {
        this.uploadTime = uploadTime;
    }

    public String getCreationTimeText() {
        return creationTimeText;
    }

    public void setCreationTimeText(String creationTimeText) {
        this.creationTimeText = creationTimeText;
    }

    public String getUploadTimeText() {
        return uploadTimeText;
    }

    public void setUploadTimeText(String uploadTimeText) {
        this.uploadTimeText = uploadTimeText;
    }

    public MomentStatus getStatus() {
        return status;
    }

    public void setStatus(MomentStatus status) {
        this.status = status;
    }

    public void setIsLiked(boolean b) {
        this.isLiked = b;
    }

    public boolean isLiked() {
        return isLiked;
    }

    // Alias setter for JSON deserialization when field is named "liked"
    public void setLiked(boolean liked) {
        this.isLiked = liked;
    }

    public List<String> getTaggedUserIds() {
        return taggedUserIds;
    }

    public void setTaggedUserIds(List<String> taggedUserIds) {
        this.taggedUserIds = taggedUserIds;
    }

    public MomentMemoryUsage getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(MomentMemoryUsage memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Timestamp getServerUpdatedAt() {
        return serverUpdatedAt;
    }

    public void setServerUpdatedAt(Timestamp serverUpdatedAt) {
        this.serverUpdatedAt = serverUpdatedAt;
    }

    public Double getLegacyUpdatedAtNumber() {
        return legacyUpdatedAtNumber;
    }

    public void setLegacyUpdatedAtNumber(Double legacyUpdatedAtNumber) {
        this.legacyUpdatedAtNumber = legacyUpdatedAtNumber;
    }
}
