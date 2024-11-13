package com.moments.controller;

import com.moments.models.UserProfile;
import com.moments.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/userProfile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Autowired
    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // Create a new user profile
    @PostMapping("/create")
    public ResponseEntity<String> createUserProfile(@RequestBody UserProfile userProfile) {
        try {
            userProfileService.createUser(userProfile);
            return ResponseEntity.status(HttpStatus.CREATED).body("User profile created successfully.");
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating user profile: " + e.getMessage());
        }
    }

    // Get user profile by userId
    @GetMapping("/get")
    public ResponseEntity<UserProfile> getUserProfile(@RequestParam String userId) {
        try {
            UserProfile userProfile = userProfileService.getUser(userId);
            if (userProfile != null) {
                return ResponseEntity.ok(userProfile);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Update an existing user profile
    @PutMapping("/update")
    public ResponseEntity<String> updateUserProfile(@RequestBody UserProfile userProfile) {
        try {
            userProfileService.updateUser(userProfile);
            return ResponseEntity.ok("User profile updated successfully.");
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user profile: " + e.getMessage());
        }
    }

    // Delete a user profile by userId
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteUserProfile(@RequestParam String userId) {
        try {
            userProfileService.deleteUser(userId);
            return ResponseEntity.ok("User profile deleted successfully.");
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting user profile: " + e.getMessage());
        }
    }

    @GetMapping("/phone/")
    public ResponseEntity<UserProfile> getUserProfileByPhoneNumber(@RequestParam String phoneNumber) {
        UserProfile userProfile = null;
        try {
            userProfile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
        } catch (ExecutionException  | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return userProfile==null? (ResponseEntity<UserProfile>) ResponseEntity.notFound() : ResponseEntity.ok(userProfile);
    }
}
