package com.moments.models;

import org.springframework.http.HttpStatus;

public class BaseResponse {

    String message;

    HttpStatus status;

    Object data;

    public BaseResponse(String message, HttpStatus status, Object data) {
        this.message = message;
        this.status = status;
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }
    public Object getData() {return data;}
    public void setData(Object data) {this.data = data;}
}
