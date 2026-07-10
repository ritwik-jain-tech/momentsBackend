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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moments.models.BaseResponse;
import com.moments.models.BulkEventRoleRequest;
import com.moments.models.Event;
import com.moments.models.EventRole;
import com.moments.models.ReviewExportRequest;
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

    /** Create a new event (server assigns {@code eventId} when missing). */
    @PostMapping
    public ResponseEntity<BaseResponse> createEvent(@RequestBody Event event) {
        try {
            String time = eventService.saveEvent(event);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Event created. " + time, HttpStatus.OK, event));

        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /** Partial update of an existing event by id. */
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse> updateEvent(@PathVariable String id, @RequestBody Event event) {
        try {
            event.setEventId(id);
            Event updated = eventService.updateEvent(id, event);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Event updated", HttpStatus.OK, updated));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Event not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
            }
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Must be registered before {@code /{id}} so the path segment {@code for-user} is not treated as an event id.
     */
    @GetMapping("/for-user")
    public ResponseEntity<BaseResponse> getEventsForUser(@RequestParam("userId") String userId) {
        try {
            List<Event> events = eventService.getEventsForMemberUser(userId);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success getting events for user", HttpStatus.OK, events));
        } catch (IllegalArgumentException e) {
            HttpStatus st = e.getMessage() != null && e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(st)
                    .body(new BaseResponse(e.getMessage(), st, null));
        } catch (ExecutionException | InterruptedException e) {
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

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Event not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
            }
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /** Photographer exports approved moments to the public client review page. Returns the shareable token. */
    @PostMapping("/{eventId}/review/export")
    public ResponseEntity<BaseResponse> exportToReview(@PathVariable String eventId,
            @RequestBody ReviewExportRequest request) {
        try {
            if (request == null || request.getUserId() == null || request.getUserId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("userId is required", HttpStatus.BAD_REQUEST, null));
            }
            Event event = eventService.exportToReview(eventId, request.getUserId());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Exported to review", HttpStatus.OK, reviewInfo(event)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.FORBIDDEN, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /** Public: event meta for the review/album pages (no login). Safe subset only. */
    @GetMapping("/review/{reviewToken}")
    public ResponseEntity<BaseResponse> getReviewInfo(@PathVariable String reviewToken) {
        try {
            Event event = eventService.getEventByReviewToken(reviewToken);
            if (event == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new BaseResponse("Invalid review token", HttpStatus.NOT_FOUND, null));
            }
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Success", HttpStatus.OK, reviewInfo(event)));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /** Public (token-scoped): client marks their album selection final, unlocking the album page. */
    @PostMapping("/review/{reviewToken}/finalize")
    public ResponseEntity<BaseResponse> finalizeAlbum(@PathVariable String reviewToken) {
        try {
            Event event = eventService.finalizeAlbum(reviewToken);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new BaseResponse("Album finalized", HttpStatus.OK, reviewInfo(event)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.NOT_FOUND, null));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /** Safe, public-facing subset of an event for the review/album pages. */
    private java.util.Map<String, Object> reviewInfo(Event event) {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("eventId", event.getEventId());
        info.put("eventName", event.getEventName());
        info.put("eventDate", event.getEventDate());
        info.put("eventThumbnail", event.getEventThumbnail());
        info.put("reviewToken", event.getReviewToken());
        info.put("reviewEnabled", event.isReviewEnabled());
        info.put("albumFinalized", event.isAlbumFinalized());
        return info;
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
