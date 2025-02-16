package com.moments.models;

public class BlockRequest {

    String blockingUserId;
    String reason;
    String blockedUserId;


    public BlockRequest(String blockingUserId, String reason, String blockedUserId) {
        this.blockingUserId = blockingUserId;
        this.reason = reason;
        this.blockedUserId = blockedUserId;

    }

    public String getBlockingUserId() {
        return blockingUserId;
    }

    public void setBlockingUserId(String blockingUserId) {
        this.blockingUserId = blockingUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(String blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

}
