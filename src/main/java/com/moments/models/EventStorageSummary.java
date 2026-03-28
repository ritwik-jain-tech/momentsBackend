package com.moments.models;

public class EventStorageSummary {

    private String eventId;
    private String eventName;
    private long totalOriginalSizeBytes;
    private long totalOptimisedSizeBytes;
    private long totalThumbnailSizeBytes;
    private int momentCount;

    public EventStorageSummary() {
    }

    public EventStorageSummary(String eventId, long totalOriginalSizeBytes, long totalOptimisedSizeBytes,
            long totalThumbnailSizeBytes, int momentCount) {
        this.eventId = eventId;
        this.totalOriginalSizeBytes = totalOriginalSizeBytes;
        this.totalOptimisedSizeBytes = totalOptimisedSizeBytes;
        this.totalThumbnailSizeBytes = totalThumbnailSizeBytes;
        this.momentCount = momentCount;
    }

    public EventStorageSummary(String eventId, String eventName, long totalOriginalSizeBytes,
            long totalOptimisedSizeBytes, long totalThumbnailSizeBytes, int momentCount) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.totalOriginalSizeBytes = totalOriginalSizeBytes;
        this.totalOptimisedSizeBytes = totalOptimisedSizeBytes;
        this.totalThumbnailSizeBytes = totalThumbnailSizeBytes;
        this.momentCount = momentCount;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public long getTotalOriginalSizeBytes() {
        return totalOriginalSizeBytes;
    }

    public void setTotalOriginalSizeBytes(long totalOriginalSizeBytes) {
        this.totalOriginalSizeBytes = totalOriginalSizeBytes;
    }

    public long getTotalOptimisedSizeBytes() {
        return totalOptimisedSizeBytes;
    }

    public void setTotalOptimisedSizeBytes(long totalOptimisedSizeBytes) {
        this.totalOptimisedSizeBytes = totalOptimisedSizeBytes;
    }

    public long getTotalThumbnailSizeBytes() {
        return totalThumbnailSizeBytes;
    }

    public void setTotalThumbnailSizeBytes(long totalThumbnailSizeBytes) {
        this.totalThumbnailSizeBytes = totalThumbnailSizeBytes;
    }

    public int getMomentCount() {
        return momentCount;
    }

    public void setMomentCount(int momentCount) {
        this.momentCount = momentCount;
    }
}
