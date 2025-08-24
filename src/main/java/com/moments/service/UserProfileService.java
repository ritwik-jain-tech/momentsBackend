package com.moments.service;

import com.moments.dao.EventDao;
import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutionException;

@Service
public class UserProfileService {

    private final UserProfileDao userProfileDao;
    private final EventDao eventDao;

    @Autowired
    public UserProfileService(UserProfileDao userProfileDao, EventDao eventDao) {
        this.userProfileDao = userProfileDao;
        this.eventDao = eventDao;
    }

    public Long createUser(UserProfile userProfile) throws ExecutionException, InterruptedException {
        return userProfileDao.createUserProfile(userProfile);
    }

    public UserProfile getUser(String userId) throws ExecutionException, InterruptedException {
       UserProfile userProfile = userProfileDao.getUserProfile(userId);
       userProfile.setEventDetails(eventDao.getEventsByIds(userProfile.getEventIds()));
       return userProfile;
    }

    public void updateUser(UserProfile userProfile) throws ExecutionException, InterruptedException {
        userProfileDao.updateUserProfile(userProfile);
    }

    public void deleteUser(String userId) throws ExecutionException, InterruptedException {
        userProfileDao.deleteUserProfile(userId);
    }

    public UserProfile getUserProfileByPhoneNumber(String phoneNumber) throws ExecutionException, InterruptedException {
        return userProfileDao.findByPhoneNumber(phoneNumber);
    }

    public UserProfile addUserToEvent(String userId, String eventId) throws ExecutionException, InterruptedException {
       UserProfile userProfile = userProfileDao.addUserToEvent(userId, eventId);
        eventDao.addUserToEvent(eventId, userId);
        return userProfile;
    }

    public void blockUser(String blockingUserId, String blockedUserId) throws ExecutionException, InterruptedException {
        userProfileDao.blockUser(blockingUserId, blockedUserId);
    }

    public void unblockUser(String unblockingUserId, String blockedUserId) throws ExecutionException, InterruptedException {
      userProfileDao.unblockUser(unblockingUserId, blockedUserId);
    }

    public void deleteUserByPhoneNumber(String phoneNumber) throws ExecutionException, InterruptedException {
        UserProfile userProfile = getUserProfileByPhoneNumber(phoneNumber);
        if (userProfile != null) {
            deleteUser(userProfile.getUserId());
        }
    }
}
