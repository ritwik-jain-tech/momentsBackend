package com.moments.service;


import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Storage.PredefinedAcl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class GoogleCloudStorageService {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();
    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");

    public String uploadFile(MultipartFile file) throws IOException {
        // Create a unique filename for the uploaded file
        String blobName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // Define the blob (object) metadata
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName).build();

        // Upload the file to the bucket
        Blob blob = storage.create(blobInfo, file.getBytes());

        // Return the public URL of the uploaded file
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, blob.getName());

    }
}

