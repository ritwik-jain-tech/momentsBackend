package com.moments.controller;

import com.moments.models.Moment;
import com.moments.models.MomentsRequest;
import com.moments.models.MomentsResponse;
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
    public ResponseEntity<?> createOrUpdateMoment(@RequestBody Moment moment) {
        try {
            String result = momentService.saveMoment(moment);
            return ResponseEntity.ok("Created Moment with ID :"+result);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving the moment: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<?> createOrUpdateMoments(@RequestBody List<Moment> moments) {
        try {
            List<String> results = momentService.saveMoments(moments); // Implement this method in the service
            return ResponseEntity.ok("Created Moments with IDs: " + String.join(", ", results));
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving the moments: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // Get a Moment by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getMomentById(@PathVariable String id) {
        try {
            Moment moment = momentService.getMomentById(id);
            return ResponseEntity.ok(moment);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving the moment: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Moment not found with ID: " + id);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // Get all Moments
    @GetMapping
    public ResponseEntity<?> getAllMoments() {
        try {
            List<Moment> moments = momentService.getAllMoments();
            return ResponseEntity.ok(moments);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving moments: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/feed")
    public ResponseEntity<MomentsResponse> getMomentsFeed(@RequestBody MomentsRequest request){
        try {
            MomentsResponse response = momentService.findMoments(request.getEventId(), request.getFilter(), request.getCursor());
            return ResponseEntity.ok(response);
        } catch (ExecutionException  | InterruptedException e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Delete a Moment by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMoment(@PathVariable String id) {
        try {
            momentService.deleteMoment(id);
            return ResponseEntity.ok("Moment deleted successfully");
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting the moment: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Moment not found with ID: " + id);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }
}
