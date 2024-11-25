package com.moments.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/moments")
public class DefaultController {
    @GetMapping("/hello")
    public ResponseEntity<String> getUserProfileByPhoneNumber() {
        return  ResponseEntity.ok("Hello Moments");
    }
}
