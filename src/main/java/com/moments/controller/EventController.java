package com.moments.controller;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moments.models.BaseResponse;
import com.moments.models.BulkEventRoleRequest;
import com.moments.models.Event;
import com.moments.models.EventRole;
import com.moments.models.UserProfile;
import com.moments.service.EventRoleService;
import com.moments.service.EventService;

@RestController
@RequestMapping("/api/event")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    @Autowired
    private EventService eventService; // Use the service layer
    
    @Autowired
    private EventRoleService eventRoleService;

    // Create or Update an Event
    @PostMapping
    public ResponseEntity<BaseResponse> createOrUpdateEvent(@RequestBody Event event) {
        try {
            String time = eventService.saveEvent(event);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success updated time: " + time, HttpStatus.OK, event));

        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get an Event by ID
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse> getEventById(@PathVariable String id)
            throws ExecutionException, InterruptedException {
        try {
            Event event = eventService.getEventById(id);
            if (event != null) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Success getting event", HttpStatus.OK, event));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse("Event not found", HttpStatus.NOT_FOUND, event));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<BaseResponse> getUsersInEventById(@PathVariable String id,
            @RequestParam(value = "userId", required = false) String userId)
            throws ExecutionException, InterruptedException {
        try {
            List<UserProfile> userProfiles = eventService.getAllUserProfilesInEventWithRoles(id, userId);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting Users for Event", HttpStatus.OK, userProfiles));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get all Events
    @GetMapping
    public ResponseEntity<BaseResponse> getAllEvents() {
        try {
            List<Event> allEvents = eventService.getAllEvents();
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting events", HttpStatus.OK, allEvents));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Delete an Event by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse> deleteEvent(@PathVariable String id) {
        try {
            eventService.deleteEvent(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Deleted ", HttpStatus.OK, null));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @GetMapping("/addUser")
    public ResponseEntity<BaseResponse> addUser(@RequestParam String userId, @RequestParam String eventId,
            @RequestParam boolean isGroomSide, @RequestParam(required = false) String roleName) {
        try {
            Event event = eventService.addUserToEvent(userId, eventId, isGroomSide, roleName);
            if (event != null) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new BaseResponse("Success adding user", HttpStatus.OK, event));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse("Event not found", HttpStatus.NOT_FOUND, null));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
    
    @PostMapping("/roles/bulk")
    public ResponseEntity<BaseResponse> bulkCreateOrUpdateEventRoles(@RequestBody BulkEventRoleRequest request) {
        try {
            if (request == null || request.getEventRoles() == null || request.getEventRoles().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Request body cannot be empty", HttpStatus.BAD_REQUEST, null));
            }
            
            List<EventRole> createdOrUpdatedRoles = eventRoleService.bulkCreateOrUpdateEventRoles(request);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Successfully created/updated " + createdOrUpdatedRoles.size() + " event roles", 
                            HttpStatus.OK, createdOrUpdatedRoles));

        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in bulk create/update event roles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create/update event roles: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (Exception e) {
            log.error("Unexpected error in bulk create/update event roles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Unexpected error: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}
