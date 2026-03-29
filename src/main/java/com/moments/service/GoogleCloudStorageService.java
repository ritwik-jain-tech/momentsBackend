package com.moments.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
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
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class GoogleCloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudStorageService.class);

    /** Chunk size for GCS resumable uploads (avoids "Error writing request body" on large JPEGs). */
    private static final int GCS_WRITE_CHUNK_BYTES = 256 * 1024;

    @Autowired
    private Storage storage;

    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");
    private final String cdnDomain = System.getProperty("gcp.cdn.domain", "images.moments.live");

    /**
     * Stable object name for Google Drive import: same Drive file id + event always maps to the same GCS key
     * so retries skip re-upload and {@link MomentService} can use a matching deterministic Firestore id.
     */
    public String driveImportObjectName(String eventId, String driveFileId) {
        String e = sanitizeGcsPathSegment(eventId);
        String f = sanitizeGcsPathSegment(driveFileId);
        return "drive-import/" + e + "/" + f;
    }

    public boolean blobExists(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return false;
        }
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        return blob != null && blob.exists();
    }

    /**
     * Reads up to {@code maxPrefixBytes} from the start of an image object for dimension probing.
     *
     * @return {@code null} if the object is missing
     */
    public ExistingImageBlobHead readImageBlobHead(String objectName, int maxPrefixBytes) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null || !blob.exists()) {
            return null;
        }
        long size = blob.getSize() != null ? blob.getSize() : 0L;
        String contentType = blob.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        int cap = (size > 0L && maxPrefixBytes > 0) ? (int) Math.min(size, (long) maxPrefixBytes) : 0;
        byte[] prefix = cap > 0 ? new byte[cap] : new byte[0];
        int prefixLen = 0;
        if (cap > 0) {
            try (ReadChannel reader = blob.reader()) {
                ByteBuffer buffer = ByteBuffer.wrap(prefix);
                while (buffer.hasRemaining()) {
                    int n = reader.read(buffer);
                    if (n < 0) {
                        break;
                    }
                }
                prefixLen = buffer.position();
            }
        }
        return new ExistingImageBlobHead(objectName, contentType, publicURL, size, prefix, prefixLen);
    }

    /**
     * Stream upload to a fixed object name (Drive import idempotency). Caller must ensure the object should be
     * created (e.g. {@link #blobExists(String)} is false).
     */
    public FileUploadResponse uploadStreamToObjectName(InputStream in, String objectName, FileType fileType,
            String contentType) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("objectName is required");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = fileType == FileType.IMAGE ? "image/jpeg" : "video/mp4";
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(contentType).build();
        long written = streamToResumableChannel(blobInfo, in);
        Blob blob = storage.get(blobInfo.getBlobId());
        if (blob == null || !blob.exists()) {
            throw new IOException("GCS object missing after upload: " + objectName);
        }
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        return new FileUploadResponse(objectName, contentType, publicURL, written);
    }

    private static String sanitizeGcsPathSegment(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "unknown";
        }
        return t.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static final class ExistingImageBlobHead {
        private final String objectName;
        private final String contentType;
        private final String publicUrl;
        private final long sizeBytes;
        private final byte[] prefix;
        private final int prefixLength;

        public ExistingImageBlobHead(String objectName, String contentType, String publicUrl, long sizeBytes,
                byte[] prefix, int prefixLength) {
            this.objectName = objectName;
            this.contentType = contentType;
            this.publicUrl = publicUrl;
            this.sizeBytes = sizeBytes;
            this.prefix = prefix != null ? prefix : new byte[0];
            this.prefixLength = prefixLength;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getContentType() {
            return contentType;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public byte[] getPrefix() {
            return prefix;
        }

        public int getPrefixLength() {
            return prefixLength;
        }
    }

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
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();

        Blob blob = uploadWithResumableChannel(blobInfo, fileBytes);

        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());

        return new FileUploadResponse(blobName, contentType, publicURL, fileBytes.length);
    }

    public FileUploadResponse uploadBytes(byte[] fileBytes, String originalFilename, FileType fileType, String contentType) throws IOException {
        String safeName = originalFilename != null ? originalFilename : "image";
        String blobName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_") + Math.random();
        if (contentType == null || contentType.isBlank()) {
            contentType = fileType == FileType.IMAGE ? "image/jpeg" : "video/mp4";
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();
        Blob blob = uploadWithResumableChannel(blobInfo, fileBytes);
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        return new FileUploadResponse(blobName, contentType, publicURL, fileBytes.length);
    }

    /**
     * Streams bytes to GCS (resumable upload, fixed read buffer) so callers are not limited by heap size.
     * Used for Google Drive import of large originals.
     */
    public FileUploadResponse uploadStream(InputStream in, String originalFilename, FileType fileType, String contentType) throws IOException {
        String safeName = originalFilename != null ? originalFilename : "image";
        String blobName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_") + Math.random();
        if (contentType == null || contentType.isBlank()) {
            contentType = fileType == FileType.IMAGE ? "image/jpeg" : "video/mp4";
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();
        long written = streamToResumableChannel(blobInfo, in);
        Blob blob = storage.get(blobInfo.getBlobId());
        if (blob == null || !blob.exists()) {
            throw new IOException("GCS object missing after upload: " + blobInfo.getName());
        }
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        return new FileUploadResponse(blobName, contentType, publicURL, written);
    }

    /**
     * Uses a {@link WriteChannel} (resumable upload) instead of {@link Storage#create(BlobInfo, byte[])} so large
     * images from Drive import do not fail with client errors such as "Error writing request body to server".
     */
    private Blob uploadWithResumableChannel(BlobInfo blobInfo, byte[] fileBytes) throws IOException {
        try (WriteChannel writer = storage.writer(blobInfo)) {
            ByteBuffer buf = ByteBuffer.wrap(fileBytes);
            while (buf.hasRemaining()) {
                int slice = Math.min(buf.remaining(), GCS_WRITE_CHUNK_BYTES);
                int limit = buf.limit();
                buf.limit(buf.position() + slice);
                writer.write(buf);
                buf.limit(limit);
            }
        }
        Blob blob = storage.get(blobInfo.getBlobId());
        if (blob == null || !blob.exists()) {
            throw new IOException("GCS object missing after upload: " + blobInfo.getName());
        }
        return blob;
    }

    /** @return total bytes written */
    private long streamToResumableChannel(BlobInfo blobInfo, InputStream in) throws IOException {
        byte[] chunk = new byte[GCS_WRITE_CHUNK_BYTES];
        long total = 0;
        try (WriteChannel writer = storage.writer(blobInfo)) {
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                ByteBuffer bb = ByteBuffer.wrap(chunk, 0, n);
                while (bb.hasRemaining()) {
                    writer.write(bb);
                }
            }
        }
        return total;
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

