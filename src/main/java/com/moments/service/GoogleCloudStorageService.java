package com.moments.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.api.gax.paging.Page;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.Media;
import com.moments.models.SignedUploadTarget;

@Service
public class GoogleCloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudStorageService.class);

    /**
     * Chunk size for GCS resumable uploads (avoids "Error writing request body" on
     * large JPEGs).
     */
    private static final int GCS_WRITE_CHUNK_BYTES = 256 * 1024;

    public static boolean isCanonCr3Filename(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).endsWith(".cr3");
    }

    /**
     * Browsers often omit or misreport MIME for Canon RAW; avoid storing CR3 as
     * {@code image/jpeg}.
     */
    public static String effectiveImageContentType(String originalFilename, String reportedContentType) {
        if (isCanonCr3Filename(originalFilename)) {
            if (reportedContentType == null || reportedContentType.isBlank()) {
                return "application/octet-stream";
            }
            if ("image/jpeg".equalsIgnoreCase(reportedContentType)) {
                return "application/octet-stream";
            }
            return reportedContentType;
        }
        if (reportedContentType == null || reportedContentType.isBlank()) {
            return "image/jpeg";
        }
        return reportedContentType;
    }

    public static String effectiveVideoContentType(String reportedContentType) {
        if (reportedContentType == null || reportedContentType.isBlank()) {
            return "video/mp4";
        }
        return reportedContentType;
    }

    @Autowired
    private Storage storage;

    /**
     * Same credentials the {@link Storage} bean is built from. Needed to sign upload URLs:
     * a key-file credential ({@link ServiceAccountSigner}) signs locally; Cloud Run's default
     * {@link ComputeEngineCredentials} cannot, so we impersonate the runtime SA (IAM signBlob).
     */
    @Autowired
    private GoogleCredentials googleCredentials;

    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");
    private final String cdnDomain = System.getProperty("gcp.cdn.domain", "images.moments.live");

    /** Signed upload URL lifetime. */
    private static final long SIGNED_URL_TTL_SECONDS = 15 * 60;

    /** Lazily-built signer (see {@link #signer()}); {@code null} until first use. */
    private volatile ServiceAccountSigner cachedSigner;

    /**
     * Stable object name for Google Drive import: same Drive file id + event always
     * maps to the same GCS key
     * so retries skip re-upload and {@link MomentService} can use a matching
     * deterministic Firestore id.
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
     * Reads up to {@code maxPrefixBytes} from the start of an image object for
     * dimension probing.
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
            String base = blob.getName();
            int slash = base != null ? base.lastIndexOf('/') : -1;
            String leaf = (base != null && slash >= 0 && slash < base.length() - 1)
                    ? base.substring(slash + 1)
                    : base;
            contentType = effectiveImageContentType(leaf, null);
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
     * Stream upload to a fixed object name (Drive import idempotency). Caller must
     * ensure the object should be
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

    /**
     * Object key from the client filename only (no random suffix): same name → same
     * GCS object (overwrite on
     * duplicate upload). Uses the last path segment, then
     * {@link #sanitizeGcsPathSegment}.
     */
    public static String gcsObjectNameFromOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }
        String base = originalFilename.replace('\\', '/').trim();
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash < base.length() - 1) {
            base = base.substring(slash + 1).trim();
        }
        if (base.isEmpty()) {
            return "upload";
        }
        String sanitized = sanitizeGcsPathSegment(base);
        if (sanitized.isEmpty() || "unknown".equals(sanitized)) {
            return "upload";
        }
        final int maxLen = 850;
        if (sanitized.length() <= maxLen) {
            return sanitized;
        }
        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot > 0 && sanitized.length() - lastDot <= 20) {
            String ext = sanitized.substring(lastDot);
            int stemChars = maxLen - ext.length();
            if (stemChars < 1) {
                return sanitized.substring(0, maxLen);
            }
            if (stemChars >= lastDot) {
                return sanitized.substring(0, lastDot) + ext;
            }
            return sanitized.substring(0, stemChars) + ext;
        }
        return sanitized.substring(0, maxLen);
    }

    /**
     * Per-event object prefix:
     * {@code events/{eventId}/{sanitizedOriginalFilename}}.
     * When {@code eventId} is null/blank, uses {@code uploads/unscoped/} (e.g.
     * cover upload before an event id exists).
     */
    public static String gcsObjectKey(String eventId, String originalFilename) {
        String basename = gcsObjectNameFromOriginalFilename(originalFilename);
        if (eventId == null || eventId.isBlank()) {
            return "uploads/unscoped/" + basename;
        }
        return "events/" + sanitizeGcsPathSegment(eventId) + "/" + basename;
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
        return uploadFile(file, fileType, null);
    }

    public FileUploadResponse uploadFile(MultipartFile file, FileType fileType, String eventId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String blobName = gcsObjectKey(eventId, originalFilename);

        String contentType = fileType == FileType.IMAGE
                ? effectiveImageContentType(originalFilename, file.getContentType())
                : effectiveVideoContentType(file.getContentType());
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();

        long written;
        try (InputStream in = file.getInputStream()) {
            written = streamToResumableChannel(blobInfo, in);
        }
        Blob blob = storage.get(blobInfo.getBlobId());
        if (blob == null || !blob.exists()) {
            throw new IOException("GCS object missing after upload: " + blobName);
        }

        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());

        return new FileUploadResponse(blobName, contentType, publicURL, written);
    }

    public FileUploadResponse uploadBytes(byte[] fileBytes, String originalFilename, FileType fileType,
            String contentType) throws IOException {
        return uploadBytes(fileBytes, originalFilename, fileType, contentType, null);
    }

    public FileUploadResponse uploadBytes(byte[] fileBytes, String originalFilename, FileType fileType,
            String contentType,
            String eventId) throws IOException {
        String safeName = originalFilename != null ? originalFilename : "image";
        String blobName = gcsObjectKey(eventId, safeName);
        if (fileType == FileType.IMAGE) {
            contentType = effectiveImageContentType(safeName, contentType);
        } else {
            contentType = effectiveVideoContentType(contentType);
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();
        Blob blob = uploadWithResumableChannel(blobInfo, fileBytes);
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());
        return new FileUploadResponse(blobName, contentType, publicURL, fileBytes.length);
    }

    /**
     * Streams bytes to GCS (resumable upload, fixed read buffer) so callers are not
     * limited by heap size.
     * Used for Google Drive import of large originals.
     */
    public FileUploadResponse uploadStream(InputStream in, String originalFilename, FileType fileType,
            String contentType) throws IOException {
        return uploadStream(in, originalFilename, fileType, contentType, null);
    }

    public FileUploadResponse uploadStream(InputStream in, String originalFilename, FileType fileType,
            String contentType, String eventId) throws IOException {
        String safeName = originalFilename != null ? originalFilename : "image";
        String blobName = gcsObjectKey(eventId, safeName);
        if (fileType == FileType.IMAGE) {
            contentType = effectiveImageContentType(safeName, contentType);
        } else {
            contentType = effectiveVideoContentType(contentType);
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
     * Uses a {@link WriteChannel} (resumable upload) instead of
     * {@link Storage#create(BlobInfo, byte[])} so large
     * images from Drive import do not fail with client errors such as "Error
     * writing request body to server".
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
     * Deletes all CDN objects referenced by the moment's media (original, feed,
     * thumbnail).
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
     * Resolves object name in {@link #bucketName} from a public HTTPS URL, if it
     * targets this CDN host.
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

    /**
     * Creates a V4-signed HTTP PUT URL so the browser can upload {@code originalFilename} directly
     * to GCS (bypassing the Cloud Run request-size cap). The object key matches
     * {@link #gcsObjectKey(String, String)} so the moment's deterministic id and idempotency behave
     * exactly like the multipart path.
     *
     * <p>Content-Type is not bound into the signature, so the browser may PUT with any (or no)
     * Content-Type without a {@code SignatureDoesNotMatch}. The returned {@code contentType} is the
     * value the client should send for correct rendering.</p>
     */
    public SignedUploadTarget createUploadUrl(String eventId, String originalFilename, FileType fileType,
            String reportedContentType) {
        String objectName = gcsObjectKey(eventId, originalFilename);
        String contentType = fileType == FileType.VIDEO
                ? effectiveVideoContentType(reportedContentType)
                : effectiveImageContentType(originalFilename, reportedContentType);

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName))
                .setContentType(contentType).build();

        URL signed = storage.signUrl(
                blobInfo,
                SIGNED_URL_TTL_SECONDS, TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.signWith(signer()));

        String publicURL = String.format("https://%s/%s", cdnDomain, objectName);
        return new SignedUploadTarget(originalFilename, objectName, signed.toString(), publicURL,
                contentType, SIGNED_URL_TTL_SECONDS);
    }

    /**
     * Signer for {@link #createUploadUrl}. Built once and cached.
     * <ul>
     *   <li>Local/dev: {@link #googleCredentials} comes from the service-account key file and is
     *       itself a {@link ServiceAccountSigner} (signs locally, no extra permissions).</li>
     *   <li>Cloud Run: default credentials cannot sign, so we impersonate the runtime service
     *       account via the IAM Credentials {@code signBlob} API. The runtime SA needs
     *       {@code roles/iam.serviceAccountTokenCreator} on itself and the IAM Credentials API
     *       enabled.</li>
     * </ul>
     */
    private ServiceAccountSigner signer() {
        ServiceAccountSigner s = cachedSigner;
        if (s != null) {
            return s;
        }
        synchronized (this) {
            if (cachedSigner != null) {
                return cachedSigner;
            }
            if (googleCredentials instanceof ServiceAccountSigner) {
                cachedSigner = (ServiceAccountSigner) googleCredentials;
            } else {
                String email = signingServiceAccountEmail();
                if (email == null || email.isBlank()) {
                    throw new IllegalStateException(
                            "Cannot sign upload URLs: no service-account signer available and the runtime "
                                    + "service-account email could not be resolved. Set GCS_SIGNING_SA_EMAIL.");
                }
                cachedSigner = ImpersonatedCredentials.create(
                        googleCredentials,
                        email,
                        null,
                        Arrays.asList("https://www.googleapis.com/auth/devstorage.read_write"),
                        3600);
                logger.info("Signed-URL signer: impersonating runtime service account {}", email);
            }
            return cachedSigner;
        }
    }

    /** Runtime SA email: explicit env override first, else the Compute/metadata identity. */
    private String signingServiceAccountEmail() {
        String env = System.getenv("GCS_SIGNING_SA_EMAIL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        try {
            if (googleCredentials instanceof ComputeEngineCredentials) {
                return ((ComputeEngineCredentials) googleCredentials).getAccount();
            }
        } catch (Exception e) {
            logger.warn("Could not resolve service-account email from ComputeEngineCredentials: {}", e.getMessage());
        }
        return null;
    }

    /**
     * All stored objects that belong to an event, across both prefixes an event's files can live
     * under: direct uploads ({@code events/{eventId}/}) and Google Drive imports
     * ({@code drive-import/{eventId}/}). Folder placeholders (keys ending in {@code /}) are skipped.
     */
    public List<StoredObject> listEventObjects(String eventId) {
        List<StoredObject> out = new ArrayList<>();
        for (String prefix : eventPrefixes(eventId)) {
            Page<Blob> page = storage.list(bucketName, Storage.BlobListOption.prefix(prefix));
            for (Blob b : page.iterateAll()) {
                String name = b.getName();
                if (name == null || name.endsWith("/")) {
                    continue;
                }
                long size = b.getSize() != null ? b.getSize() : 0L;
                Long updated = b.getUpdateTime();
                String publicURL = String.format("https://%s/%s", cdnDomain, name);
                out.add(new StoredObject(name, size, publicURL, updated));
            }
        }
        return out;
    }

    /**
     * Streams every object belonging to {@code eventId} into a ZIP written to {@code rawOut}.
     * Each object is copied through a bounded buffer (never fully buffered in memory), so the
     * response size is limited only by request time, not heap. Entry names are the object path with
     * the event prefix stripped; Drive imports are nested under {@code drive-import/}.
     */
    public void streamEventZip(String eventId, OutputStream rawOut) throws IOException {
        List<StoredObject> objects = listEventObjects(eventId);
        byte[] buf = new byte[GCS_WRITE_CHUNK_BYTES];
        try (ZipOutputStream zip = new ZipOutputStream(rawOut)) {
            for (StoredObject o : objects) {
                Blob blob = storage.get(BlobId.of(bucketName, o.getName()));
                if (blob == null || !blob.exists()) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(zipEntryName(eventId, o.getName())));
                try (ReadChannel reader = blob.reader();
                        InputStream in = Channels.newInputStream(reader)) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        zip.write(buf, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String[] eventPrefixes(String eventId) {
        String e = sanitizeGcsPathSegment(eventId);
        return new String[] { "events/" + e + "/", "drive-import/" + e + "/" };
    }

    /** Object key -> ZIP entry name: strip {@code events/{e}/}; keep Drive imports under a subfolder. */
    private String zipEntryName(String eventId, String objectName) {
        String e = sanitizeGcsPathSegment(eventId);
        String uploads = "events/" + e + "/";
        String imports = "drive-import/" + e + "/";
        if (objectName.startsWith(uploads)) {
            return objectName.substring(uploads.length());
        }
        if (objectName.startsWith(imports)) {
            return "drive-import/" + objectName.substring(imports.length());
        }
        return objectName;
    }

    /** A stored object exposed by the event export/listing endpoints. */
    public static final class StoredObject {
        private final String name;
        private final long sizeBytes;
        private final String publicUrl;
        private final Long updatedMillis;

        public StoredObject(String name, long sizeBytes, String publicUrl, Long updatedMillis) {
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.publicUrl = publicUrl;
            this.updatedMillis = updatedMillis;
        }

        public String getName() {
            return name;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public Long getUpdatedMillis() {
            return updatedMillis;
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getCdnDomain() {
        return cdnDomain;
    }
}
