package com.moments.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageCentralVerifyResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private MessageCentralVerifyData data;

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

    public MessageCentralVerifyData getData() {
        return data;
    }

    public void setData(MessageCentralVerifyData data) {
        this.data = data;
    }

    public static class MessageCentralVerifyData {
        @JsonProperty("verificationStatus")
        private String verificationStatus;

        public String getVerificationStatus() {
            return verificationStatus;
        }

        public void setVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
        }
    }
}

