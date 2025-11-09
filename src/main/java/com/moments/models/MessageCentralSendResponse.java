package com.moments.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageCentralSendResponse {
    @JsonProperty("data")
    private MessageCentralData data;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    public MessageCentralData getData() {
        return data;
    }

    public void setData(MessageCentralData data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class MessageCentralData {
        @JsonProperty("verificationId")
        private String verificationId;

        public String getVerificationId() {
            return verificationId;
        }

        public void setVerificationId(String verificationId) {
            this.verificationId = verificationId;
        }
    }
}

