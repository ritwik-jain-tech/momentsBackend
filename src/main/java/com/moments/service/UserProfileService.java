package com.moments.service;

import com.moments.dao.UserProfileDao;
import com.moments.models.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutionException;

@Service
public class UserProfileService {

    private final UserProfileDao userProfileDao;

    @Autowired
    public UserProfileService(UserProfileDao userProfileDao) {
        this.userProfileDao = userProfileDao;
    }

    public void createUser(UserProfile userProfile) throws ExecutionException, InterruptedException {
        userProfileDao.createUserProfile(userProfile);
    }

    public UserProfile getUser(String userId) throws ExecutionException, InterruptedException {
        return userProfileDao.getUserProfile(userId);
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
}
