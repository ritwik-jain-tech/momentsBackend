package com.moments.models;

public class ReportRequest {
    private String reportingUserId;
    private String eventId;
    private String momentId;
    private String reason;

    public ReportRequest(String reportingUserId, String eventId, String momentId, String reason) {
        this.reportingUserId = reportingUserId;
        this.eventId = eventId;
        this.momentId = momentId;
         this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReportingUserId() {
        return reportingUserId;
    }

    public void setReportingUserId(String reportingUserId) {
        this.reportingUserId = reportingUserId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }
}
