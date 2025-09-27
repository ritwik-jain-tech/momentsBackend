package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.service.UserFaceEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/selfie")
@CrossOrigin(origins = "*")
public class SelfieController {
    
    private static final Logger logger = LoggerFactory.getLogger(SelfieController.class);
    
    @Autowired
    private UserFaceEmbeddingService userFaceEmbeddingService;
    
    @PostMapping("/upload")
    public ResponseEntity<BaseResponse> uploadSelfie(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("userId") String userId) {
        
        logger.info("Received selfie upload request for userId: {}", userId);
        
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
            String embeddingId = userFaceEmbeddingService.saveUserFaceEmbedding(userId, imageFile);
            
            if (embeddingId == null) {
                return ResponseEntity.badRequest()
                    .body(new BaseResponse("Failed to process selfie - no face detected or processing error", HttpStatus.BAD_REQUEST, null));
            }
            
            logger.info("Selfie processing completed successfully for userId: {}, embeddingId: {}", userId, embeddingId);
            
            return ResponseEntity.ok(new BaseResponse(
                "Selfie uploaded and face embedding saved successfully", 
                HttpStatus.OK, 
                new SelfieUploadResponse(embeddingId, userId)
            ));
            
        } catch (Exception e) {
            logger.error("Error processing selfie: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(
                    "Error processing selfie: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    null
                ));
        }
    }
    
    // Inner class for response
    public static class SelfieUploadResponse {
        private String embeddingId;
        private String userId;
        
        public SelfieUploadResponse(String embeddingId, String userId) {
            this.embeddingId = embeddingId;
            this.userId = userId;
        }
        
        public String getEmbeddingId() {
            return embeddingId;
        }
        
        public void setEmbeddingId(String embeddingId) {
            this.embeddingId = embeddingId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
