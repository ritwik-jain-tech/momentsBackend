package com.moments.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FaceRecognitionRequest {
    
    @JsonProperty("personImageUrl")
    private String personImageUrl;
    
    @JsonProperty("groupImageUrl")
    private String groupImageUrl;
    
    public FaceRecognitionRequest() {
    }
    
    public FaceRecognitionRequest(String personImageUrl, String groupImageUrl) {
        this.personImageUrl = personImageUrl;
        this.groupImageUrl = groupImageUrl;
    }
    
    public String getPersonImageUrl() {
        return personImageUrl;
    }
    
    public void setPersonImageUrl(String personImageUrl) {
        this.personImageUrl = personImageUrl;
    }
    
    public String getGroupImageUrl() {
        return groupImageUrl;
    }
    
    public void setGroupImageUrl(String groupImageUrl) {
        this.groupImageUrl = groupImageUrl;
    }
}
