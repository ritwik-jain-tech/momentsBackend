package com.moments.dao;

import com.moments.models.TeamTask;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface TeamTaskDao {
    TeamTask saveTeamTask(TeamTask task) throws ExecutionException, InterruptedException;

    TeamTask getTeamTaskById(String taskId) throws ExecutionException, InterruptedException;

    /** Tasks for an agency, optionally filtered by assignee, status, and/or event (null/blank = ignore). */
    List<TeamTask> listByAgency(String agencyId, String assigneeId, String status, String eventId)
            throws ExecutionException, InterruptedException;

    void deleteTeamTask(String taskId) throws ExecutionException, InterruptedException;
}
