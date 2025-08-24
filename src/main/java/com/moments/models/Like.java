package com.moments.models;

import java.time.Instant;

public class Like {
    private String likeId;
    private String likedBy; // userId who liked
    private String likedMoment; // momentId that was liked
    private String eventId;
    private Long createdAt;
    private Long momentCreationTime; // creation time of the liked moment

    public Like() {
    }

    public Like(String likedBy, String likedMoment, String eventId, Long momentCreationTime) {
        this.likedBy = likedBy;
        this.likedMoment = likedMoment;
        this.eventId = eventId;
        this.momentCreationTime = momentCreationTime;
        this.createdAt = Instant.now().toEpochMilli();
        this.likeId = generateLikeId(likedBy, likedMoment);
    }

    private String generateLikeId(String likedBy, String likedMoment) {
        return likedBy + "_" + likedMoment + "_" + Instant.now().toEpochMilli();
    }

    public String getLikeId() {
        return likeId;
    }

    public void setLikeId(String likeId) {
        this.likeId = likeId;
    }

    public String getLikedBy() {
        return likedBy;
    }

    public void setLikedBy(String likedBy) {
        this.likedBy = likedBy;
    }

    public String getLikedMoment() {
        return likedMoment;
    }

    public void setLikedMoment(String likedMoment) {
        this.likedMoment = likedMoment;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getMomentCreationTime() {
        return momentCreationTime;
    }

    public void setMomentCreationTime(Long momentCreationTime) {
        this.momentCreationTime = momentCreationTime;
    }
} 