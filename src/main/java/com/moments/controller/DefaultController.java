package com.moments.controller;


import com.moments.models.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/moments")
public class DefaultController {
    @GetMapping("/hello")
    public ResponseEntity<BaseResponse> getUserProfileByPhoneNumber() {
        return  ResponseEntity.status(HttpStatus.OK).body(new BaseResponse("Hello", HttpStatus.OK,null));
    }
}
