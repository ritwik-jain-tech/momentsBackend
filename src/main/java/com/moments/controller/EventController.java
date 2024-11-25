package com.moments.controller;



import com.moments.models.Event;
import com.moments.models.UserProfile;
import com.moments.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/event")
public class EventController {

    @Autowired
    private EventService eventService; // Use the service layer

    // Create or Update an Event
    @PostMapping
    public ResponseEntity<String> createOrUpdateEvent(@RequestBody Event event)  {
        try {
            return ResponseEntity.ok(eventService.saveEvent(event));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get an Event by ID
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable String id) throws ExecutionException, InterruptedException {
       try{
        Event event = eventService.getEventById(id);
        if(event != null){
            return ResponseEntity.ok(event);
        }
        return ResponseEntity.notFound().build();

       } catch (ExecutionException | InterruptedException e) {
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
       }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<List<UserProfile>> getUsersInEventById(@PathVariable String id) throws ExecutionException, InterruptedException {
        try{
            List<UserProfile> userProfiles = eventService.getAllUserProfilesInEvent(id);
            return ResponseEntity.ok(userProfiles);

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Get all Events
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    // Delete an Event by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEvent(@PathVariable String id) throws ExecutionException, InterruptedException {
        eventService.deleteEvent(id);
        return ResponseEntity.ok("Event deleted successfully");
    }

    @GetMapping("/addUser")
    public ResponseEntity<Event> addUser(@RequestParam String userId, @RequestParam String eventId) {
        try{
            Event event = eventService.addUserToEvent(eventId, userId);
            if(event != null){
                return ResponseEntity.ok(event);
            }
            return ResponseEntity.notFound().build();

        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}

