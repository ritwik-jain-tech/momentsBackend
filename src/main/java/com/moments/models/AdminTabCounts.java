package com.moments.models;

/** Status bucket totals for the admin (web) moderation tabs; non-video moments only. */
public class AdminTabCounts {
    private long allNonVideo;
    private long pendingNonVideo;
    private long approvedNonVideo;

    public AdminTabCounts() {
    }

    public AdminTabCounts(long allNonVideo, long pendingNonVideo, long approvedNonVideo) {
        this.allNonVideo = allNonVideo;
        this.pendingNonVideo = pendingNonVideo;
        this.approvedNonVideo = approvedNonVideo;
    }

    public long getAllNonVideo() {
        return allNonVideo;
    }

    public void setAllNonVideo(long allNonVideo) {
        this.allNonVideo = allNonVideo;
    }

    public long getPendingNonVideo() {
        return pendingNonVideo;
    }

    public void setPendingNonVideo(long pendingNonVideo) {
        this.pendingNonVideo = pendingNonVideo;
    }

    public long getApprovedNonVideo() {
        return approvedNonVideo;
    }

    public void setApprovedNonVideo(long approvedNonVideo) {
        this.approvedNonVideo = approvedNonVideo;
    }
}
