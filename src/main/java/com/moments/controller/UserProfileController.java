package com.moments.controller;

import com.moments.models.Role;
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
            if(userProfile.getRole()==null){
                userProfile.setRole(Role.USER);
            }
            if(userProfile.getEventIds()==null || userProfile.getEventIds().isEmpty()){
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            if(!isValidPhoneNumber(userProfile.getPhoneNumber())){
                return new ResponseEntity<>("Invalid phone number. It must be a 10-digit number.", HttpStatus.BAD_REQUEST);
            }
           UserProfile userProfileExisting = userProfileService.getUserProfileByPhoneNumber(userProfile.getPhoneNumber());
            if(userProfileExisting==null){
                String userId = userProfileService.createUser(userProfile).toString();
                userProfileService.addUserToEvent(userId, userProfile.getEventIds().get(0));
                return ResponseEntity.status(HttpStatus.CREATED).body("User profile created successfully and added to event. userId "+ userId + ", eventId :"+  userProfile.getEventIds().get(0));
            } else {

                return ResponseEntity.status(HttpStatus.CONFLICT).body("User profile already exists:"+ userProfileExisting.toString());
            }

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating user profile: " + e.getMessage());
        }
    }

    // Get user profile by userId
    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getUserProfileById(@PathVariable String id) {
        try {
            UserProfile userProfile = userProfileService.getUser(id);
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

    @GetMapping("/phone")
    public ResponseEntity<UserProfile> getUserProfileByPhoneNumber(@RequestParam String phoneNumber) {
        UserProfile userProfile = null;
        try {
            userProfile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
        } catch (ExecutionException  | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return userProfile==null? (ResponseEntity<UserProfile>) ResponseEntity.notFound() : ResponseEntity.ok(userProfile);
    }

    @GetMapping("/verify/otp")
    public ResponseEntity<UserProfile> verifyOTP(@RequestParam String phoneNumber
                                                    , @RequestParam String otp,
                                                 @RequestParam String eventId) {
        //TODO: Validate OTP
        String userIdFromOTP = "1";
        try {
            if (userIdFromOTP != null) {
                return ResponseEntity.ok(userProfileService.addUserToEvent(userIdFromOTP, eventId));
            }
        }catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);

    }
        return ResponseEntity.ok(null);
    }


    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }

        // Regex to match exactly 10 digits
        String regex = "^\\d{10}$";
        return phoneNumber.matches(regex);
    }


}
