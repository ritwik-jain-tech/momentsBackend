package com.moments.service;

import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class NotificationService {

    @Autowired
    private UserProfileDao userProfileDao;

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
                // TODO: Implement actual FCM notification sending logic here
                // This would typically involve calling Firebase Admin SDK
                System.out.println("Sending notification to user " + userId + " with FCM token: " + userProfile.getFcmToken());
                System.out.println("Title: " + title);
                System.out.println("Body: " + body);
                return true;
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
}
