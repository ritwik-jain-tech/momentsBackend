package com.moments.controller;

import com.moments.models.*;
import com.moments.service.MomentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/moments")
public class MomentController {

    @Autowired
    private MomentService momentService;

    // Create or Update a Moment
    @PostMapping
    public ResponseEntity<BaseResponse> createOrUpdateMoment(@RequestBody Moment moment) {
        try {
            String result = momentService.saveMoment(moment);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Created moment with Id : "+result,HttpStatus.OK,moment));

        } catch (ExecutionException | InterruptedException e) {
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(new BaseResponse("Failed to create moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,moment));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<BaseResponse> createOrUpdateMoments(@RequestBody List<Moment> moments) {
        try {
            List<String> results = momentService.saveMoments(moments); // Implement this method in the service
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Created moment with IDs: " + results,HttpStatus.OK,null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    // Get a Moment by ID
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse> getMomentById(@PathVariable String id) {
        try {
            Moment moment = momentService.getMomentById(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Moment with Id : "+id,HttpStatus.OK,moment));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    // Get all Moments
    @GetMapping
    public ResponseEntity<?> getAllMoments() {
        try {
            List<Moment> moments = momentService.getAllMoments();
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success",HttpStatus.OK,moments));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    @PostMapping("/feed")
    public ResponseEntity<BaseResponse> getMomentsFeed(@RequestBody MomentsRequest request){
        try {
            MomentsResponse response = momentService.findMoments(request.getEventId(), request.getFilter(), request.getCursor(), request.getUserId());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success",HttpStatus.OK,response));
        } catch (ExecutionException  | InterruptedException e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    // Delete a Moment by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse> deleteMoment(@PathVariable String id) {
        try {
            momentService.deleteMoment(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success",HttpStatus.OK,null));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    @PostMapping("/report")
    public ResponseEntity<BaseResponse> reportMoment(@RequestBody ReportRequest reportRequest){
         try{
             boolean status = momentService.reportMoment(reportRequest);
             return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Report status: "+ status,HttpStatus.OK,status));
         } catch (ExecutionException | InterruptedException e) {
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                     .body(new BaseResponse("Failed to get moment error: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
         }
    }

    @PatchMapping("/status")
    public ResponseEntity<BaseResponse> updateMomentStatus(
            @RequestBody UpdateMomentStatusRequest request) {
        try {
            String result = momentService.updateMomentStatus(request.getMomentId(), request.getStatus());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Moment status updated successfully", HttpStatus.OK, result));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to update moment status: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        }
    }

    @PostMapping("/like")
    public ResponseEntity<BaseResponse> likeMoment(@RequestBody LikeRequest likeRequest){
        try{
            boolean status = momentService.likeMoment(likeRequest);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Like status: "+ status,HttpStatus.OK,status));
        } catch (Exception  e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to like moment: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    @PostMapping("/liked-feed")
    public ResponseEntity<BaseResponse> getLikedMomentsFeed(@RequestBody LikedMomentsRequest request){
        try {
            MomentsResponse response = momentService.getLikedMomentsFeed(request.getUserId(), request.getEventId(), request.getCursor());
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success",HttpStatus.OK,response));
        } catch (ExecutionException | InterruptedException e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to get liked moments: "+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR,null));
        }
    }

    // Create a Moment with Face Recognition
    @PostMapping("/with-face-recognition")
    public ResponseEntity<BaseResponse> createMomentWithFaceRecognition(
            @RequestParam("imageFile") org.springframework.web.multipart.MultipartFile imageFile,
            @RequestParam("moment") String momentJson) {
        try {
            // Parse moment from JSON
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Moment moment = objectMapper.readValue(momentJson, Moment.class);
            
            String result = momentService.saveMomentWithFaceRecognition(moment, imageFile);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Created moment with face recognition, Id: " + result, HttpStatus.OK, moment));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create moment with face recognition, error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}
