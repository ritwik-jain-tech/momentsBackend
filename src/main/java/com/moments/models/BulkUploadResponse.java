package com.moments.models;

import java.util.List;

public class BulkUploadResponse {
    private int totalFiles;
    private int successfulUploads;
    private int failedUploads;
    private List<FileUploadResponse> successfulFiles;
    private List<FileUploadError> failedFiles;

    public BulkUploadResponse(int totalFiles, int successfulUploads, int failedUploads,
                             List<FileUploadResponse> successfulFiles, List<FileUploadError> failedFiles) {
        this.totalFiles = totalFiles;
        this.successfulUploads = successfulUploads;
        this.failedUploads = failedUploads;
        this.successfulFiles = successfulFiles;
        this.failedFiles = failedFiles;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getSuccessfulUploads() {
        return successfulUploads;
    }

    public void setSuccessfulUploads(int successfulUploads) {
        this.successfulUploads = successfulUploads;
    }

    public int getFailedUploads() {
        return failedUploads;
    }

    public void setFailedUploads(int failedUploads) {
        this.failedUploads = failedUploads;
    }

    public List<FileUploadResponse> getSuccessfulFiles() {
        return successfulFiles;
    }

    public void setSuccessfulFiles(List<FileUploadResponse> successfulFiles) {
        this.successfulFiles = successfulFiles;
    }

    public List<FileUploadError> getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(List<FileUploadError> failedFiles) {
        this.failedFiles = failedFiles;
    }

    public static class FileUploadError {
        private String fileName;
        private String errorMessage;

        public FileUploadError(String fileName, String errorMessage) {
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}

