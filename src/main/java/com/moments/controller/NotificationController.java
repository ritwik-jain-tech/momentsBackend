package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.FCMTokenRequest;
import com.moments.models.NotificationRequest;
import com.moments.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

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
}
