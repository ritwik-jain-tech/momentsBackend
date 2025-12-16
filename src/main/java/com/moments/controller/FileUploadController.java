package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.BulkUploadResponse;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.models.Media;
import com.moments.models.MediaType;
import com.moments.models.Moment;
import com.moments.service.GoogleCloudStorageService;
import com.moments.service.MomentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(
        origins = "https://admin.moments.live",
        allowedHeaders = "*",
        methods = {RequestMethod.POST, RequestMethod.OPTIONS},
        allowCredentials = "true")
public class FileUploadController {

    @Autowired
    private GoogleCloudStorageService storageService;

    @Autowired
    private MomentService momentService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 5;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("fileType") FileType fileType) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BaseResponse("File cannot be empty",HttpStatus.BAD_REQUEST, null ));
            }

            // Upload the file and get its public URL
            FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType);
            BaseResponse response = new BaseResponse("Successfully uploaded file",HttpStatus.OK, fileUploadResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BaseResponse("Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR, null ));
        }
    }

    @PostMapping("/bulk-upload")
    public ResponseEntity<BaseResponse> bulkUploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("fileType") FileType fileType) {
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
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType);
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

                        // Upload the file and get its public URL
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, FileType.IMAGE);
                        
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

                        // Upload the file and get its public URL
                        FileUploadResponse fileUploadResponse = storageService.uploadFile(file, fileType);
                        
                        // Update the moment's media URL with the uploaded URL
                        moment.getMedia().setUrl(fileUploadResponse.getPublicUrl());
                        
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
}
