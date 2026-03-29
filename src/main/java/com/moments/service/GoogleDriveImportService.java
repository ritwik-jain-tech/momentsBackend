package com.moments.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.moments.models.CreatorDetails;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.GoogleDriveImportRequest;
import com.moments.models.GoogleDriveImportResponse;
import com.moments.models.Media;
import com.moments.models.MediaType;
import com.moments.models.Moment;
import com.moments.models.MomentMemoryUsage;

import com.moments.config.DriveImportProperties;

import jakarta.annotation.PostConstruct;

@Service
public class GoogleDriveImportService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveImportService.class);
    private static final String PHOTOGRAPHER_ROLE = "Photographer";
    private static final int MAX_FILES = 5000;
    private static final int MAX_FOLDER_DEPTH = 12;
    private static final int PROCESS_BATCH = 25;
    /** Bytes of file start kept for width/height probe; avoids loading the whole file into heap. */
    private static final int DRIVE_IMPORT_PREFIX_PROBE_BYTES = 512 * 1024;

    private static final Pattern FOLDER_IN_PATH = Pattern.compile("/folders/([a-zA-Z0-9_-]+)");
    private static final Pattern FILE_IN_PATH = Pattern.compile("/file/d/([a-zA-Z0-9_-]+)");
    private static final Pattern ID_PARAM = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)");

    @Value("${google.drive.api.key:}")
    private String driveApiKey;

    @Value("${google.drive.credentials.path:}")
    private String credentialsPath;

    /**
     * When true (e.g. {@code local} profile), use the service account for Drive
     * even if
     * {@code GOOGLE_DRIVE_API_KEY} is set. Avoids invalid key errors from
     * HTTP-referrer–restricted keys
     * (server-side Java has no referrer) and from stray quotes in env.
     */
    @Value("${google.drive.prefer-service-account:false}")
    private boolean preferServiceAccount;

    @Autowired
    private GoogleCloudStorageService storageService;

    @Autowired
    private MomentService momentService;

    @Autowired
    private DriveImportProperties driveImportProperties;

    @Autowired
    private UploadRecordService uploadRecordService;

    /** Same credentials as Firestore/Firebase (ADC on Cloud Run, classpath SA in local DEV). */
    @Autowired
    private GoogleCredentials googleCredentials;

    private String normalizedDriveApiKey() {
        if (driveApiKey == null) {
            return "";
        }
        String s = driveApiKey.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }
        return s.replace("\"", "").replace("'", "").replace("\u201c", "").replace("\u201d", "").trim();
    }

    private String normalizedCredentialsPath() {
        return credentialsPath != null ? credentialsPath.trim().replace("\"", "") : "";
    }

    private boolean hasReadableServiceAccountCredentials() {
        String path = normalizedCredentialsPath();
        return !path.isEmpty() && Files.isReadable(Paths.get(path));
    }

    @PostConstruct
    void logDriveImportMode() {
        boolean sa = hasReadableServiceAccountCredentials();
        String key = normalizedDriveApiKey();
        if (preferServiceAccount && sa) {
            logger.info(
                    "Google Drive import: using service account (google.drive.prefer-service-account=true); ignoring API key for Drive.");
        } else if (!key.isEmpty()) {
            logger.info(
                    "Google Drive import: using API key. If you see API_KEY_INVALID, unset GOOGLE_DRIVE_API_KEY and use prefer-service-account + credentials path, or use a key with application restrictions = None or IP (not HTTP referrers only).");
        } else if (sa) {
            logger.info("Google Drive import: using service account from google.drive.credentials.path.");
        } else {
            logger.info(
                    "Google Drive import: will use Application Default Credentials for Drive (same identity as Firestore). "
                            + "Enable Google Drive API and grant this service account access to shared folders.");
        }
    }

    public boolean isConfigured() {
        if (preferServiceAccount && hasReadableServiceAccountCredentials()) {
            return true;
        }
        if (!normalizedDriveApiKey().isEmpty()) {
            return true;
        }
        if (hasReadableServiceAccountCredentials()) {
            return true;
        }
        // Production Cloud Run: no env file path needed — use the runtime service account (same as other GCP APIs).
        return googleCredentials != null;
    }

    public boolean isDriveLinkAccessible(String folderOrFileUrl) throws IOException {
        if (folderOrFileUrl == null || folderOrFileUrl.isBlank()) {
            throw new IllegalArgumentException("Google Drive link is required.");
        }
        if (!isConfigured()) {
            throw new IllegalArgumentException(
                    "Google Drive import is not configured. Set GOOGLE_DRIVE_API_KEY, GOOGLE_DRIVE_CREDENTIALS_PATH, "
                            + "or run with a GCP service account (Application Default Credentials).");
        }

        DriveAccess access = createDriveAccess();
        List<File> images = new ArrayList<>();
        try {
            resolveImagesFromPublicOrSharedLink(access, folderOrFileUrl.trim(), images);
            return !images.isEmpty();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (GoogleJsonResponseException e) {
            // For private or inaccessible links, we intentionally return false so
            // controller can map to 403.
            int code = e.getStatusCode();
            if (code == 401 || code == 403 || code == 404) {
                return false;
            }
            throw e;
        } catch (IOException e) {
            // Treat metadata/list/download access failures as non-public/inaccessible link.
            return false;
        }
    }

    @Async("taskExecutor")
    public void importFolderAsync(GoogleDriveImportRequest request) {
        try {
            GoogleDriveImportResponse result = importFolder(request);
            logger.info("Drive import finished for event {}: created={}, failed={}, found={}",
                    request != null ? request.getEventId() : null,
                    result.getMomentsCreated(),
                    result.getFailed(),
                    result.getImageFilesFound());
            if (!result.getErrors().isEmpty()) {
                logger.warn("Drive import completed with {} error(s): {}", result.getErrors().size(),
                        result.getErrors());
            }
        } catch (Exception e) {
            logger.error("Drive import async job failed for event {}: {}",
                    request != null ? request.getEventId() : null, e.getMessage(), e);
            markUploadRecordFailed(uploadRecordId(request), e.getMessage());
        }
    }

    private static String uploadRecordId(GoogleDriveImportRequest request) {
        if (request == null || request.getUploadRecordId() == null) {
            return "";
        }
        return request.getUploadRecordId().trim();
    }

    private void markUploadRecordFailed(String recordId, String message) {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        try {
            uploadRecordService.markDriveImportFailed(recordId, message);
        } catch (Exception e) {
            logger.warn("Could not mark UploadRecord failed: {}", e.getMessage());
        }
    }

    public GoogleDriveImportResponse importFolder(GoogleDriveImportRequest request)
            throws IOException, ExecutionException, InterruptedException {
        GoogleDriveImportResponse response = new GoogleDriveImportResponse();
        String rid = uploadRecordId(request);
        if (!rid.isEmpty()) {
            response.setUploadRecordId(rid);
        }

        if (!isConfigured()) {
            String msg = "Google Drive import is not configured. Set GOOGLE_DRIVE_API_KEY (public link folders), "
                    + "GOOGLE_DRIVE_CREDENTIALS_PATH (SA JSON), or deploy with a GCP service account that has Drive API access.";
            response.getErrors().add(msg);
            markUploadRecordFailed(rid, msg);
            return response;
        }

        String creatorId = request.getCreatorId() != null ? request.getCreatorId().trim() : "";
        if (creatorId.isEmpty()) {
            response.getErrors().add("creatorId is required.");
            markUploadRecordFailed(rid, "creatorId is required.");
            return response;
        }
        String eventId = request.getEventId() != null ? request.getEventId().trim() : "";
        if (eventId.isEmpty()) {
            response.getErrors().add("eventId is required.");
            markUploadRecordFailed(rid, "eventId is required.");
            return response;
        }

        DriveAccess access;
        try {
            access = createDriveAccess();
        } catch (IOException e) {
            response.getErrors().add(e.getMessage());
            markUploadRecordFailed(rid, e.getMessage());
            throw e;
        }

        List<File> images = new ArrayList<>();
        String u = request.getFolderUrl() != null ? request.getFolderUrl().trim() : "";

        try {
            resolveImagesFromPublicOrSharedLink(access, u, images);
        } catch (IOException e) {
            logger.warn("Drive resolve failed: {}", e.getMessage());
            String em = e.getMessage()
                    + " Ensure the link is shared as \"Anyone with the link\" (Viewer) when using an API key.";
            response.getErrors().add(em);
            markUploadRecordFailed(rid, em);
            return response;
        }

        if (images.isEmpty()) {
            response.getErrors().add("No image files found for this link.");
            markUploadRecordFailed(rid, "No image files found for this link.");
            return response;
        }

        response.setImageFilesFound(images.size());

        if (images.size() > MAX_FILES) {
            String msg = "Found " + images.size() + " images; maximum per import is " + MAX_FILES + ".";
            response.getErrors().add(msg);
            markUploadRecordFailed(rid, msg);
            return response;
        }

        try {
            uploadRecordService.afterDriveListing(rid, images.size());
        } catch (Exception e) {
            logger.warn("UploadRecord afterDriveListing: {}", e.getMessage());
        }

        String userName = "Photographer";

        int created = 0;
        int failed = 0;
        int skipped = 0;

        try {
            for (int start = 0; start < images.size(); start += PROCESS_BATCH) {
                int end = Math.min(start + PROCESS_BATCH, images.size());
                List<Moment> batch = new ArrayList<>();
                for (int i = start; i < end; i++) {
                    File driveFile = images.get(i);
                    try {
                        DriveImportOutcome outcome = importDriveImageFile(access.drive, driveFile, eventId, creatorId,
                                userName, access.supportsAllDrives);
                        if (outcome.skippedDuplicate) {
                            skipped++;
                        } else if (outcome.moment != null) {
                            batch.add(outcome.moment);
                        }
                    } catch (Exception e) {
                        failed++;
                        String msg = (driveFile.getName() != null ? driveFile.getName() : driveFile.getId()) + ": "
                                + e.getMessage();
                        logger.error("Drive import file failed: {}", msg);
                        if (response.getErrors().size() < 25) {
                            response.getErrors().add(msg);
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    List<String> ids = momentService.saveMoments(batch, false,
                            driveImportProperties.isSynchronousFaceTaggingDuringDriveImport());
                    created += ids.size();
                }
                try {
                    uploadRecordService.updateDriveImportProgress(rid, created + skipped, failed);
                } catch (Exception e) {
                    logger.warn("UploadRecord progress: {}", e.getMessage());
                }
            }

            response.setMomentsCreated(created);
            response.setMomentsSkipped(skipped);
            response.setFailed(failed);
            try {
                uploadRecordService.markDriveImportDone(rid, created + skipped, failed);
            } catch (Exception e) {
                logger.warn("UploadRecord mark done: {}", e.getMessage());
            }
            return response;
        } catch (ExecutionException | InterruptedException e) {
            markUploadRecordFailed(rid, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            markUploadRecordFailed(rid, e.getMessage());
            throw e;
        }
    }

    private void resolveImagesFromPublicOrSharedLink(DriveAccess access, String url, List<File> images)
            throws IOException {
        String fileViewId = extractFileViewId(url);
        if (fileViewId != null) {
            appendIfImageFile(access.drive, fileViewId, access.supportsAllDrives, images);
            return;
        }
        String folderId = extractDriveFolderId(url);
        if (folderId != null) {
            collectImageFiles(access.drive, folderId, 0, images, access.supportsAllDrives);
            return;
        }
        String openId = extractAnyIdParameter(url);
        if (openId != null) {
            resolveIdAsFileOrFolder(access.drive, openId, access.supportsAllDrives, images);
            return;
        }
        throw new IllegalArgumentException("Could not parse a Google Drive folder or file link.");
    }

    private String extractFileViewId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Matcher m = FILE_IN_PATH.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String extractDriveFolderId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Matcher m = FOLDER_IN_PATH.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String extractAnyIdParameter(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Matcher m = ID_PARAM.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private DriveAccess createDriveAccess() throws IOException {
        NetHttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize Google HTTP transport", e);
        }

        if (preferServiceAccount && hasReadableServiceAccountCredentials()) {
            return buildDriveWithServiceAccount(transport, normalizedCredentialsPath());
        }

        String key = normalizedDriveApiKey();
        if (!key.isEmpty()) {
            Drive drive = new Drive.Builder(transport, GsonFactory.getDefaultInstance(), httpRequest -> {
            })
                    .setApplicationName("Moments Backend")
                    .setGoogleClientRequestInitializer(new CommonGoogleClientRequestInitializer(key))
                    .build();
            return new DriveAccess(drive, false);
        }

        String path = normalizedCredentialsPath();
        if (!path.isEmpty() && Files.isReadable(Paths.get(path))) {
            return buildDriveWithServiceAccount(transport, path);
        }
        return buildDriveWithApplicationCredentials(transport);
    }

    private DriveAccess buildDriveWithApplicationCredentials(NetHttpTransport transport) throws IOException {
        try {
            GoogleCredentials scoped = googleCredentials.createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));
            Drive drive = new Drive.Builder(transport, GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(scoped))
                    .setApplicationName("Moments Backend")
                    .build();
            return new DriveAccess(drive, true);
        } catch (RuntimeException e) {
            throw new IOException("Failed to build Drive client with application credentials: " + e.getMessage(), e);
        }
    }

    private DriveAccess buildDriveWithServiceAccount(NetHttpTransport transport, String path) throws IOException {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(Files.newInputStream(Paths.get(path)))
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));
            Drive drive = new Drive.Builder(transport, GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Moments Backend")
                    .build();
            return new DriveAccess(drive, true);
        } catch (IOException e) {
            throw new IOException("Failed to load service account credentials: " + e.getMessage(), e);
        }
    }

    private File fetchMetadata(Drive drive, String id, boolean supportsAllDrives) throws IOException {
        Drive.Files.Get get = drive.files().get(id)
                .setFields("id, name, mimeType, modifiedTime, shortcutDetails, imageMediaMetadata/time");
        if (supportsAllDrives) {
            get.setSupportsAllDrives(true);
        }
        return get.execute();
    }

    private void appendIfImageFile(Drive drive, String fileId, boolean supportsAllDrives, List<File> out)
            throws IOException {
        File meta = fetchMetadata(drive, fileId, supportsAllDrives);
        if (!addDriveFileAsImageIfSupported(meta, out)) {
            throw new IOException("Not an image file (mime: " + meta.getMimeType() + ").");
        }
    }

    private void resolveIdAsFileOrFolder(Drive drive, String id, boolean supportsAllDrives, List<File> out)
            throws IOException {
        File meta = fetchMetadata(drive, id, supportsAllDrives);
        if ("application/vnd.google-apps.folder".equals(meta.getMimeType())) {
            collectImageFiles(drive, id, 0, out, supportsAllDrives);
            return;
        }
        if (!addDriveFileAsImageIfSupported(meta, out)) {
            throw new IOException("Link is not a folder or image file.");
        }
    }

    private boolean addDriveFileAsImageIfSupported(File meta, List<File> out) {
        String mt = meta.getMimeType();
        if (mt != null && mt.startsWith("image/")) {
            out.add(meta);
            return true;
        }
        if ("application/vnd.google-apps.shortcut".equals(mt) && meta.getShortcutDetails() != null) {
            File.ShortcutDetails sd = meta.getShortcutDetails();
            String targetMime = sd.getTargetMimeType();
            if (targetMime != null && targetMime.startsWith("image/")) {
                File synthetic = new File();
                synthetic.setId(sd.getTargetId());
                synthetic.setName(meta.getName() != null ? meta.getName() : sd.getTargetId());
                synthetic.setMimeType(targetMime);
                synthetic.setModifiedTime(meta.getModifiedTime());
                out.add(synthetic);
                return true;
            }
        }
        return false;
    }

    private void collectImageFiles(Drive drive, String folderId, int depth, List<File> out, boolean supportsAllDrives)
            throws IOException {
        if (depth > MAX_FOLDER_DEPTH) {
            return;
        }
        String pageToken = null;
        do {
            Drive.Files.List req = drive.files().list()
                    .setQ("'" + folderId + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields(
                            "nextPageToken, files(id, name, mimeType, modifiedTime, shortcutDetails, imageMediaMetadata/time)")
                    .setPageToken(pageToken)
                    .setPageSize(100);
            if (supportsAllDrives) {
                req.setSupportsAllDrives(true).setIncludeItemsFromAllDrives(true);
            }
            FileList result = req.execute();

            List<File> files = result.getFiles();
            if (files != null) {
                for (File f : files) {
                    String mt = f.getMimeType();
                    if (mt == null) {
                        continue;
                    }
                    if ("application/vnd.google-apps.folder".equals(mt)) {
                        collectImageFiles(drive, f.getId(), depth + 1, out, supportsAllDrives);
                    } else if ("application/vnd.google-apps.shortcut".equals(mt) && f.getShortcutDetails() != null) {
                        File.ShortcutDetails sd = f.getShortcutDetails();
                        String targetMime = sd.getTargetMimeType();
                        if (targetMime != null && targetMime.startsWith("image/")) {
                            File synthetic = new File();
                            synthetic.setId(sd.getTargetId());
                            synthetic.setName(f.getName() != null ? f.getName() : sd.getTargetId());
                            synthetic.setMimeType(targetMime);
                            synthetic.setModifiedTime(f.getModifiedTime());
                            out.add(synthetic);
                        }
                    } else if (mt.startsWith("image/")) {
                        out.add(f);
                    }
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
    }

    /**
     * Stable Firestore document id for a Drive-sourced image so retries do not create duplicate moments.
     */
    private static String deterministicDriveMomentId(String eventId, String driveFileId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = eventId.trim() + "\0" + driveFileId.trim();
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("drv");
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private DriveImportOutcome importDriveImageFile(Drive drive, File driveFile, String eventId, String creatorId,
            String userName, boolean supportsAllDrives) throws IOException, ExecutionException, InterruptedException {
        String blobName = storageService.driveImportObjectName(eventId, driveFile.getId());
        String momentId = deterministicDriveMomentId(eventId, driveFile.getId());

        if (momentService.momentExists(momentId)) {
            logger.debug("Drive import skip: moment already exists id={} driveFile={}", momentId, driveFile.getId());
            return new DriveImportOutcome(null, true);
        }

        FileUploadResponse uploaded;
        int[] wh;

        if (storageService.blobExists(blobName)) {
            ExistingImageBlobHead head = storageService.readImageBlobHead(blobName, DRIVE_IMPORT_PREFIX_PROBE_BYTES);
            if (head == null || head.getSizeBytes() <= 0L) {
                throw new IOException("GCS object missing or empty: " + blobName);
            }
            uploaded = new FileUploadResponse(head.getObjectName(), head.getContentType(), head.getPublicUrl(),
                    head.getSizeBytes());
            wh = readImageDimensionsFromPrefix(head.getPrefix(), head.getPrefixLength());
        } else {
            Drive.Files.Get getReq = drive.files().get(driveFile.getId());
            if (supportsAllDrives) {
                getReq.setSupportsAllDrives(true);
            }
            HttpResponse mediaResp = getReq.executeMedia();
            InputStream raw = mediaResp.getContent();
            if (raw == null) {
                mediaResp.disconnect();
                throw new IOException("Drive returned no content");
            }
            try (InputStream in = raw) {
                PrefixCaptureInputStream capturing = new PrefixCaptureInputStream(in, DRIVE_IMPORT_PREFIX_PROBE_BYTES);
                String mime = driveFile.getMimeType() != null ? driveFile.getMimeType() : "image/jpeg";
                uploaded = storageService.uploadStreamToObjectName(capturing, blobName, FileType.IMAGE, mime);
                long sizeBytes = uploaded.getSizeBytes() != null ? uploaded.getSizeBytes() : 0L;
                if (sizeBytes == 0L) {
                    throw new IOException("Uploaded object has zero size: " + blobName);
                }
                wh = readImageDimensionsFromPrefix(capturing.getPrefixBuffer(), capturing.getPrefixLength());
            } finally {
                mediaResp.disconnect();
            }
        }

        long sizeBytes = uploaded.getSizeBytes() != null ? uploaded.getSizeBytes() : 0L;
        if (sizeBytes == 0L) {
            throw new IOException("Resolved object has zero size: " + blobName);
        }
        int width = wh[0];
        int height = wh[1];

        long creationTime = resolveBestCreationTime(driveFile);
        long aspectRatio = height > 0 ? Math.round((width * 1000.0) / height) : 0;

        CreatorDetails details = new CreatorDetails();
        details.setUserId(creatorId);
        details.setUserName(userName);
        details.setRole(PHOTOGRAPHER_ROLE);

        Media media = new Media();
        media.setType(MediaType.IMAGE);
        media.setUrl(uploaded.getPublicUrl());
        media.setWidth(width);
        media.setHeight(height);

        Moment moment = new Moment();
        moment.setMomentId(momentId);
        moment.setCreatorId(creatorId);
        moment.setEventId(eventId);
        moment.setCreationTime(creationTime);
        moment.setCreatorRole(PHOTOGRAPHER_ROLE);
        moment.setCreatorDetails(details);
        moment.setMedia(media);
        moment.setAspectRatio(aspectRatio);

        MomentMemoryUsage usage = new MomentMemoryUsage();
        usage.setOriginalUploadSizeBytes(sizeBytes);
        moment.setMemoryUsage(usage);

        return new DriveImportOutcome(moment, false);
    }

    private static final class DriveImportOutcome {
        final Moment moment;
        final boolean skippedDuplicate;

        DriveImportOutcome(Moment moment, boolean skippedDuplicate) {
            this.moment = moment;
            this.skippedDuplicate = skippedDuplicate;
        }
    }

    private static int[] readImageDimensionsFromPrefix(byte[] data, int len) {
        if (data == null || len <= 0) {
            return new int[] { 0, 0 };
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data, 0, len));
            if (img != null) {
                return new int[] { img.getWidth(), img.getHeight() };
            }
        } catch (Exception ignored) {
            // try ImageReader below
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data, 0, len))) {
            if (iis == null) {
                return new int[] { 0, 0 };
            }
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) {
                return new int[] { 0, 0 };
            }
            ImageReader reader = it.next();
            try {
                reader.setInput(iis, true, true);
                return new int[] { reader.getWidth(0), reader.getHeight(0) };
            } catch (Exception ignored) {
                return new int[] { 0, 0 };
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return new int[] { 0, 0 };
        }
    }

    /**
     * Passes through the delegate stream while copying the first {@code maxPrefix} bytes into an internal buffer
     * (for metadata probes) without holding the full object in memory.
     */
    private static final class PrefixCaptureInputStream extends FilterInputStream {
        private final byte[] prefix;
        private int prefixLen;

        PrefixCaptureInputStream(InputStream in, int maxPrefix) {
            super(in);
            this.prefix = new byte[maxPrefix];
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && prefixLen < prefix.length) {
                prefix[prefixLen++] = (byte) b;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0 && prefixLen < prefix.length) {
                int copy = Math.min(n, prefix.length - prefixLen);
                System.arraycopy(b, off, prefix, prefixLen, copy);
                prefixLen += copy;
            }
            return n;
        }

        byte[] getPrefixBuffer() {
            return prefix;
        }

        int getPrefixLength() {
            return prefixLen;
        }
    }

    private long resolveBestCreationTime(File driveFile) {
        // Prefer original capture timestamp (EXIF via Drive imageMediaMetadata.time),
        // fallback to modified time.
        try {
            if (driveFile.getImageMediaMetadata() != null && driveFile.getImageMediaMetadata().getTime() != null) {
                String captureTime = driveFile.getImageMediaMetadata().getTime();
                try {
                    return OffsetDateTime.parse(captureTime).toInstant().toEpochMilli();
                } catch (DateTimeParseException ignored) {
                    return Instant.parse(captureTime).toEpochMilli();
                }
            }
        } catch (Exception ignored) {
            // Keep fallback behavior below.
        }
        if (driveFile.getModifiedTime() != null) {
            return driveFile.getModifiedTime().getValue();
        }
        return System.currentTimeMillis();
    }

    private static final class DriveAccess {
        final Drive drive;
        /**
         * Shared drives / private SA access; false for API key + public My Drive links
         */
        final boolean supportsAllDrives;

        DriveAccess(Drive drive, boolean supportsAllDrives) {
            this.drive = drive;
            this.supportsAllDrives = supportsAllDrives;
        }
    }
}
