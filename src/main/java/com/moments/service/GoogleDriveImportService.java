package com.moments.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
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

import jakarta.annotation.PostConstruct;

@Service
public class GoogleDriveImportService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveImportService.class);
    private static final String PHOTOGRAPHER_ROLE = "Photographer";
    private static final int MAX_FILES = 5000;
    private static final int MAX_FOLDER_DEPTH = 12;
    private static final int PROCESS_BATCH = 25;

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
            logger.info("Google Drive import: not configured (no API key and no readable service account path).");
        }
    }

    public boolean isConfigured() {
        if (preferServiceAccount && hasReadableServiceAccountCredentials()) {
            return true;
        }
        if (!normalizedDriveApiKey().isEmpty()) {
            return true;
        }
        return hasReadableServiceAccountCredentials();
    }

    public boolean isDriveLinkAccessible(String folderOrFileUrl) throws IOException {
        if (folderOrFileUrl == null || folderOrFileUrl.isBlank()) {
            throw new IllegalArgumentException("Google Drive link is required.");
        }
        if (!isConfigured()) {
            throw new IllegalArgumentException(
                    "Google Drive import is not configured. Set google.drive.api.key or google.drive.credentials.path.");
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
        }
    }

    public GoogleDriveImportResponse importFolder(GoogleDriveImportRequest request)
            throws IOException, ExecutionException, InterruptedException {
        GoogleDriveImportResponse response = new GoogleDriveImportResponse();
        if (!isConfigured()) {
            response.getErrors().add(
                    "Google Drive import is not configured. Set google.drive.api.key (public \"Anyone with the link\" folders/files, Drive API enabled) "
                            + "or google.drive.credentials.path (service account JSON for private folders).");
            return response;
        }

        String creatorId = request.getCreatorId() != null ? request.getCreatorId().trim() : "";
        if (creatorId.isEmpty()) {
            response.getErrors().add("creatorId is required.");
            return response;
        }
        String eventId = request.getEventId() != null ? request.getEventId().trim() : "";
        if (eventId.isEmpty()) {
            response.getErrors().add("eventId is required.");
            return response;
        }

        DriveAccess access = createDriveAccess();
        List<File> images = new ArrayList<>();
        String u = request.getFolderUrl() != null ? request.getFolderUrl().trim() : "";

        try {
            resolveImagesFromPublicOrSharedLink(access, u, images);
        } catch (IOException e) {
            logger.warn("Drive resolve failed: {}", e.getMessage());
            response.getErrors().add(
                    e.getMessage()
                            + " Ensure the link is shared as \"Anyone with the link\" (Viewer) when using an API key.");
            return response;
        }

        if (images.isEmpty()) {
            response.getErrors().add("No image files found for this link.");
            return response;
        }

        response.setImageFilesFound(images.size());

        if (images.size() > MAX_FILES) {
            response.getErrors().add("Found " + images.size() + " images; maximum per import is " + MAX_FILES + ".");
            return response;
        }

        String userName = "Photographer";

        int created = 0;
        int failed = 0;

        for (int start = 0; start < images.size(); start += PROCESS_BATCH) {
            int end = Math.min(start + PROCESS_BATCH, images.size());
            List<Moment> batch = new ArrayList<>();
            for (int i = start; i < end; i++) {
                File driveFile = images.get(i);
                try {
                    Moment moment = downloadAndBuildMoment(access.drive, driveFile, eventId, creatorId, userName,
                            access.supportsAllDrives);
                    if (moment != null) {
                        batch.add(moment);
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
                List<String> ids = momentService.saveMoments(batch, false);
                created += ids.size();
            }
        }

        response.setMomentsCreated(created);
        response.setFailed(failed);
        return response;
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
        if (path.isEmpty() || !Files.isReadable(Paths.get(path))) {
            throw new IOException("No readable google.drive.credentials.path");
        }
        return buildDriveWithServiceAccount(transport, path);
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

    private Moment downloadAndBuildMoment(Drive drive, File driveFile, String eventId, String creatorId,
            String userName,
            boolean supportsAllDrives) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Drive.Files.Get getReq = drive.files().get(driveFile.getId());
        if (supportsAllDrives) {
            getReq.setSupportsAllDrives(true);
        }
        getReq.executeMediaAndDownloadTo(out);
        byte[] bytes = out.toByteArray();
        if (bytes.length == 0) {
            return null;
        }

        String mime = driveFile.getMimeType() != null ? driveFile.getMimeType() : "image/jpeg";
        String filename = driveFile.getName() != null ? driveFile.getName() : (driveFile.getId() + ".jpg");

        FileUploadResponse uploaded = storageService.uploadBytes(bytes, filename, FileType.IMAGE, mime);

        int width = 0;
        int height = 0;
        try {
            BufferedImage buf = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buf != null) {
                width = buf.getWidth();
                height = buf.getHeight();
            }
        } catch (Exception ignored) {
            // HEIC / unsupported: keep defaults
        }

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
        moment.setCreatorId(creatorId);
        moment.setEventId(eventId);
        moment.setCreationTime(creationTime);
        moment.setCreatorRole(PHOTOGRAPHER_ROLE);
        moment.setCreatorDetails(details);
        moment.setMedia(media);
        moment.setAspectRatio(aspectRatio);

        MomentMemoryUsage usage = new MomentMemoryUsage();
        usage.setOriginalUploadSizeBytes((long) bytes.length);
        moment.setMemoryUsage(usage);

        return moment;
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
