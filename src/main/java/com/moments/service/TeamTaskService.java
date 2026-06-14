package com.moments.service;

import com.moments.dao.TeamTaskDao;
import com.moments.models.TeamMember;
import com.moments.models.TeamTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class TeamTaskService {

    @Autowired
    private TeamTaskDao teamTaskDao;

    @Autowired
    private TeamMemberService teamMemberService;

    /** Create a new task or update an existing one (when {@code taskId} is set). */
    public TeamTask createOrUpdateTask(TeamTask task) throws ExecutionException, InterruptedException {
        long now = System.currentTimeMillis();
        boolean isNew = task.getTaskId() == null || task.getTaskId().isEmpty();

        if (task.getStatus() == null || task.getStatus().trim().isEmpty()) {
            task.setStatus("TODO");
        }
        if (task.getPriority() == null || task.getPriority().trim().isEmpty()) {
            task.setPriority("MEDIUM");
        }
        if (isNew && task.getCreatedAt() == null) {
            task.setCreatedAt(now);
        }
        task.setUpdatedAt(now);

        // Denormalize assignee name from the member record when not provided.
        if ((task.getAssigneeName() == null || task.getAssigneeName().trim().isEmpty())
                && task.getAssigneeId() != null && !task.getAssigneeId().trim().isEmpty()) {
            TeamMember member = teamMemberService.getMember(task.getAssigneeId());
            if (member != null) {
                task.setAssigneeName(member.getName());
            }
        }

        return teamTaskDao.saveTeamTask(task);
    }

    public TeamTask getTask(String taskId) throws ExecutionException, InterruptedException {
        return teamTaskDao.getTeamTaskById(taskId);
    }

    public List<TeamTask> listTasks(String agencyId, String assigneeId, String status, String eventId)
            throws ExecutionException, InterruptedException {
        return teamTaskDao.listByAgency(agencyId, assigneeId, status, eventId);
    }

    public void deleteTask(String taskId) throws ExecutionException, InterruptedException {
        teamTaskDao.deleteTeamTask(taskId);
    }

    /** Per-status counts plus per-member completion (done/total) for the agency dashboard. */
    public Map<String, Object> getStats(String agencyId) throws ExecutionException, InterruptedException {
        List<TeamTask> tasks = teamTaskDao.listByAgency(agencyId, null, null, null);

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("TODO", 0);
        statusCounts.put("IN_PROGRESS", 0);
        statusCounts.put("IN_REVIEW", 0);
        statusCounts.put("DONE", 0);

        Map<String, Map<String, Object>> byMember = new HashMap<>();

        for (TeamTask task : tasks) {
            String status = task.getStatus() == null ? "TODO" : task.getStatus();
            statusCounts.merge(status, 1, Integer::sum);

            String assigneeId = task.getAssigneeId();
            if (assigneeId != null && !assigneeId.isEmpty()) {
                Map<String, Object> mStats = byMember.computeIfAbsent(assigneeId, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("total", 0);
                    m.put("done", 0);
                    return m;
                });
                mStats.put("total", (Integer) mStats.get("total") + 1);
                if ("DONE".equals(status)) {
                    mStats.put("done", (Integer) mStats.get("done") + 1);
                }
            }
        }

        int total = tasks.size();
        int done = statusCounts.getOrDefault("DONE", 0);
        int completionPct = total == 0 ? 0 : Math.round((done * 100f) / total);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasks", total);
        stats.put("statusCounts", statusCounts);
        stats.put("byMember", byMember);
        stats.put("completionPct", completionPct);
        return stats;
    }
}
