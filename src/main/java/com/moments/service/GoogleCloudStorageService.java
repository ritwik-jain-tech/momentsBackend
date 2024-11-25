package com.moments.service;


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

        // Upload the file to GCS
        BlobInfo blobInfo = storage.create(
                BlobInfo.newBuilder(bucketName, blobName).build(),
                file.getBytes(),
                Storage.BlobTargetOption.predefinedAcl(PredefinedAcl.PUBLIC_READ)
        );

        // Return the public URL of the uploaded file
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, blobName);
    }
}

