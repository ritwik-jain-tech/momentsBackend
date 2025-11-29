package com.moments.controller;

import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moments.models.BaseResponse;
import com.moments.models.BlockRequest;
import com.moments.models.Role;
import com.moments.models.UserProfile;
import com.moments.service.UserProfileService;

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
    public ResponseEntity<BaseResponse> createUserProfile(@RequestBody UserProfile userProfile) {
        try {
            boolean isGroomSide = userProfile.getSide() != null && userProfile.getSide().equals("groom");
            if (userProfile.getEventIds() == null || userProfile.getEventIds().isEmpty()) {

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("EventId can not be null or empty", HttpStatus.BAD_REQUEST, null));
            }
            if (!isValidPhoneNumber(userProfile.getPhoneNumber())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Invalid Phone number", HttpStatus.BAD_REQUEST, null));
            }
            UserProfile userProfileExisting = userProfileService
                    .getUserProfileByPhoneNumber(userProfile.getPhoneNumber());
            if (userProfileExisting == null) {
                String userId = userProfileService.createUser(userProfile).toString();
                userProfile.setUserId(userId);
                if (userProfile.getRole() == null) {
                    userProfile.setRole(Role.USER);
                }
                // Pass optional eventRoleName to addUserToEvent
                userProfileService.addUserToEvent(userId, userProfile.getEventIds().get(0), isGroomSide, userProfile.getEventRoleName());
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new BaseResponse("Success", HttpStatus.CREATED, userProfile));
            } else {
                if (userProfileExisting.getEventIds() != null
                        && userProfileExisting.getEventIds().contains(userProfile.getEventIds().get(0))) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new BaseResponse("Success", HttpStatus.CONFLICT, userProfileExisting));
                } else {
                    // Pass optional eventRoleName to addUserToEvent
                    userProfileService.addUserToEvent(userProfileExisting.getUserId(), userProfile.getEventIds().get(0),
                            isGroomSide, userProfile.getEventRoleName());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new BaseResponse("Success", HttpStatus.CREATED, userProfile));
                }
            }

        } catch (ExecutionException | InterruptedException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get user profile by userId
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse> getUserProfileById(@PathVariable String id) {
        try {
            UserProfile userProfile = userProfileService.getUser(id);
            if (userProfile != null) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Success", HttpStatus.OK, userProfile));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse("Success", HttpStatus.NOT_FOUND, null));
            }
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Update an existing user profile
    @PutMapping("/update")
    public ResponseEntity<BaseResponse> updateUserProfile(@RequestBody UserProfile userProfile) {
        try {
            userProfileService.updateUser(userProfile);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, userProfile));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Delete a user profile by userId
    @DeleteMapping("/delete")
    public ResponseEntity<BaseResponse> deleteUserProfile(@RequestParam String userId) {
        try {
            userProfileService.deleteUser(userId);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Deleted userId: " + userId, HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new BaseResponse("Error deleting : " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @GetMapping("/phone")
    public ResponseEntity<BaseResponse> getUserProfileByPhoneNumber(@RequestParam String phoneNumber) {
        UserProfile userProfile = null;
        try {
            userProfile = userProfileService.getUserProfileByPhoneNumber(phoneNumber);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
        return userProfile == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse("Success", HttpStatus.NOT_FOUND, null))
                : ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success", HttpStatus.OK, userProfile));
    }

    @PostMapping("/block")
    public ResponseEntity<BaseResponse> blockUser(@RequestBody BlockRequest blockRequest) {
        try {
            userProfileService.blockUser(blockRequest.getBlockingUserId(), blockRequest.getBlockedUserId());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("UserBlocked", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/unblock")
    public ResponseEntity<BaseResponse> unblockUser(@RequestBody BlockRequest blockRequest) {
        try {
            userProfileService.unblockUser(blockRequest.getBlockingUserId(), blockRequest.getBlockedUserId());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("User UnBlocked", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }

        // Regex to match exactly 10 digits
        String regex = "^\\d{10}$";
        return phoneNumber.matches(regex);
    }

    @DeleteMapping("/deleteByPhone")
    public ResponseEntity<BaseResponse> deleteUserByPhoneNumber(@RequestParam String phoneNumber) {
        try {
            if (!isValidPhoneNumber(phoneNumber)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Invalid phone number format", HttpStatus.BAD_REQUEST, null));
            }

            userProfileService.deleteUserByPhoneNumber(phoneNumber);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("User deleted successfully", HttpStatus.OK, null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Error deleting user: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                            null));
        }
    }

}
