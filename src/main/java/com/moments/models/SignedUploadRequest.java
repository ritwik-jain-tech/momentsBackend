package com.moments.models;

import java.util.List;

/**
 * Request for direct-to-GCS upload URLs. The browser PUTs each file straight to Cloud Storage
 * using the returned signed URL, bypassing the backend (and Cloud Run's ~32 MiB request cap).
 */
public class SignedUploadRequest {

    private String eventId;
    private List<Item> files;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public List<Item> getFiles() {
        return files;
    }

    public void setFiles(List<Item> files) {
        this.files = files;
    }

    public static class Item {
        private String filename;
        /** Browser-reported MIME (may be blank/omitted for Canon CR3). */
        private String contentType;
        /** IMAGE (default) or VIDEO. */
        private FileType fileType;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public FileType getFileType() {
            return fileType;
        }

        public void setFileType(FileType fileType) {
            this.fileType = fileType;
        }
    }
}
