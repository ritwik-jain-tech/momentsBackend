package com.moments.models;

/**
 * Response model for face tagging service results
 * Contains all relevant information from the face tagging API response
 */
public class FaceTaggingResult {

    private boolean success;
    private int httpStatus;
    private String message;
    private int faceCount;
    private int matchCount;
    private Double averageQualityScore;
    private String rawResponse;

    // Default constructor
    public FaceTaggingResult() {
        this.success = false;
        this.httpStatus = 500;
        this.message = "Unknown error";
        this.faceCount = 0;
        this.matchCount = 0;
        this.averageQualityScore = null;
        this.rawResponse = null;
    }

    // Constructor with basic parameters
    public FaceTaggingResult(boolean success, int httpStatus, String message) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.message = message;
        this.faceCount = 0;
        this.matchCount = 0;
        this.averageQualityScore = null;
        this.rawResponse = null;
    }

    // Full constructor
    public FaceTaggingResult(boolean success, int httpStatus, String message,
            int faceCount, int matchCount, Double averageQualityScore, String rawResponse) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.message = message;
        this.faceCount = faceCount;
        this.matchCount = matchCount;
        this.averageQualityScore = averageQualityScore;
        this.rawResponse = rawResponse;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getFaceCount() {
        return faceCount;
    }

    public void setFaceCount(int faceCount) {
        this.faceCount = faceCount;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public Double getAverageQualityScore() {
        return averageQualityScore;
    }

    public void setAverageQualityScore(Double averageQualityScore) {
        this.averageQualityScore = averageQualityScore;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    @Override
    public String toString() {
        return "FaceTaggingResult{" +
                "success=" + success +
                ", httpStatus=" + httpStatus +
                ", message='" + message + '\'' +
                ", faceCount=" + faceCount +
                ", matchCount=" + matchCount +
                ", averageQualityScore=" + averageQualityScore +
                ", rawResponse='" + rawResponse + '\'' +
                '}';
    }
}
