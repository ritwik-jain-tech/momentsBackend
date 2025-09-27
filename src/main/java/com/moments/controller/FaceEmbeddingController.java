package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.FaceEmbeddingRequest;
import com.moments.models.FaceEmbeddingResponse;
import com.moments.service.FaceRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/face-embedding")
@CrossOrigin(origins = "*")
public class FaceEmbeddingController {
    
    private static final Logger logger = LoggerFactory.getLogger(FaceEmbeddingController.class);
    
    @Autowired
    private FaceRecognitionService faceRecognitionService;
    
    @PostMapping("/process-moment")
    public ResponseEntity<BaseResponse> processMomentImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("momentId") String momentId) {
        
        logger.info("Received moment image processing request for momentId: {}", momentId);
        
        try {
            // Validate request
            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new BaseResponse("Image file is required", HttpStatus.BAD_REQUEST, null));
            }
            
            if (momentId == null || momentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new BaseResponse("Moment ID is required", HttpStatus.BAD_REQUEST, null));
            }
            
            // Process the moment image
            FaceEmbeddingResponse response = faceRecognitionService.processMomentImage(imageFile, momentId);
            
            logger.info("Moment image processing completed. Success: {}, Faces detected: {}, Faces matched: {}", 
                response.isSuccess(), response.getFacesDetected(), response.getFacesMatched());
            
            return ResponseEntity.ok(new BaseResponse(
                response.getMessage(), 
                HttpStatus.OK, 
                response
            ));
            
        } catch (Exception e) {
            logger.error("Error processing moment image: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(
                    "Error processing moment image: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    null
                ));
        }
    }
    
    @PostMapping("/process-selfie")
    public ResponseEntity<BaseResponse> processSelfieImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("userId") String userId) {
        
        logger.info("Received selfie image processing request for userId: {}", userId);
        
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
            
            // Process the selfie image
            FaceEmbeddingResponse response = faceRecognitionService.processSelfieImage(imageFile, userId);
            
            logger.info("Selfie image processing completed. Success: {}, Faces detected: {}, Moments matched: {}", 
                response.isSuccess(), response.getFacesDetected(), response.getFacesMatched());
            
            return ResponseEntity.ok(new BaseResponse(
                response.getMessage(), 
                HttpStatus.OK, 
                response
            ));
            
        } catch (Exception e) {
            logger.error("Error processing selfie image: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(
                    "Error processing selfie image: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    null
                ));
        }
    }
}
