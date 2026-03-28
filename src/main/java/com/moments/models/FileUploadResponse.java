package com.moments.models;

public class FileUploadResponse {
    private String fileName;
    private String fileType;
    private String publicUrl;
    /** Byte length of the uploaded object (same as stored in GCS). */
    private Long sizeBytes;

    public FileUploadResponse(String fileName, String fileType, String publicUrl) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.publicUrl = publicUrl;
    }

    public FileUploadResponse(String fileName, String fileType, String publicUrl, long sizeBytes) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.publicUrl = publicUrl;
        this.sizeBytes = sizeBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
