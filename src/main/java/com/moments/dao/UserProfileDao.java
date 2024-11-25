package com.moments.dao;

import com.moments.models.UserProfile;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface UserProfileDao {
    Long createUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException;

    UserProfile getUserProfile(String userId) throws ExecutionException, InterruptedException;

    List<UserProfile> getUserProfiles(List<String> userIds) throws ExecutionException, InterruptedException;

    UserProfile findByPhoneNumber(String userId) throws ExecutionException, InterruptedException;

    void updateUserProfile(UserProfile userProfile) throws ExecutionException, InterruptedException;

    void deleteUserProfile(String userId) throws ExecutionException, InterruptedException;

    UserProfile addUserToEvent(String userId,String eventId) throws ExecutionException, InterruptedException;





}

