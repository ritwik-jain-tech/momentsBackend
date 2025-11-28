package com.moments.controller;

import com.moments.models.BaseResponse;
import com.moments.service.MomentCompressionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/moments/compress")
public class MomentCompressionController {
    
    private static final Logger logger = LoggerFactory.getLogger(MomentCompressionController.class);
    
    @Autowired
    private MomentCompressionService compressionService;
    
    /**
     * Compresses all images for moments with the given eventId
     * @param eventId The event ID to process
     * @param quality Compression quality (0.0 to 1.0, where 1.0 is highest quality). Default: 0.6 (60%)
     * @return Compression statistics
     */
    @PostMapping("/event/{eventId}")
    public ResponseEntity<BaseResponse> compressMomentsByEventId(
            @PathVariable String eventId,
            @RequestParam(required = false, defaultValue = "0.6") Float quality) {
        try {
            if (eventId == null || eventId.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("EventId is required", HttpStatus.BAD_REQUEST, null));
            }
            
            // Validate quality parameter
            if (quality != null && (quality < 0.0f || quality > 1.0f)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse("Quality must be between 0.0 and 1.0", HttpStatus.BAD_REQUEST, null));
            }
            
            logger.info("Received compression request for eventId: {} with quality: {}", eventId, quality);
            
            MomentCompressionService.CompressionStatistics stats = 
                compressionService.compressMomentsByEventId(eventId, quality);
            
            // Build response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("eventId", stats.getEventId());
            responseData.put("totalMoments", stats.getTotalMoments());
            responseData.put("processed", stats.getProcessedCount());
            responseData.put("failed", stats.getFailedCount());
            responseData.put("skipped", stats.getSkippedCount());
            responseData.put("totalOriginalSize", stats.getTotalOriginalSize());
            responseData.put("totalCompressedSize", stats.getTotalCompressedSize());
            responseData.put("totalSizeReduction", stats.getTotalSizeReduction());
            responseData.put("overallCompressionRatio", String.format("%.2fx", stats.getOverallCompressionRatio()));
            responseData.put("processedBlobs", stats.getProcessedBlobs());
            responseData.put("failedBlobs", stats.getFailedBlobs());
            responseData.put("skippedBlobs", stats.getSkippedBlobs());
            
            String message = String.format(
                "Compression completed. Processed: %d, Failed: %d, Skipped: %d. " +
                "Total reduction: %d bytes (%.2fx compression)",
                stats.getProcessedCount(), stats.getFailedCount(), stats.getSkippedCount(),
                stats.getTotalSizeReduction(), stats.getOverallCompressionRatio()
            );
            
            return ResponseEntity.status(HttpStatus.OK)
                .body(new BaseResponse(message, HttpStatus.OK, responseData));
                
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error compressing moments for eventId {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse("Error compressing moments: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, null));
        } catch (Exception e) {
            logger.error("Unexpected error compressing moments for eventId {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse("Unexpected error: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }
}

