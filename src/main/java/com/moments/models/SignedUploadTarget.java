package com.moments.models;

/**
 * One direct-to-GCS upload target: the browser sends the file bytes via HTTP PUT to
 * {@link #uploadUrl}, then references {@link #publicUrl} when finalizing the moment.
 */
public class SignedUploadTarget {

    private String filename;
    private String objectName;
    private String uploadUrl;
    private String publicUrl;
    /** Content-Type the object will be stored as; the client should send this on the PUT. */
    private String contentType;
    private String method = "PUT";
    private long expiresInSeconds;

    public SignedUploadTarget() {
    }

    public SignedUploadTarget(String filename, String objectName, String uploadUrl, String publicUrl,
            String contentType, long expiresInSeconds) {
        this.filename = filename;
        this.objectName = objectName;
        this.uploadUrl = uploadUrl;
        this.publicUrl = publicUrl;
        this.contentType = contentType;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
