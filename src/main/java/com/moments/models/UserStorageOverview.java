package com.moments.models;

import java.util.ArrayList;
import java.util.List;

/** Total storage across all events the user belongs to, plus per-event breakdown. */
public class UserStorageOverview {

    private long totalOriginalSizeBytes;
    private long totalOptimisedSizeBytes;
    private long totalThumbnailSizeBytes;
    private int totalMomentCount;
    private List<EventStorageSummary> events = new ArrayList<>();

    public UserStorageOverview() {
    }

    public UserStorageOverview(long totalOriginalSizeBytes, long totalOptimisedSizeBytes,
            long totalThumbnailSizeBytes, int totalMomentCount, List<EventStorageSummary> events) {
        this.totalOriginalSizeBytes = totalOriginalSizeBytes;
        this.totalOptimisedSizeBytes = totalOptimisedSizeBytes;
        this.totalThumbnailSizeBytes = totalThumbnailSizeBytes;
        this.totalMomentCount = totalMomentCount;
        this.events = events != null ? events : new ArrayList<>();
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

    public int getTotalMomentCount() {
        return totalMomentCount;
    }

    public void setTotalMomentCount(int totalMomentCount) {
        this.totalMomentCount = totalMomentCount;
    }

    public List<EventStorageSummary> getEvents() {
        return events;
    }

    public void setEvents(List<EventStorageSummary> events) {
        this.events = events != null ? events : new ArrayList<>();
    }
}
