package com.moments.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
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
        String originalFilename = file.getOriginalFilename();
        String blobName =  originalFilename;
        
        // Upload file as-is without compression
        byte[] fileBytes = file.getBytes();
        String contentType = file.getContentType();
        
        // Set default content type if not provided
        if (contentType == null) {
            if (fileType == FileType.IMAGE) {
                contentType = "image/jpeg";
            } else {
                contentType = "video/mp4";
            }
        }
        // Define the blob (object) metadata
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();

        // Upload the file to the bucket
        Blob blob = storage.create(blobInfo, fileBytes);

        // Return the CDN URL of the uploaded file with HTTPS
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());

        return new FileUploadResponse(blobName, contentType, publicURL);
    }
}

