package com.moments.models;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Moment {
    private String momentId;  // Firestore document ID

    private String eventId;

    private Long creationTime;

    private CreatorDetails creatorDetails;

    private String creatorId;

    private Media media;

    private Long uploadTime;

    private String creationTimeText;

    private String uploadTimeText;

    private MomentStatus status;

    private long aspectRatio;

    private List<String> reportedBy = new ArrayList<>();
    
    private List<String> likedBy = new ArrayList<>();

    private boolean isLiked;

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
}

class CreatorDetails {

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    private String userId;
    private String userName;
}



class Media {
    private String url;
    private MediaType type;
    private Integer width ;
    private Integer height ;

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public MediaType getType() {
        return type;
    }

    public void setType(MediaType type) {
        this.type = type;
    }

}

enum MediaType {
    IMAGE,
    VIDEO
}
