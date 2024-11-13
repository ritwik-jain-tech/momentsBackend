package com.moments.dao;

import com.moments.models.UserProfile;
import java.util.concurrent.ExecutionException;

public interface UserProfileDao {
    void createUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException;

    UserProfile getUserProfile(String userId) throws ExecutionException, InterruptedException;

    UserProfile findByPhoneNumber(String userId) throws ExecutionException, InterruptedException;

    void updateUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException;

    void deleteUserProfile(String userId) throws ExecutionException, InterruptedException;
}

