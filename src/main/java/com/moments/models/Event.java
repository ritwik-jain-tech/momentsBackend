package com.moments.models;


import java.util.List;


public class Event {

    private String eventId;
    private String creatorId;
    private String eventThumbnail;
    private String eventName;
    private List<String> userIds;
    private List<String> groomSide;
    private List<String> brideSide;
    private int totalMoments;
    private Long startTime;
    private Long endTime;

    // Getters and Setters

    public List<String> getGroomSide() {
        return groomSide;
    }

    public void setGroomSide(List<String> groomSide) {
        this.groomSide = groomSide;
    }

    public List<String> getBrideSide() {
        return brideSide;
    }

    public void setBrideSide(List<String> brideSide) {
        this.brideSide = brideSide;
    }

    public int getTotalMoments() {
        return totalMoments;
    }

    public void setTotalMoments(int totalMoments) {
        this.totalMoments = totalMoments;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getEventThumbnail() {
        return eventThumbnail;
    }

    public void setEventThumbnail(String eventThumbnail) {
        this.eventThumbnail = eventThumbnail;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public int getMemberCount(){
        return this.userIds.size();
    }

    public void setMemberCount(int memberCount){

    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

}
