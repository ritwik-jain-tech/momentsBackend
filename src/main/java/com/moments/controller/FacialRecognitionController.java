package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.Moment;
import com.moments.service.FacialRecognitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/facial-recognition")
public class FacialRecognitionController {

    @Autowired
    private FacialRecognitionService facialRecognitionService;

    @PostMapping("/search/{eventId}")
    public ResponseEntity<BaseResponse> searchFaces(
            @PathVariable String eventId,
            @RequestParam("image") MultipartFile image) {
        try {
            // Upload the image to get its URL
            String imageUrl = facialRecognitionService.uploadImage(image);
            
            // Start the facial recognition process asynchronously
            CompletableFuture<List<Moment>> futureResults = facialRecognitionService.findSimilarFaces(eventId, imageUrl);
            
            // Return immediately with a processing message
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new BaseResponse(
                            "Facial recognition search started. Results will be available shortly.",
                            HttpStatus.ACCEPTED,
                            null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(
                            "Error starting facial recognition: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null));
        }
    }

    @GetMapping("/results/{eventId}/{imageId}")
    public ResponseEntity<BaseResponse> getSearchResults(
            @PathVariable String eventId,
            @PathVariable String imageId) {
        try {
            // Get the results from the facial recognition service
            List<Moment> results = facialRecognitionService.getSearchResults(eventId, imageId);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse(
                            "Facial recognition results",
                            HttpStatus.OK,
                            results));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(
                            "Error retrieving results: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null));
        }
    }
} 