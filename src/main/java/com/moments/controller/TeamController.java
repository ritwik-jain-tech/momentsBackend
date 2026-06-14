package com.moments.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moments.models.BaseResponse;
import com.moments.models.TeamMember;
import com.moments.models.TeamTask;
import com.moments.service.TeamMemberService;
import com.moments.service.TeamTaskService;

/**
 * Agency-wide team management: roster of members (filterable by role) and a
 * JIRA-style task board. All endpoints are scoped by {@code agencyId} (the
 * studio owner's userId).
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);

    @Autowired
    private TeamMemberService teamMemberService;

    @Autowired
    private TeamTaskService teamTaskService;

    private ResponseEntity<BaseResponse> serverError(String prefix, Exception e) {
        log.error("{}: {}", prefix, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(prefix + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
    }

    // ---------------------------------------------------------------- Members

    @PostMapping("/members")
    public ResponseEntity<BaseResponse> createOrUpdateMember(@RequestBody TeamMember member) {
        try {
            if (member == null || member.getAgencyId() == null || member.getAgencyId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("agencyId is required", HttpStatus.BAD_REQUEST, null));
            }
            TeamMember saved = teamMemberService.createOrUpdateMember(member);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Member saved", HttpStatus.OK, saved));
        } catch (Exception e) {
            return serverError("Failed to save member", e);
        }
    }

    @GetMapping("/members")
    public ResponseEntity<BaseResponse> listMembers(@RequestParam("agencyId") String agencyId,
            @RequestParam(value = "role", required = false) String role) {
        try {
            List<TeamMember> members = teamMemberService.listMembers(agencyId, role);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting members", HttpStatus.OK, members));
        } catch (ExecutionException | InterruptedException e) {
            return serverError("Failed to list members", e);
        }
    }

    @PutMapping("/members/{id}")
    public ResponseEntity<BaseResponse> updateMember(@PathVariable String id, @RequestBody TeamMember member) {
        try {
            member.setMemberId(id);
            TeamMember saved = teamMemberService.createOrUpdateMember(member);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Member updated", HttpStatus.OK, saved));
        } catch (Exception e) {
            return serverError("Failed to update member", e);
        }
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<BaseResponse> deleteMember(@PathVariable String id) {
        try {
            teamMemberService.deleteMember(id);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Member deleted", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return serverError("Failed to delete member", e);
        }
    }

    // ------------------------------------------------------------------ Tasks

    @PostMapping("/tasks")
    public ResponseEntity<BaseResponse> createOrUpdateTask(@RequestBody TeamTask task) {
        try {
            if (task == null || task.getAgencyId() == null || task.getAgencyId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("agencyId is required", HttpStatus.BAD_REQUEST, null));
            }
            if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("title is required", HttpStatus.BAD_REQUEST, null));
            }
            TeamTask saved = teamTaskService.createOrUpdateTask(task);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Task saved", HttpStatus.OK, saved));
        } catch (Exception e) {
            return serverError("Failed to save task", e);
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<BaseResponse> listTasks(@RequestParam("agencyId") String agencyId,
            @RequestParam(value = "assigneeId", required = false) String assigneeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "eventId", required = false) String eventId) {
        try {
            List<TeamTask> tasks = teamTaskService.listTasks(agencyId, assigneeId, status, eventId);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting tasks", HttpStatus.OK, tasks));
        } catch (ExecutionException | InterruptedException e) {
            return serverError("Failed to list tasks", e);
        }
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<BaseResponse> updateTask(@PathVariable String id, @RequestBody TeamTask task) {
        try {
            task.setTaskId(id);
            TeamTask saved = teamTaskService.createOrUpdateTask(task);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Task updated", HttpStatus.OK, saved));
        } catch (Exception e) {
            return serverError("Failed to update task", e);
        }
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<BaseResponse> deleteTask(@PathVariable String id) {
        try {
            teamTaskService.deleteTask(id);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Task deleted", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return serverError("Failed to delete task", e);
        }
    }

    // ------------------------------------------------------------------ Stats

    @GetMapping("/stats")
    public ResponseEntity<BaseResponse> getStats(@RequestParam("agencyId") String agencyId) {
        try {
            Map<String, Object> stats = teamTaskService.getStats(agencyId);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting team stats", HttpStatus.OK, stats));
        } catch (ExecutionException | InterruptedException e) {
            return serverError("Failed to get team stats", e);
        }
    }
}
