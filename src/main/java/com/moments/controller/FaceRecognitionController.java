package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.FaceRecognitionRequest;
import com.moments.models.FaceRecognitionResponse;
import com.moments.service.FaceRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/face-recognition")
@CrossOrigin(origins = "*")
public class FaceRecognitionController {
    
    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionController.class);
    
    @Autowired
    private FaceRecognitionService faceRecognitionService;
    
    @PostMapping("/compare")
    public ResponseEntity<BaseResponse> compareFaces(@RequestBody FaceRecognitionRequest request) {
        logger.info("Received face recognition request for person: {} and group: {}", 
            request.getPersonImageUrl(), request.getGroupImageUrl());
        
        try {
            // Validate request
            if (request.getPersonImageUrl() == null || request.getPersonImageUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new BaseResponse("Person image URL is required", HttpStatus.BAD_REQUEST, null));
            }
            
            if (request.getGroupImageUrl() == null || request.getGroupImageUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new BaseResponse("Group image URL is required", HttpStatus.BAD_REQUEST, null));
            }
            
            // Call face recognition service
            boolean isPersonInImage = faceRecognitionService.isPersonInImage(
                request.getPersonImageUrl(), 
                request.getGroupImageUrl()
            );
            
            // Create response
            String message = isPersonInImage ? 
                "Person found in the group image" : 
                "Person not found in the group image";
            
            FaceRecognitionResponse response = new FaceRecognitionResponse(isPersonInImage, message);
            
            logger.info("Face recognition completed successfully. Result: {}", isPersonInImage);
            
            return ResponseEntity.ok(new BaseResponse(
                "Face recognition completed successfully", 
                HttpStatus.OK, 
                response
            ));
            
        } catch (IOException e) {
            logger.error("IO error during face recognition: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(
                    "Error processing images: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    null
                ));
        } catch (Exception e) {
            logger.error("Unexpected error during face recognition: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse(
                    "Face recognition failed: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    null
                ));
        }
    }
}
