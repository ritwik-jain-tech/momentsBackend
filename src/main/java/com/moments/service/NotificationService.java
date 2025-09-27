package com.moments.service;

import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.FirebaseMessagingException;

import java.util.concurrent.ExecutionException;

@Service
public class NotificationService {

    @Autowired
    private UserProfileDao userProfileDao;

    @Autowired
    private FirebaseMessaging firebaseMessaging;

    /**
     * Save or update FCM token for a user
     * @param userId The user ID
     * @param fcmToken The FCM token to save/update
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void saveOrUpdateFCMToken(String userId, String fcmToken) throws ExecutionException, InterruptedException {
        UserProfile userProfile = userProfileDao.getUserProfile(userId);
        if (userProfile != null) {
            userProfile.setFcmToken(fcmToken);
            userProfileDao.updateUserProfile(userProfile);
            System.out.println("FCM token updated for user: " + userId);
        } else {
            throw new RuntimeException("User not found with ID: " + userId);
        }
    }

    /**
     * Send notification to a user
     * @param userId The user ID to send notification to
     * @param title The notification title
     * @param body The notification body
     * @return true if notification was sent successfully, false otherwise
     */
    public boolean sendNotification(String userId, String title, String body) {
        try {
            UserProfile userProfile = userProfileDao.getUserProfile(userId);
            if (userProfile != null && userProfile.getFcmToken() != null && !userProfile.getFcmToken().isEmpty()) {
                try {
                    Message message = Message.builder()
                            .setToken(userProfile.getFcmToken())
                            .setNotification(Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build())
                            .build();

                    String response = firebaseMessaging.send(message);
                    System.out.println("Successfully sent notification to user " + userId + ": " + response);
                    return true;
                } catch (FirebaseMessagingException ex) {
                    System.err.println("Firebase Messaging Error for user " + userId + ": " + ex.getMessage());
                    System.err.println("Error Code: " + ex.getMessagingErrorCode());
                    
                    // Handle specific error cases
                    if (ex.getMessagingErrorCode() != null) {
                        switch (ex.getMessagingErrorCode()) {
                            case SENDER_ID_MISMATCH:
                                System.err.println("SENDER_ID_MISMATCH: The FCM token doesn't match the sender ID. Please ensure the client app is using the correct Firebase project.");
                                break;
                            case INVALID_ARGUMENT:
                                System.err.println("INVALID_ARGUMENT: Invalid FCM token format.");
                                break;
                            case UNREGISTERED:
                                System.err.println("UNREGISTERED: The FCM token is no longer valid. User may have uninstalled the app.");
                                // Optionally remove the invalid token
                                try {
                                    userProfile.setFcmToken(null);
                                    userProfileDao.updateUserProfile(userProfile);
                                    System.out.println("Removed invalid FCM token for user: " + userId);
                                } catch (Exception e) {
                                    System.err.println("Failed to remove invalid FCM token: " + e.getMessage());
                                }
                                break;
                            case QUOTA_EXCEEDED:
                                System.err.println("QUOTA_EXCEEDED: FCM quota exceeded. Please check your Firebase project quotas.");
                                break;
                            default:
                                System.err.println("Other FCM error: " + ex.getMessagingErrorCode());
                        }
                    }
                    return false;
                }
            } else {
                System.out.println("User not found or FCM token not available for user: " + userId);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error sending notification to user " + userId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Send notification to multiple users
     * @param userIds Array of user IDs to send notification to
     * @param title The notification title
     * @param body The notification body
     * @return Number of notifications sent successfully
     */
    public int sendNotificationToMultipleUsers(String[] userIds, String title, String body) {
        int successCount = 0;
        for (String userId : userIds) {
            if (sendNotification(userId, title, body)) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * Validate FCM token format (basic validation)
     * @param fcmToken The FCM token to validate
     * @return true if token appears valid, false otherwise
     */
    public boolean isValidFCMToken(String fcmToken) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            return false;
        }
        // Basic validation - FCM tokens are typically long strings
        return fcmToken.length() > 100 && fcmToken.matches("^[A-Za-z0-9_-]+$");
    }
}
