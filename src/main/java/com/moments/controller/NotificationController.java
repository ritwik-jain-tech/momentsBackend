package com.moments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.models.BaseResponse;
import com.moments.models.FCMTokenRequest;
import com.moments.models.Moment;
import com.moments.models.MomentNotificationRequest;
import com.moments.models.NotificationRequest;
import com.moments.service.MomentService;
import com.moments.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MomentService momentService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Save or update FCM token for a user
     * @param fcmTokenRequest The FCM token request containing userId and fcmToken
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<BaseResponse> saveOrUpdateFCMToken(@RequestBody FCMTokenRequest fcmTokenRequest) {
        try {
            notificationService.saveOrUpdateFCMToken(fcmTokenRequest.getUserId(), fcmTokenRequest.getFcmToken());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("FCM token saved successfully", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to save FCM token: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        }
    }

    /**
     * Broadcast notification to every user participating in an event
     * @param notificationRequest contains eventId, body, and optional imageUrl
     * @return ResponseEntity with details about delivery
     */
    @PostMapping("/send-event")
    public ResponseEntity<BaseResponse> sendNotificationToEvent(@RequestBody NotificationRequest notificationRequest) {
        if (notificationRequest.getEventId() == null || notificationRequest.getEventId().trim().isEmpty()
                || notificationRequest.getBody() == null || notificationRequest.getBody().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("eventId and body are required", HttpStatus.BAD_REQUEST, null));
        }

        try {
            int successCount = notificationService.sendNotificationToEvent(
                    notificationRequest.getEventId(),
                    notificationRequest.getTitle(),
                    notificationRequest.getBody(),
                    notificationRequest.getImageUrl(),null);

            String message = String.format("Event notification sent to %d participants", successCount);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse(message, HttpStatus.OK, successCount));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to send event notification: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Unexpected error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Send notification to a single user
     * @param notificationRequest The notification request containing userId, title, and body
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/send")
    public ResponseEntity<BaseResponse> sendNotification(@RequestBody NotificationRequest notificationRequest) {
        try {
            boolean success = notificationService.sendNotification(
                    notificationRequest.getUserId(),
                    notificationRequest.getTitle(),
                    notificationRequest.getBody()
            );
            
            if (success) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Notification sent successfully", HttpStatus.OK, null));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Failed to send notification", HttpStatus.BAD_REQUEST, null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error sending notification: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Send notification to multiple users
     * @param notificationRequest The notification request containing userIds array, title, and body
     * @return ResponseEntity with success count and message
     */
    @PostMapping("/send-batch")
    public ResponseEntity<BaseResponse> sendNotificationToMultipleUsers(@RequestBody NotificationRequest notificationRequest) {
        try {
            int successCount = notificationService.sendNotificationToMultipleUsers(
                    notificationRequest.getUserIds(),
                    notificationRequest.getTitle(),
                    notificationRequest.getBody()
            );
            
            String message = String.format("Notifications sent successfully to %d out of %d users", 
                    successCount, notificationRequest.getUserIds().length);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse(message, HttpStatus.OK, successCount));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error sending batch notifications: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Test endpoint to send a simple notification
     * @param userId The user ID to send test notification to
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/test/{userId}")
    public ResponseEntity<BaseResponse> sendTestNotification(@PathVariable String userId) {
        try {
            boolean success = notificationService.sendNotification(
                    userId,
                    "Test Notification",
                    "This is a test notification from Moments app"
            );
            
            if (success) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Test notification sent successfully", HttpStatus.OK, null));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Failed to send test notification", HttpStatus.BAD_REQUEST, null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error sending test notification: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Send notification with moment data to a user
     * @param request The request containing momentId and userId
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/send-moment")
    public ResponseEntity<BaseResponse> sendMomentNotification(@RequestBody MomentNotificationRequest request) {
        if (request.getMomentId() == null || request.getMomentId().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("momentId is required", HttpStatus.BAD_REQUEST, null));
        }
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("userId is required", HttpStatus.BAD_REQUEST, null));
        }

        try {
            // Get the moment by ID
            Moment moment = momentService.getMomentById(request.getMomentId());
            if (moment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse("Moment not found with ID: " + request.getMomentId(), HttpStatus.NOT_FOUND, null));
            }

            // Convert moment to Map<String, String> for FCM data
            Map<String, String> momentData = convertMomentToDataMap(moment);

            // Get creator name for notification title
            String creatorName = moment.getCreatorDetails() != null && moment.getCreatorDetails().getUserName() != null
                    ? moment.getCreatorDetails().getUserName()
                    : "Someone";

            // Send notification with moment data
            boolean success = notificationService.sendNotification(
                    request.getUserId(),
                    "New moment from " + creatorName,
                    "Tap to view the moment",
                    null, // imageUrl - can be set from moment.getMedia() if needed
                    momentData
            );

            if (success) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Moment notification sent successfully", HttpStatus.OK, null));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Failed to send moment notification", HttpStatus.BAD_REQUEST, null));
            }
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error fetching moment: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error sending moment notification: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Convert Moment object to Map<String, String> for FCM data field
     * @param moment The moment to convert
     * @return Map containing moment data as strings
     */
    private Map<String, String> convertMomentToDataMap(Moment moment) {
        Map<String, String> dataMap = new HashMap<>();
        try {
            // Convert moment to JSON string and add to data map
            String momentJson = objectMapper.writeValueAsString(moment);
            dataMap.put("moment", momentJson);
            
            // Also add individual fields for easier access on client side
            if (moment.getMomentId() != null) {
                dataMap.put("momentId", moment.getMomentId());
            }
            if (moment.getEventId() != null) {
                dataMap.put("eventId", moment.getEventId());
            }
            if (moment.getCreatorId() != null) {
                dataMap.put("creatorId", moment.getCreatorId());
            }
            if (moment.getMedia() != null && moment.getMedia().getUrl() != null) {
                dataMap.put("mediaUrl", moment.getMedia().getUrl());
            }
        } catch (Exception e) {
            // Fallback: if JSON conversion fails, add basic fields
            System.err.println("Error converting moment to JSON: " + e.getMessage());
            if (moment.getMomentId() != null) {
                dataMap.put("momentId", moment.getMomentId());
            }
            if (moment.getEventId() != null) {
                dataMap.put("eventId", moment.getEventId());
            }
        }
        return dataMap;
    }
    
}
