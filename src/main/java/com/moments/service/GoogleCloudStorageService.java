package com.moments.service;


import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Storage.PredefinedAcl;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class GoogleCloudStorageService {
    @Autowired
    private Storage storage;

    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");
    private final String cdnDomain = "images.moments.live";

    public FileUploadResponse uploadFile(MultipartFile file, FileType fileType) throws IOException {
        // Create a unique filename for the uploaded file
        String blobName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // Determine content type based on file type
        String contentType;
        switch (fileType) {
            case IMAGE:
                contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
                break;
            case VIDEO:
                contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type");
        }
        // Define the blob (object) metadata
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();

        // Upload the file to the bucket
        Blob blob = storage.create(blobInfo, file.getBytes());

        // Return the CDN URL of the uploaded file
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());

        return new FileUploadResponse(blobName, contentType, publicURL);
    }
}

