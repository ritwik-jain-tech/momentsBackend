package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import com.moments.service.GoogleCloudStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired
    private GoogleCloudStorageService storageService;

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
}
