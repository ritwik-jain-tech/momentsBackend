package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.BulkUploadResponse;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.Media;
import com.moments.models.MediaType;
import com.moments.models.Moment;
import com.moments.models.MomentMemoryUsage;
import com.moments.config.DriveImportProperties;
import com.moments.models.GoogleDriveImportRequest;
import com.moments.models.GoogleDriveImportResponse;
import com.moments.models.ComputerUploadSessionRequest;
import com.moments.models.FinalizeUploadRequest;
import com.moments.models.SignedUploadRequest;
import com.moments.models.SignedUploadTarget;
import com.moments.models.UploadRecord;
import com.moments.service.GoogleCloudStorageService;
import com.moments.service.GoogleCloudStorageService.ExistingImageBlobHead;
import com.moments.service.GoogleDriveImportService;
import com.moments.service.MomentService;
import com.moments.service.UploadRecordService;
import com.moments.util.Cr3PreviewExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(
    originPatterns = {
            "https://studio.moments.live",
            "http://localhost:*",
            "http://127.0.0.1:*",
    },
    allowedHeaders = {"*"},
    methods = {RequestMethod.POST, RequestMethod.OPTIONS, RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.HEAD}
)
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private GoogleCloudStorageService storageService;

    @Autowired
    private MomentService momentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoogleDriveImportService googleDriveImportService;

    @Autowired
    private DriveImportProperties driveImportProperties;

    @Autowired
    private UploadRecordService uploadRecordService;

    private static final int BATCH_SIZE = 5;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") FileType fileType,
            @RequestParam(value = "eventId", required = false) String eventId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BaseResponse("File cannot be empty",HttpStatus.BAD_REQUEST, null ));
            }

            // Upload the file and get its public URL
            String eid = eventId != null ? eventId.trim() : null;
            FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType, eid);
            BaseResponse response = new BaseResponse("Successfully uploaded file",HttpStatus.OK, fileUploadResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse("Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR, null ));
        }
    }

    @PostMapping("/bulk-upload")
    public ResponseEntity<BaseResponse> bulkUploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("fileType") FileType fileType,
            @RequestParam(value = "eventId", required = false) String eventId) {
        try {
            if (files == null || files.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("No files provided", HttpStatus.BAD_REQUEST, null));
            }

            // Validate that we don't exceed reasonable limits
            if (files.length > 50) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Maximum 50 files allowed per request", HttpStatus.BAD_REQUEST, null));
            }

            String eid = eventId != null ? eventId.trim() : null;

            List<FileUploadResponse> successfulFiles = new ArrayList<>();
            List<BulkUploadResponse.FileUploadError> failedFiles = new ArrayList<>();

            // Process files in batches of 5 to manage memory and avoid overwhelming the system
            for (int i = 0; i < files.length; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, files.length);
                
                // Process current batch
                for (int j = i; j < endIndex; j++) {
                    MultipartFile file = files[j];
                    String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
                    
                    try {
                        if (file.isEmpty()) {
                            failedFiles.add(new BulkUploadResponse.FileUploadError(
                                    fileName, "File is empty"));
                            continue;
                        }

                        // Upload the file and get its public URL
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType, eid);
                        successfulFiles.add(fileUploadResponse);
                    } catch (Exception e) {
                        failedFiles.add(new BulkUploadResponse.FileUploadError(
                                fileName, "Upload failed: " + e.getMessage()));
                    }
                }
            }

            BulkUploadResponse bulkResponse = new BulkUploadResponse(
                    files.length,
                    successfulFiles.size(),
                    failedFiles.size(),
                    successfulFiles,
                    failedFiles
            );

            HttpStatus status = failedFiles.isEmpty() ? HttpStatus.OK : 
                               (successfulFiles.isEmpty() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.PARTIAL_CONTENT);
            
            String message = failedFiles.isEmpty() ? 
                    "All files uploaded successfully" : 
                    String.format("Uploaded %d of %d files", successfulFiles.size(), files.length);

            BaseResponse response = new BaseResponse(message, status, bulkResponse);
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Internal Server Error: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/bulk-upload-moments")
    public ResponseEntity<BaseResponse> bulkUploadFilesAndCreateMoments(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("creatorUserID") String creatorUserID,
            @RequestParam("eventId") String eventId,
            @RequestParam("createdTimestamps") Long[] createdTimestamps) {
        try {
            // Validate input parameters
            if (files == null || files.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("No files provided", HttpStatus.BAD_REQUEST, null));
            }

            if (creatorUserID == null || creatorUserID.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("creatorUserID is required", HttpStatus.BAD_REQUEST, null));
            }

            if (eventId == null || eventId.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("eventId is required", HttpStatus.BAD_REQUEST, null));
            }

            if (createdTimestamps == null || createdTimestamps.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("createdTimestamps array is required", HttpStatus.BAD_REQUEST, null));
            }

            // Validate that timestamps array matches files array length
            if (createdTimestamps.length != files.length) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Number of createdTimestamps must match number of files. Expected: " + 
                                files.length + ", Got: " + createdTimestamps.length, HttpStatus.BAD_REQUEST, null));
            }

            // Validate all timestamps are valid
            for (int i = 0; i < createdTimestamps.length; i++) {
                if (createdTimestamps[i] == null || createdTimestamps[i] <= 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("createdTimestamp at index " + i + " is invalid. All timestamps must be valid positive numbers", 
                                    HttpStatus.BAD_REQUEST, null));
                }
            }

            // Validate that we don't exceed reasonable limits
            if (files.length > 50) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Maximum 50 files allowed per request", HttpStatus.BAD_REQUEST, null));
            }

            List<Moment> momentsToCreate = new ArrayList<>();
            List<BulkUploadResponse.FileUploadError> failedFiles = new ArrayList<>();

            // Process files in batches of 5 to manage memory and avoid overwhelming the system
            for (int i = 0; i < files.length; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, files.length);
                
                // Process current batch
                for (int j = i; j < endIndex; j++) {
                    MultipartFile file = files[j];
                    Long createdTimestamp = createdTimestamps[j];
                    String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
                    
                    try {
                        if (file.isEmpty()) {
                            failedFiles.add(new BulkUploadResponse.FileUploadError(
                                    fileName, "File is empty"));
                            continue;
                        }

                        // Upload the file and get its public URL (scoped under events/{eventId}/)
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, FileType.IMAGE,
                                eventId.trim());
                        
                        // Create Media object with the uploaded URL
                        Media media = new Media();
                        media.setUrl(fileUploadResponse.getPublicUrl());
                        media.setType(MediaType.IMAGE);
                        
                        // Create Moment object with individual timestamp
                        Moment moment = new Moment();
                        moment.setCreatorId(creatorUserID);
                        moment.setEventId(eventId);
                        moment.setCreationTime(createdTimestamp);
                        moment.setMedia(media);
                        MomentMemoryUsage usage = new MomentMemoryUsage();
                        usage.setOriginalUploadSizeBytes(file.getSize());
                        moment.setMemoryUsage(usage);

                        // Idempotency: stable moment id from the deterministic GCS object key so a
                        // re-uploaded file maps to the same moment instead of a duplicate.
                        String detId = momentService.deterministicUploadMomentId(fileUploadResponse.getFileName());
                        if (detId != null) {
                            moment.setMomentId(detId);
                        }

                        // CR3 is not browser-renderable; store its embedded JPEG preview as feedUrl.
                        attachCr3PreviewIfNeeded(moment, file, eventId.trim());

                        momentsToCreate.add(moment);
                    } catch (Exception e) {
                        failedFiles.add(new BulkUploadResponse.FileUploadError(
                                fileName, "Upload failed: " + e.getMessage()));
                    }
                }
            }

            // Save all moments in batch
            List<String> createdMomentIds = new ArrayList<>();
            if (!momentsToCreate.isEmpty()) {
                try {
                    createdMomentIds = momentService.saveMoments(momentsToCreate,false);
                } catch (ExecutionException | InterruptedException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new BaseResponse("Failed to create moments: " + e.getMessage(), 
                                    HttpStatus.INTERNAL_SERVER_ERROR, null));
                }
            }

            // Prepare response
            BulkUploadResponse bulkResponse = new BulkUploadResponse(
                    files.length,
                    createdMomentIds.size(),
                    failedFiles.size(),
                    null, // successfulFiles - not needed for this endpoint
                    failedFiles
            );

            HttpStatus status = failedFiles.isEmpty() ? HttpStatus.OK : 
                               (createdMomentIds.isEmpty() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.PARTIAL_CONTENT);
            
            String message = failedFiles.isEmpty() ? 
                    String.format("Successfully uploaded %d files and created %d moments", files.length, createdMomentIds.size()) : 
                    String.format("Uploaded %d of %d files and created %d moments", createdMomentIds.size(), files.length, createdMomentIds.size());

            BaseResponse response = new BaseResponse(message, status, bulkResponse);
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Internal Server Error: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    @PostMapping("/bulk-upload-moments-with-details")
    public ResponseEntity<BaseResponse> bulkUploadFilesAndCreateMomentsWithDetails(
            @RequestPart("moments") String momentsJson,
            @RequestParam("files") MultipartFile[] files) {
        try {
            // Parse moments JSON
            List<Moment> moments;
            try {
                moments = objectMapper.readValue(momentsJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Moment.class));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Invalid moments JSON: " + e.getMessage(), 
                                HttpStatus.BAD_REQUEST, null));
            }

            // Validate input parameters
            if (moments == null || moments.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("No moments provided", HttpStatus.BAD_REQUEST, null));
            }

            if (files == null || files.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("No files provided", HttpStatus.BAD_REQUEST, null));
            }

            // Validate that files array matches moments array length
            if (files.length != moments.size()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Number of files must match number of moments. Expected: " + 
                                moments.size() + ", Got: " + files.length, HttpStatus.BAD_REQUEST, null));
            }

            // Validate that we don't exceed reasonable limits
            if (files.length > 50) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Maximum 50 files allowed per request", HttpStatus.BAD_REQUEST, null));
            }

            // Validate each moment has required fields
            for (int i = 0; i < moments.size(); i++) {
                Moment moment = moments.get(i);
                if (moment.getCreatorId() == null || moment.getCreatorId().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("Moment at index " + i + " has missing or empty creatorId", 
                                    HttpStatus.BAD_REQUEST, null));
                }
                if (moment.getEventId() == null || moment.getEventId().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("Moment at index " + i + " has missing or empty eventId", 
                                    HttpStatus.BAD_REQUEST, null));
                }
                if (moment.getCreationTime() == null || moment.getCreationTime() <= 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("Moment at index " + i + " has invalid creationTime", 
                                    HttpStatus.BAD_REQUEST, null));
                }
                if (moment.getMedia() == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("Moment at index " + i + " has missing media object", 
                                    HttpStatus.BAD_REQUEST, null));
                }
            }

            List<Moment> momentsToCreate = new ArrayList<>();
            List<BulkUploadResponse.FileUploadError> failedFiles = new ArrayList<>();

            // Process files in batches of 5 to manage memory and avoid overwhelming the system
            for (int i = 0; i < files.length; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, files.length);
                
                // Process current batch
                for (int j = i; j < endIndex; j++) {
                    MultipartFile file = files[j];
                    Moment moment = moments.get(j);
                    String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
                    
                    try {
                        if (file.isEmpty()) {
                            failedFiles.add(new BulkUploadResponse.FileUploadError(
                                    fileName, "File is empty"));
                            continue;
                        }

                        // Determine file type from moment's media type
                        FileType fileType = FileType.IMAGE;
                        if (moment.getMedia().getType() != null) {
                            fileType = moment.getMedia().getType() == MediaType.VIDEO ? FileType.VIDEO : FileType.IMAGE;
                        }

                        // Upload the file and get its public URL (scoped under events/{eventId}/)
                        String uploadEventId = moment.getEventId() != null ? moment.getEventId().trim() : null;
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType, uploadEventId);
                        
                        // Update the moment's media URL with the uploaded URL
                        moment.getMedia().setUrl(fileUploadResponse.getPublicUrl());

                        // Idempotency: derive a stable moment id from the (deterministic) GCS object
                        // key so re-uploading the same file to the same event doesn't create a
                        // duplicate moment. Only set it when the client didn't provide one.
                        if (moment.getMomentId() == null || moment.getMomentId().isBlank()) {
                            String detId = momentService.deterministicUploadMomentId(fileUploadResponse.getFileName());
                            if (detId != null) {
                                moment.setMomentId(detId);
                            }
                        }

                        // CR3 is not browser-renderable; store its embedded JPEG preview as feedUrl
                        // (skipped when the client already supplied a feedUrl preview).
                        attachCr3PreviewIfNeeded(moment, file, uploadEventId);

                        MomentMemoryUsage usage = moment.getMemoryUsage() != null
                                ? moment.getMemoryUsage()
                                : new MomentMemoryUsage();
                        usage.setOriginalUploadSizeBytes(file.getSize());
                        moment.setMemoryUsage(usage);
                        
                        // Ensure media type is set correctly
                        if (moment.getMedia().getType() == null) {
                            moment.getMedia().setType( MediaType.IMAGE);
                        }
                        
                        momentsToCreate.add(moment);
                    } catch (Exception e) {
                        failedFiles.add(new BulkUploadResponse.FileUploadError(
                                fileName, "Upload failed: " + e.getMessage()));
                    }
                }
            }

            // Save all moments in batch
            List<String> createdMomentIds = new ArrayList<>();
            if (!momentsToCreate.isEmpty()) {
                try {
                    createdMomentIds = momentService.saveMoments(momentsToCreate, false);
                } catch (ExecutionException | InterruptedException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new BaseResponse("Failed to create moments: " + e.getMessage(), 
                                    HttpStatus.INTERNAL_SERVER_ERROR, null));
                }
            }

            // Prepare response
            BulkUploadResponse bulkResponse = new BulkUploadResponse(
                    files.length,
                    createdMomentIds.size(),
                    failedFiles.size(),
                    null, // successfulFiles - not needed for this endpoint
                    failedFiles
            );

            HttpStatus status = failedFiles.isEmpty() ? HttpStatus.OK : 
                               (createdMomentIds.isEmpty() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.PARTIAL_CONTENT);
            
            String message = failedFiles.isEmpty() ? 
                    String.format("Successfully uploaded %d files and created %d moments", files.length, createdMomentIds.size()) : 
                    String.format("Uploaded %d of %d files and created %d moments", createdMomentIds.size(), files.length, createdMomentIds.size());

            BaseResponse response = new BaseResponse(message, status, bulkResponse);
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Internal Server Error: " + e.getMessage(), 
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Issue direct-to-GCS signed PUT URLs so the browser can upload files (esp. large RAW/CR3
     * originals) straight to Cloud Storage, bypassing Cloud Run's ~32 MiB request-body cap.
     * After PUTting, the client calls {@link #finalizeMoments} to create the moments.
     */
    @PostMapping("/signed-upload-url")
    public ResponseEntity<BaseResponse> createSignedUploadUrls(@RequestBody SignedUploadRequest request) {
        try {
            if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("files is required", HttpStatus.BAD_REQUEST, null));
            }
            if (request.getFiles().size() > 50) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Maximum 50 files per request", HttpStatus.BAD_REQUEST, null));
            }
            String eventId = request.getEventId() != null ? request.getEventId().trim() : null;

            List<SignedUploadTarget> targets = new ArrayList<>();
            for (SignedUploadRequest.Item item : request.getFiles()) {
                if (item == null || item.getFilename() == null || item.getFilename().isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new BaseResponse("Each file requires a filename", HttpStatus.BAD_REQUEST, null));
                }
                FileType fileType = item.getFileType() != null ? item.getFileType() : FileType.IMAGE;
                targets.add(storageService.createUploadUrl(
                        eventId, item.getFilename(), fileType, item.getContentType()));
            }
            return ResponseEntity.ok(new BaseResponse("OK", HttpStatus.OK, targets));
        } catch (IllegalStateException e) {
            // Signer misconfiguration (e.g. missing IAM signBlob permission on Cloud Run).
            logger.error("Signed upload URL generation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BaseResponse("Direct upload is not available: " + e.getMessage(),
                            HttpStatus.SERVICE_UNAVAILABLE, null));
        } catch (Exception e) {
            logger.error("Signed upload URL generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to create upload URLs: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Create moments for files already uploaded to GCS via {@link #createSignedUploadUrls} signed
     * URLs. Each moment's {@code media.url} must be the {@code publicUrl} from the signed-URL step.
     * Object size and any CR3 embedded-JPEG preview are read back from GCS (no file bytes here).
     */
    @PostMapping("/finalize-moments")
    public ResponseEntity<BaseResponse> finalizeMoments(@RequestBody FinalizeUploadRequest request) {
        try {
            List<Moment> moments = request != null ? request.getMoments() : null;
            if (moments == null || moments.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("moments is required", HttpStatus.BAD_REQUEST, null));
            }
            if (moments.size() > 50) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Maximum 50 moments per request", HttpStatus.BAD_REQUEST, null));
            }

            List<Moment> momentsToCreate = new ArrayList<>();
            List<BulkUploadResponse.FileUploadError> failed = new ArrayList<>();

            for (int i = 0; i < moments.size(); i++) {
                Moment moment = moments.get(i);
                String label = "moment[" + i + "]";
                try {
                    if (moment == null || moment.getMedia() == null
                            || moment.getMedia().getUrl() == null || moment.getMedia().getUrl().isBlank()) {
                        failed.add(new BulkUploadResponse.FileUploadError(label, "media.url is required"));
                        continue;
                    }
                    if (moment.getCreatorId() == null || moment.getCreatorId().isBlank()
                            || moment.getEventId() == null || moment.getEventId().isBlank()
                            || moment.getCreationTime() == null || moment.getCreationTime() <= 0) {
                        failed.add(new BulkUploadResponse.FileUploadError(label,
                                "creatorId, eventId and a positive creationTime are required"));
                        continue;
                    }

                    String publicUrl = moment.getMedia().getUrl();
                    String objectName = storageService.objectNameFromPublicUrl(publicUrl);
                    if (objectName == null) {
                        failed.add(new BulkUploadResponse.FileUploadError(label,
                                "media.url is not a recognized upload URL: " + publicUrl));
                        continue;
                    }

                    // Read object head from GCS for size + CR3 preview; verifies the object exists.
                    ExistingImageBlobHead head = storageService.readImageBlobHead(
                            objectName, Cr3PreviewExtractor.DEFAULT_SCAN_LIMIT_BYTES);
                    if (head == null) {
                        failed.add(new BulkUploadResponse.FileUploadError(label,
                                "Uploaded object not found in storage: " + objectName));
                        continue;
                    }

                    MomentMemoryUsage usage = moment.getMemoryUsage() != null
                            ? moment.getMemoryUsage()
                            : new MomentMemoryUsage();
                    usage.setOriginalUploadSizeBytes(head.getSizeBytes());
                    moment.setMemoryUsage(usage);

                    if (moment.getMedia().getType() == null) {
                        moment.getMedia().setType(MediaType.IMAGE);
                    }

                    if (moment.getMomentId() == null || moment.getMomentId().isBlank()) {
                        String detId = momentService.deterministicUploadMomentId(objectName);
                        if (detId != null) {
                            moment.setMomentId(detId);
                        }
                    }

                    attachCr3PreviewFromGcs(moment, objectName, head, moment.getEventId().trim());

                    momentsToCreate.add(moment);
                } catch (Exception e) {
                    failed.add(new BulkUploadResponse.FileUploadError(label, "Finalize failed: " + e.getMessage()));
                }
            }

            List<String> createdMomentIds = new ArrayList<>();
            if (!momentsToCreate.isEmpty()) {
                try {
                    createdMomentIds = momentService.saveMoments(momentsToCreate, false);
                } catch (ExecutionException | InterruptedException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new BaseResponse("Failed to create moments: " + e.getMessage(),
                                    HttpStatus.INTERNAL_SERVER_ERROR, null));
                }
            }

            BulkUploadResponse bulkResponse = new BulkUploadResponse(
                    moments.size(), createdMomentIds.size(), failed.size(), null, failed);
            HttpStatus status = failed.isEmpty() ? HttpStatus.OK
                    : (createdMomentIds.isEmpty() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.PARTIAL_CONTENT);
            String message = failed.isEmpty()
                    ? String.format("Created %d moment(s)", createdMomentIds.size())
                    : String.format("Created %d of %d moment(s)", createdMomentIds.size(), moments.size());
            return ResponseEntity.status(status).body(new BaseResponse(message, status, bulkResponse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Internal Server Error: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * List every stored file for an event (direct uploads under {@code events/{eventId}/} and Drive
     * imports under {@code drive-import/{eventId}/}) so the folder can be reviewed or exported.
     */
    @GetMapping("/events/{eventId}/files")
    public ResponseEntity<BaseResponse> listEventFiles(@PathVariable String eventId) {
        try {
            if (eventId == null || eventId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("eventId is required", HttpStatus.BAD_REQUEST, null));
            }
            List<GoogleCloudStorageService.StoredObject> files = storageService.listEventObjects(eventId.trim());
            return ResponseEntity.ok(new BaseResponse("OK", HttpStatus.OK, files));
        } catch (Exception e) {
            logger.error("Failed to list event files for {}", eventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to list event files: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Export an entire event folder as a streamed ZIP download. Objects are copied straight from GCS
     * through a bounded buffer, so memory stays flat regardless of event size (large events are
     * bounded only by the request timeout).
     */
    @GetMapping("/events/{eventId}/export")
    public ResponseEntity<StreamingResponseBody> exportEventFolder(@PathVariable String eventId) {
        String safeEvent = eventId != null ? eventId.trim() : "";
        StreamingResponseBody body = out -> storageService.streamEventZip(safeEvent, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"event-" + safeEvent + ".zip\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    /**
     * Import images from a publicly shared Google Drive folder or file link (recursive for folders).
     * Configure {@code google.drive.api.key} for "Anyone with the link" content, or
     * {@code google.drive.credentials.path} for private/shared-drive folders.
     * Moments are created with creatorRole "Photographer".
     */
    @PostMapping("/import-google-drive-folder")
    public ResponseEntity<BaseResponse> importGoogleDriveFolder(@RequestBody GoogleDriveImportRequest request) {
        try {
            if (request == null || request.getFolderUrl() == null || request.getFolderUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("folderUrl is required", HttpStatus.BAD_REQUEST, null));
            }
            if (!googleDriveImportService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new BaseResponse(
                                "Google Drive import is not configured. Set GOOGLE_DRIVE_API_KEY, GOOGLE_DRIVE_CREDENTIALS_PATH, or use the Cloud Run service account with Drive API enabled.",
                                HttpStatus.SERVICE_UNAVAILABLE, null));
            }
            if (!googleDriveImportService.isDriveLinkAccessible(request.getFolderUrl())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new BaseResponse(
                                "This appears to be a private Google Drive link. Please provide a public link (Anyone with the link can view).",
                                HttpStatus.FORBIDDEN, null));
            }
            if (request.getCreatorId() == null || request.getCreatorId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("creatorId is required", HttpStatus.BAD_REQUEST, null));
            }
            if (request.getEventId() == null || request.getEventId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("eventId is required", HttpStatus.BAD_REQUEST, null));
            }

            String uploadRecordId = uploadRecordService.createStartedForDriveImport(
                    request.getCreatorId().trim(),
                    request.getEventId().trim(),
                    request.getCreatorUserName(),
                    request.getFolderUrl().trim());
            request.setUploadRecordId(uploadRecordId);

            if (driveImportProperties.isAsyncImport()) {
                googleDriveImportService.importFolderAsync(request);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("uploadRecordId", uploadRecordId);
                return ResponseEntity.ok(new BaseResponse(
                        "Your Drive import has started. Photos will appear in Moments shortly - you can continue with other work.",
                        HttpStatus.OK,
                        data));
            }
            GoogleDriveImportResponse result = googleDriveImportService.importFolder(request);
            String msg = String.format(
                    "Drive import finished: created %d moment(s), %d failed, %d image file(s) found.",
                    result.getMomentsCreated(), result.getFailed(), result.getImageFilesFound());
            HttpStatus st = result.getMomentsCreated() > 0 || result.getErrors().isEmpty()
                    ? HttpStatus.OK
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(st).body(new BaseResponse(msg, st, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Drive import failed: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Google Drive import jobs for the uploads tab (newest first).
     */
    @GetMapping("/upload-records")
    public ResponseEntity<BaseResponse> listUploadRecords(@RequestParam("userId") String userId) {
        try {
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("userId is required", HttpStatus.BAD_REQUEST, null));
            }
            List<UploadRecord> records = uploadRecordService.listForUserNewestFirst(userId.trim());
            return ResponseEntity.ok(new BaseResponse("OK", HttpStatus.OK, records));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to list upload records: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Create or update a browser/computer upload session record for the activity UI. Called at the
     * start of an upload (IN_PROGRESS), on pause / page-unload (PAUSED, so the session survives a
     * refresh), and on completion (DONE / STOPPED). A body without {@code uploadRecordId}/{@code status}
     * behaves like the legacy finalize call and creates a single DONE record.
     */
    @PostMapping("/upload-records/computer-session")
    public ResponseEntity<BaseResponse> recordComputerUploadSession(
            @RequestParam("userId") String userId,
            @RequestBody ComputerUploadSessionRequest body) {
        try {
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("userId is required", HttpStatus.BAD_REQUEST, null));
            }
            if (body == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("Request body is required", HttpStatus.BAD_REQUEST, null));
            }
            String recordId = uploadRecordService.upsertComputerUploadSession(
                    userId.trim(),
                    body.getUploadRecordId(),
                    body.getEventId() != null ? body.getEventId().trim() : null,
                    body.getCreatorName(),
                    body.getTotalCount(),
                    body.getUploadedCount(),
                    body.getFailedCount(),
                    body.getStatus());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadRecordId", recordId);
            return ResponseEntity.ok(new BaseResponse("Upload session recorded.", HttpStatus.OK, data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to record upload session: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Request cooperative pause for a running Drive import (stops after the current batch).
     */
    @PostMapping("/upload-records/{recordId}/pause")
    public ResponseEntity<BaseResponse> pauseUploadRecord(@PathVariable String recordId,
            @RequestParam("userId") String userId) {
        try {
            if (recordId == null || recordId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("recordId is required", HttpStatus.BAD_REQUEST, null));
            }
            uploadRecordService.requestPause(recordId, userId);
            return ResponseEntity.ok(new BaseResponse(
                    "Pause requested. The import will stop after the current batch.", HttpStatus.OK, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.CONFLICT, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to pause import: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Cancel a not-yet-finished upload record (marks it STOPPED). Works for Drive imports and
     * computer sessions; a completed record can't be cancelled.
     */
    @PostMapping("/upload-records/{recordId}/cancel")
    public ResponseEntity<BaseResponse> cancelUploadRecord(@PathVariable String recordId,
            @RequestParam("userId") String userId) {
        try {
            if (recordId == null || recordId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("recordId is required", HttpStatus.BAD_REQUEST, null));
            }
            uploadRecordService.cancel(recordId, userId);
            return ResponseEntity.ok(new BaseResponse("Upload cancelled.", HttpStatus.OK, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.CONFLICT, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to cancel upload: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * Resume / retry a Drive import using the same record (idempotent for already-imported files).
     */
    @PostMapping("/upload-records/{recordId}/retrigger")
    public ResponseEntity<BaseResponse> retriggerUploadRecord(@PathVariable String recordId,
            @RequestParam("userId") String userId) {
        try {
            if (recordId == null || recordId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponse("recordId is required", HttpStatus.BAD_REQUEST, null));
            }
            if (!googleDriveImportService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new BaseResponse(
                                "Google Drive import is not configured on the server.",
                                HttpStatus.SERVICE_UNAVAILABLE, null));
            }
            UploadRecord record = uploadRecordService.assertRetriggerEligible(recordId, userId);
            if (!googleDriveImportService.isDriveLinkAccessible(record.getDriveLink())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new BaseResponse(
                                "This Drive link is not accessible (check sharing). Import was not restarted.",
                                HttpStatus.FORBIDDEN, null));
            }
            GoogleDriveImportRequest req = uploadRecordService.commitRetriggerAndBuildRequest(record);
            if (driveImportProperties.isAsyncImport()) {
                googleDriveImportService.importFolderAsync(req);
                return ResponseEntity.ok(new BaseResponse(
                        "Import restarted. Progress will update on this card shortly.", HttpStatus.OK, null));
            }
            GoogleDriveImportResponse result = googleDriveImportService.importFolder(req);
            String msg = result.isPaused()
                    ? "Import paused again after restart."
                    : String.format("Import finished: created %d, skipped %d, failed %d.",
                            result.getMomentsCreated(), result.getMomentsSkipped(), result.getFailed());
            return ResponseEntity.ok(new BaseResponse(msg, HttpStatus.OK, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.BAD_REQUEST, null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BaseResponse(e.getMessage(), HttpStatus.CONFLICT, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse("Failed to restart import: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

    /**
     * For a Canon CR3 upload, extracts the embedded JPEG preview and stores it as the moment's
     * {@code media.feedUrl} (and {@code thumbnailUrl} when absent) so the moment is renderable in the
     * feed immediately, before the Python face-tagging pipeline produces optimized derivatives.
     *
     * <p>No-op for non-CR3 files, or when the client already supplied a {@code feedUrl} preview.
     * Failures are logged and swallowed so a preview problem never blocks moment creation.</p>
     */
    private void attachCr3PreviewIfNeeded(Moment moment, MultipartFile file, String eventId) {
        if (moment == null || moment.getMedia() == null || file == null) {
            return;
        }
        Media media = moment.getMedia();
        String originalFilename = file.getOriginalFilename();
        if (!GoogleCloudStorageService.isCanonCr3Filename(originalFilename)) {
            return;
        }
        if (media.getFeedUrl() != null && !media.getFeedUrl().isBlank()) {
            // Client already extracted an embedded-JPEG preview and set feedUrl; keep it.
            return;
        }
        try {
            byte[] prefix = readPrefix(file, Cr3PreviewExtractor.DEFAULT_SCAN_LIMIT_BYTES);
            byte[] jpeg = Cr3PreviewExtractor.extractLargestJpeg(prefix);
            if (jpeg == null) {
                logger.warn("CR3 preview: no embedded JPEG found in {}", originalFilename);
                return;
            }
            String previewName = cr3PreviewObjectName(originalFilename);
            FileUploadResponse preview = storageService.uploadBytes(
                    jpeg, previewName, FileType.IMAGE, "image/jpeg", eventId);
            media.setFeedUrl(preview.getPublicUrl());
            if (media.getThumbnailUrl() == null || media.getThumbnailUrl().isBlank()) {
                media.setThumbnailUrl(preview.getPublicUrl());
            }
            logger.info("CR3 preview: attached embedded JPEG for {} -> {} ({} bytes)",
                    originalFilename, preview.getPublicUrl(), jpeg.length);
        } catch (Exception e) {
            logger.warn("CR3 preview extraction failed for {}: {}", originalFilename, e.getMessage());
        }
    }

    /**
     * CR3 preview for the signed-URL (direct-to-GCS) path: the object is already in storage, so the
     * embedded-JPEG preview is extracted from the object's prefix bytes (read via
     * {@link GoogleCloudStorageService#readImageBlobHead}) rather than a {@link MultipartFile}.
     * No-op for non-CR3 objects or when the client already supplied a {@code feedUrl}. Failures are
     * logged and swallowed so a preview problem never blocks moment creation.
     */
    private void attachCr3PreviewFromGcs(Moment moment, String objectName, ExistingImageBlobHead head,
            String eventId) {
        if (moment == null || moment.getMedia() == null || head == null) {
            return;
        }
        if (!GoogleCloudStorageService.isCanonCr3Filename(objectName)) {
            return;
        }
        Media media = moment.getMedia();
        if (media.getFeedUrl() != null && !media.getFeedUrl().isBlank()) {
            return;
        }
        try {
            byte[] prefix = head.getPrefixLength() == head.getPrefix().length
                    ? head.getPrefix()
                    : java.util.Arrays.copyOf(head.getPrefix(), head.getPrefixLength());
            byte[] jpeg = Cr3PreviewExtractor.extractLargestJpeg(prefix);
            if (jpeg == null) {
                logger.warn("CR3 preview: no embedded JPEG found in {}", objectName);
                return;
            }
            String previewName = cr3PreviewObjectName(objectName);
            FileUploadResponse preview = storageService.uploadBytes(
                    jpeg, previewName, FileType.IMAGE, "image/jpeg", eventId);
            media.setFeedUrl(preview.getPublicUrl());
            if (media.getThumbnailUrl() == null || media.getThumbnailUrl().isBlank()) {
                media.setThumbnailUrl(preview.getPublicUrl());
            }
            logger.info("CR3 preview: attached embedded JPEG for {} -> {} ({} bytes)",
                    objectName, preview.getPublicUrl(), jpeg.length);
        } catch (Exception e) {
            logger.warn("CR3 preview extraction failed for {}: {}", objectName, e.getMessage());
        }
    }

    /** Reads up to {@code maxBytes} from the start of the upload (embedded CR3 previews live near the front). */
    private static byte[] readPrefix(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream in = file.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.min(maxBytes, 1 << 20));
            byte[] chunk = new byte[64 * 1024];
            int total = 0;
            int n;
            while (total < maxBytes && (n = in.read(chunk, 0, Math.min(chunk.length, maxBytes - total))) != -1) {
                bos.write(chunk, 0, n);
                total += n;
            }
            return bos.toByteArray();
        }
    }

    /** {@code events/{eventId}/foo.cr3} -> preview object {@code foo_preview.jpg}. */
    private static String cr3PreviewObjectName(String originalFilename) {
        String base = originalFilename == null || originalFilename.isBlank() ? "image" : originalFilename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash < base.length() - 1) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + "_preview.jpg";
    }
}
