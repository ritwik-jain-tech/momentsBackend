package com.moments.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class GoogleCloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudStorageService.class);

    @Autowired
    private Storage storage;

    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");
    private final String cdnDomain = System.getProperty("gcp.cdn.domain", "images.moments.live");

    public FileUploadResponse uploadFile(MultipartFile file, FileType fileType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String blobName =  originalFilename+Math.random() ;
        
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

        return new FileUploadResponse(blobName, contentType, publicURL, fileBytes.length);
    }

    public FileUploadResponse uploadBytes(byte[] fileBytes, String originalFilename, FileType fileType, String contentType) {
        String safeName = originalFilename != null ? originalFilename : "image";
        String blobName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_") + Math.random();
        if (contentType == null || contentType.isBlank()) {
            contentType = fileType == FileType.IMAGE ? "image/jpeg" : "video/mp4";
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();
        Blob blob = storage.create(blobInfo, fileBytes);
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        return new FileUploadResponse(blobName, contentType, publicURL, fileBytes.length);
    }

    /**
     * Deletes all CDN objects referenced by the moment's media (original, feed, thumbnail).
     * Ignores null URLs and URLs that are not served from this app's bucket/CDN.
     */
    public void deleteMediaObjects(Media media) {
        if (media == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        String n1 = objectNameFromPublicUrl(media.getUrl());
        String n2 = objectNameFromPublicUrl(media.getFeedUrl());
        String n3 = objectNameFromPublicUrl(media.getThumbnailUrl());
        if (n1 != null) {
            names.add(n1);
        }
        if (n2 != null) {
            names.add(n2);
        }
        if (n3 != null) {
            names.add(n3);
        }
        for (String objectName : names) {
            try {
                boolean deleted = storage.delete(BlobId.of(bucketName, objectName));
                if (!deleted) {
                    logger.warn("GCS delete: object not found bucket={} name={}", bucketName, objectName);
                }
            } catch (Exception e) {
                logger.warn("GCS delete failed for {}: {}", objectName, e.getMessage());
            }
        }
    }

    /**
     * Resolves object name in {@link #bucketName} from a public HTTPS URL, if it targets this CDN host.
     */
    public String objectNameFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(publicUrl.trim());
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase(cdnDomain)) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return null;
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.debug("Could not parse URL for GCS object: {}", publicUrl);
            return null;
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getCdnDomain() {
        return cdnDomain;
    }
}

