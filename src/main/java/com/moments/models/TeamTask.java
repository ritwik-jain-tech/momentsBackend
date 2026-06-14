package com.moments.models;

/**
 * A work item on the agency task board ("JIRA for photographer agencies").
 * Agency-scoped via {@code agencyId}; assigned to a {@link TeamMember} via
 * {@code assigneeId}; optionally linked to a project/event via {@code eventId}.
 */
public class TeamTask {
    private String taskId;       // Firestore document ID
    private String agencyId;     // owner userId that this task belongs to
    private String title;
    private String description;
    private String assigneeId;   // memberId of assigned TeamMember
    private String assigneeName; // denormalized for cheap rendering
    private String status;       // "TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"
    private String priority;     // "LOW", "MEDIUM", "HIGH"
    private String eventId;      // optional linked project/event
    private String eventName;    // optional denormalized project/event name
    private Long dueDate;        // epoch millis, nullable
    private Long createdAt;      // epoch millis
    private Long updatedAt;      // epoch millis
    private String createdBy;    // userId of the creator

    public TeamTask() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
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

    public Long getDueDate() {
        return dueDate;
    }

    public void setDueDate(Long dueDate) {
        this.dueDate = dueDate;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
