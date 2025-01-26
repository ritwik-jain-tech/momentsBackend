package com.moments.models;

public class FileUploadResponse {
    private String fileName;
    private String fileType;
    private String publicUrl;

    public FileUploadResponse(String fileName, String fileType, String publicUrl) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.publicUrl = publicUrl;
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
}
