package com.moments.service;

import com.moments.dao.MomentDao;
import com.moments.models.Media;
import com.moments.models.MediaType;
import com.moments.models.Moment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class MomentCompressionService {
    
    private static final Logger logger = LoggerFactory.getLogger(MomentCompressionService.class);
    
    @Autowired
    private MomentDao momentDao;
    
    @Autowired
    private GoogleCloudStorageService storageService;
    
    /**
     * Compresses all images for moments with the given eventId
     * @param eventId The event ID to process
     * @param quality Compression quality (0.0 to 1.0, where 1.0 is highest quality)
     * @return Compression statistics
     */
    public CompressionStatistics compressMomentsByEventId(String eventId, Float quality) 
            throws ExecutionException, InterruptedException {
        logger.info("Starting compression for eventId: {}", eventId);
        
        // Fetch all moments for this event
        List<Moment> moments = momentDao.getAllMoments(eventId);
        logger.info("Found {} moments for eventId: {}", moments.size(), eventId);
        
        CompressionStatistics stats = new CompressionStatistics();
        stats.setEventId(eventId);
        stats.setTotalMoments(moments.size());
        
        List<String> processedBlobs = new ArrayList<>();
        List<String> failedBlobs = new ArrayList<>();
        List<String> skippedBlobs = new ArrayList<>();
        int count =0;
        for (Moment moment : moments) {
            count++;
            Media media = moment.getMedia();
            if (media == null || media.getUrl() == null || media.getUrl().isEmpty()) {
                logger.warn("Moment {} has no media URL, skipping", moment.getMomentId());
                skippedBlobs.add(moment.getMomentId());
                stats.incrementSkipped();
                continue;
            }
            
            // Only process IMAGE type media
            if (media.getType() != MediaType.IMAGE) {
                logger.debug("Moment {} is not an image (type: {}), skipping", 
                    moment.getMomentId(), media.getType());
                skippedBlobs.add(moment.getMomentId());
                stats.incrementSkipped();
                continue;
            }
            
            String mediaUrl = media.getUrl();
            String blobName = storageService.extractBlobNameFromUrl(mediaUrl);
            
            if (blobName == null || blobName.isEmpty()) {
                logger.warn("Could not extract blob name from URL: {} for moment {}", 
                    mediaUrl, moment.getMomentId());
                failedBlobs.add(moment.getMomentId() + " (invalid URL)");
                stats.incrementFailed();
                continue;
            }
            
            try {
                logger.info("Compressing image for moment {}: {} with quality: {}", 
                    moment.getMomentId(), blobName, quality);
                GoogleCloudStorageService.CompressionResult result = 
                    storageService.compressExistingImage(blobName, quality);
                
                processedBlobs.add(blobName);
                stats.addCompressionResult(result);
                
                logger.info("Successfully compressed {}: {} bytes -> {} bytes (ratio: {}x)", 
                    blobName, result.getOriginalSize(), result.getCompressedSize(), 
                    String.format("%.2f", result.getCompressionRatio()));
                
            } catch (Exception e) {
                // Check if it's a "no size reduction" error or HEIC skip - treat as skipped, not failed
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                if (errorMsg.contains("did not reduce size enough") || 
                    errorMsg.contains("Could not achieve compression") ||
                    errorMsg.contains("compressed size would be larger") ||
                    errorMsg.contains("failed to reduce size") ||
                    errorMsg.contains("Skipping HEIC image")) {
                    logger.info("Skipping {} - {}", 
                        blobName, errorMsg.contains("HEIC") ? "HEIC format cannot be compressed" : "compression did not reduce size enough");
                    skippedBlobs.add(blobName);
                    stats.incrementSkipped();
                } else {
                    logger.error("Failed to convert image {} for moment {}: {}", 
                        blobName, moment.getMomentId(), errorMsg, e);
                    failedBlobs.add(blobName);
                    stats.incrementFailed();
                }
            }
        }
        
        stats.setProcessedBlobs(processedBlobs);
        stats.setFailedBlobs(failedBlobs);
        stats.setSkippedBlobs(skippedBlobs);
        
        logger.info("Compression completed for eventId: {}. Processed: {}, Failed: {}, Skipped: {}", 
            eventId, stats.getProcessedCount(), stats.getFailedCount(), stats.getSkippedCount());
        
        return stats;
    }
    
    /**
     * Statistics for compression operation
     */
    public static class CompressionStatistics {
        private String eventId;
        private int totalMoments;
        private int processedCount = 0;
        private int failedCount = 0;
        private int skippedCount = 0;
        private long totalOriginalSize = 0;
        private long totalCompressedSize = 0;
        private List<String> processedBlobs = new ArrayList<>();
        private List<String> failedBlobs = new ArrayList<>();
        private List<String> skippedBlobs = new ArrayList<>();
        
        public void addCompressionResult(GoogleCloudStorageService.CompressionResult result) {
            processedCount++;
            totalOriginalSize += result.getOriginalSize();
            totalCompressedSize += result.getCompressedSize();
        }
        
        public void incrementFailed() {
            failedCount++;
        }
        
        public void incrementSkipped() {
            skippedCount++;
        }
        
        public double getOverallCompressionRatio() {
            return totalCompressedSize > 0 ? (double) totalOriginalSize / totalCompressedSize : 1.0;
        }
        
        public long getTotalSizeReduction() {
            return totalOriginalSize - totalCompressedSize;
        }
        
        // Getters and Setters
        public String getEventId() {
            return eventId;
        }
        
        public void setEventId(String eventId) {
            this.eventId = eventId;
        }
        
        public int getTotalMoments() {
            return totalMoments;
        }
        
        public void setTotalMoments(int totalMoments) {
            this.totalMoments = totalMoments;
        }
        
        public int getProcessedCount() {
            return processedCount;
        }
        
        public int getFailedCount() {
            return failedCount;
        }
        
        public int getSkippedCount() {
            return skippedCount;
        }
        
        public long getTotalOriginalSize() {
            return totalOriginalSize;
        }
        
        public long getTotalCompressedSize() {
            return totalCompressedSize;
        }
        
        public List<String> getProcessedBlobs() {
            return processedBlobs;
        }
        
        public void setProcessedBlobs(List<String> processedBlobs) {
            this.processedBlobs = processedBlobs;
        }
        
        public List<String> getFailedBlobs() {
            return failedBlobs;
        }
        
        public void setFailedBlobs(List<String> failedBlobs) {
            this.failedBlobs = failedBlobs;
        }
        
        public List<String> getSkippedBlobs() {
            return skippedBlobs;
        }
        
        public void setSkippedBlobs(List<String> skippedBlobs) {
            this.skippedBlobs = skippedBlobs;
        }
    }
}

