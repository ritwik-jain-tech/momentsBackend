package com.moments.controller;



import com.moments.models.BaseResponse;
import com.moments.models.Event;
import com.moments.models.UserProfile;
import com.moments.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/event")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    @Autowired
    private EventService eventService; // Use the service layer

    // Create or Update an Event
    @PostMapping
    public ResponseEntity<BaseResponse> createOrUpdateEvent(@RequestBody Event event)  {
        try {
            String time = eventService.saveEvent(event);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success updated time: "+ time, HttpStatus.OK, event));

        }
        catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get an Event by ID
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse> getEventById(@PathVariable String id) throws ExecutionException, InterruptedException {
       try{
        Event event = eventService.getEventById(id);
        if(event != null){
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success getting event", HttpStatus.OK, event));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new BaseResponse("Event not found", HttpStatus.NOT_FOUND, event));

       } catch (ExecutionException | InterruptedException e) {
           log.error(e.getMessage());
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
       }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<BaseResponse> getUsersInEventById(@PathVariable String id) throws ExecutionException, InterruptedException {
        try{
            List<UserProfile> userProfiles = eventService.getAllUserProfilesInEvent(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success getting Users for Event", HttpStatus.OK, userProfiles));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Get all Events
    @GetMapping
    public ResponseEntity<BaseResponse> getAllEvents() {
        try {
            List<Event> allEvents = eventService.getAllEvents();
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success getting events", HttpStatus.OK, allEvents));
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    // Delete an Event by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse> deleteEvent(@PathVariable String id) {
        try {
            eventService.deleteEvent(id);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Deleted ", HttpStatus.OK, null));

        }
        catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @GetMapping("/addUser")
    public ResponseEntity<BaseResponse> addUser(@RequestParam String userId, @RequestParam String eventId, @RequestParam boolean isGroomSide) {
        try{
            Event event = eventService.addUserToEvent(userId, eventId, isGroomSide);
            if(event != null){
                return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Success adding user", HttpStatus.OK, event));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new BaseResponse("Event not found", HttpStatus.NOT_FOUND, null));

        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}

