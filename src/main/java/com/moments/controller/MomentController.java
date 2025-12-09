package com.moments.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moments.models.BaseResponse;
import com.moments.models.LikeRequest;
import com.moments.models.LikedMomentsRequest;
import com.moments.models.Moment;
import com.moments.models.MomentsRequest;
import com.moments.models.MomentsResponse;
import com.moments.models.ReportRequest;
import com.moments.models.UpdateMomentStatusRequest;
import com.moments.service.MomentService;
import com.moments.service.NotificationService;

@RestController
@RequestMapping("/api/moments")
public class MomentController {

    private static final Logger logger = LoggerFactory.getLogger(MomentController.class);

    @Autowired
    private MomentService momentService;

    @Autowired
    private NotificationService notificationService;

    // Create or Update a Moment
    @PostMapping
    public ResponseEntity<BaseResponse> createOrUpdateMoment(@RequestBody Moment moment) {
        try {
            String result = momentService.saveMoment(moment);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Created moment with Id : " + result, HttpStatus.OK, moment));

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, moment));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<BaseResponse> createOrUpdateMoments(@RequestBody List<Moment> moments) {
        try {
            if (moments == null || moments.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("No moments provided for bulk save", HttpStatus.BAD_REQUEST, null));
            }

            List<String> results = momentService.saveMoments(moments, true);

            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse(
                            "Successfully created " + results.size() + " moments atomically with IDs: " + results,
                            HttpStatus.OK, results));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create moments atomically - database error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Unexpected error during atomic bulk save: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get a Moment by ID
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse> getMomentById(@PathVariable String id) {
        try {
            Moment moment = momentService.getMomentById(id);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Moment with Id : " + id, HttpStatus.OK, moment));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get all Moments
    @GetMapping
    public ResponseEntity<?> getAllMoments() {
        try {
            List<Moment> moments = momentService.getAllMoments();
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, moments));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/feed")
    public ResponseEntity<BaseResponse> getMomentsFeed(@RequestBody MomentsRequest request,
            @RequestHeader(value = "fcm_token", required = false) String fcmToken) {
        try {
            if (request.getUserId() != null && fcmToken != null && request.getCursor() == null) {
                notificationService.saveOrUpdateFCMToken(request.getUserId(), fcmToken);
            }
            MomentsResponse response = new MomentsResponse(new ArrayList<>(), null);
            if (request.getFilter() != null && request.getFilter().getLikedById() != null) {
                // Liked Feed
                response = momentService.getLikedMomentsFeed(request.getFilter().getLikedById(), request.getEventId(),
                        request.getCursor());
            } else {
                // Default feed
                response = momentService.findMoments(request.getEventId(), request.getFilter(), request.getCursor(),
                        request.getUserId());
            }

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, response));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Delete a Moment by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse> deleteMoment(@PathVariable String id) {
        try {
            momentService.deleteMoment(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/report")
    public ResponseEntity<BaseResponse> reportMoment(@RequestBody ReportRequest reportRequest) {
        try {
            boolean status = momentService.reportMoment(reportRequest);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Report status: " + status, HttpStatus.OK, status));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PatchMapping("/status")
    public ResponseEntity<BaseResponse> updateMomentStatus(
            @RequestBody UpdateMomentStatusRequest request) {
        try {
            String result = momentService.updateMomentStatus(request.getMomentId(), request.getStatus());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Moment status updated successfully", HttpStatus.OK, result));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to update moment status: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        }
    }

    @PostMapping("/like")
    public ResponseEntity<BaseResponse> likeMoment(@RequestBody LikeRequest likeRequest) {
        try {
            boolean status = momentService.likeMoment(likeRequest);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Like status: " + status, HttpStatus.OK, status));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to like moment: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                            null));
        }
    }

    @PostMapping("/liked-feed")
    public ResponseEntity<BaseResponse> getLikedMomentsFeed(@RequestBody LikedMomentsRequest request) {
        try {
            MomentsResponse response = momentService.getLikedMomentsFeed(request.getUserId(), request.getEventId(),
                    request.getCursor());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, response));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get liked moments: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
    
    @PutMapping("/event/{eventId}/update-creator-role")
    public ResponseEntity<BaseResponse> updateAllMomentsCreatorRoleForEvent(@PathVariable String eventId,
            @RequestParam(value = "creatorRole", defaultValue = "Guest") String creatorRole) {
        try {
            if (eventId == null || eventId.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("EventId cannot be null or empty", HttpStatus.BAD_REQUEST, null));
            }
            
            int updatedCount = momentService.updateAllMomentsCreatorRoleForEvent(eventId, creatorRole);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Successfully updated " + updatedCount + " moments with creatorRole: " + creatorRole,
                            HttpStatus.OK, updatedCount));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error updating moments creatorRole for event {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to update moments creatorRole: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        }
    }

}
