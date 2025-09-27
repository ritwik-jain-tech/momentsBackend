package com.moments.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FaceRecognitionResponse {
    
    @JsonProperty("isMatch")
    private boolean isMatch;
    
    @JsonProperty("message")
    private String message;
    
    public FaceRecognitionResponse() {
    }
    
    public FaceRecognitionResponse(boolean isMatch, String message) {
        this.isMatch = isMatch;
        this.message = message;
    }
    
    public boolean isMatch() {
        return isMatch;
    }
    
    public void setMatch(boolean isMatch) {
        this.isMatch = isMatch;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
