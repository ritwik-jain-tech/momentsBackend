package com.moments.models;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.firestore.annotation.Exclude;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
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

    /** ISO calendar date (e.g. from admin "event date" field). */
    private String eventDate;
    private String projectType;
    private String location;
    private Integer expectedGuests;
    private List<String> teamMemberIds;
    private GuestAppConfig guestApp;

    /**
     * Optional millis from client; applied to {@link #startTime} before persist and not stored in Firestore.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Exclude
    private Long startTimeEpoch;

    /**
     * Optional millis from client; applied to {@link #endTime} before persist and not stored in Firestore.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Exclude
    private Long endTimeEpoch;

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

    public String getEventDate() {
        return eventDate;
    }

    public void setEventDate(String eventDate) {
        this.eventDate = eventDate;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getExpectedGuests() {
        return expectedGuests;
    }

    public void setExpectedGuests(Integer expectedGuests) {
        this.expectedGuests = expectedGuests;
    }

    public List<String> getTeamMemberIds() {
        return teamMemberIds;
    }

    public void setTeamMemberIds(List<String> teamMemberIds) {
        this.teamMemberIds = teamMemberIds;
    }

    public GuestAppConfig getGuestApp() {
        return guestApp;
    }

    public void setGuestApp(GuestAppConfig guestApp) {
        this.guestApp = guestApp;
    }

    public Long getStartTimeEpoch() {
        return startTimeEpoch;
    }

    public void setStartTimeEpoch(Long startTimeEpoch) {
        this.startTimeEpoch = startTimeEpoch;
    }

    public Long getEndTimeEpoch() {
        return endTimeEpoch;
    }

    public void setEndTimeEpoch(Long endTimeEpoch) {
        this.endTimeEpoch = endTimeEpoch;
    }

}
