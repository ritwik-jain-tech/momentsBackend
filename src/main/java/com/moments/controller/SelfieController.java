package com.moments.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moments.models.BaseResponse;
import com.moments.models.FaceTaggingResult;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.UserProfile;
import com.moments.service.FaceTaggingService;
import com.moments.service.GoogleCloudStorageService;
import com.moments.service.UserProfileService;

@RestController
@RequestMapping("/api/selfie")
@CrossOrigin(origins = "*")
public class SelfieController {

    private static final Logger logger = LoggerFactory.getLogger(SelfieController.class);

    @Autowired
    private GoogleCloudStorageService googleCloudStorageService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private FaceTaggingService faceTaggingService;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse> uploadSelfie(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("userId") String userId,
            @RequestParam(value = "eventId", required = false) String eventId) {

        logger.info("Received selfie upload request for userId: {}", userId);
        FaceTaggingResult result = null;
        try {
            // Validate request
            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new BaseResponse("Image file is required", HttpStatus.BAD_REQUEST, null));
            }

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new BaseResponse("User ID is required", HttpStatus.BAD_REQUEST, null));
            }

            // Process the selfie image and save face embedding

            // Upload image to cloud storage and get public URL
            FileUploadResponse uploadResponse = googleCloudStorageService.uploadFile(imageFile, FileType.IMAGE);
            String selfieUrl = uploadResponse.getPublicUrl();

            // Update user profile with selfie URL
            UserProfile userProfile = userProfileService.getUser(userId);
            if (userProfile != null) {
                userProfile.setSelfie(selfieUrl);
                userProfileService.updateUser(userProfile);
                logger.info("Updated user profile with selfie URL for userId: {}", userId);
            } else {
                logger.warn("User profile not found for userId: {}", userId);
            }

            // Trigger face tagging service call (sync with fail safety)
            if (eventId != null && !eventId.trim().isEmpty()) {
                logger.info("Triggering face tagging service for selfie processing, userId: {}, eventId: {}", userId,
                        eventId);
                try {
                    result = faceTaggingService.processSelfieSync(userId, selfieUrl, eventId);
                } catch (Exception e) {
                    // Log error but don't fail the main selfie upload process
                    logger.error("Face tagging service call failed for selfie, userId: {}, eventId: {}, error: {}",
                            userId, eventId, e.getMessage(), e);
                }
            } else {
                logger.warn("No eventId provided for selfie upload, skipping face tagging service call for userId: {}",
                        userId);
            }

            if (result != null && result.getHttpStatus() == 200)
                return ResponseEntity.ok(new BaseResponse(
                    "Selfie uploaded and face embedding saved successfully",
                    HttpStatus.OK,
                    result));
            else
                return ResponseEntity.badRequest()
                        .body(new BaseResponse(result.getMessage(), HttpStatus.BAD_REQUEST, result));

        } catch (Exception e) {
            logger.error("Error processing selfie: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(
                            "Error processing selfie: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null));
        }
    }

}
