package com.moments.service;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.models.FaceTaggingResult;
import com.moments.models.Moment;

@Service
public class FaceTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(FaceTaggingService.class);

    @Autowired
    private CloseableHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${face.tagging.service.url}")
    private String faceTaggingServiceUrl;

    @Value("${face.tagging.service.timeout:30000}")
    private int timeout;

    @Value("${face.tagging.service.retry.attempts:3}")
    private int retryAttempts;

    @Value("${face.tagging.service.retry.delay:1000}")
    private int retryDelay;

    /**
     * Synchronously process selfie for face tagging
     * This method will retry on failure and log errors but won't throw exceptions
     * to ensure the main selfie upload process continues even if face tagging fails
     */
    public FaceTaggingResult processSelfieSync(String userId, String selfieUrl, String eventId) {
        logger.info("Starting sync selfie processing for user: {}, event: {}", userId, eventId);
        // Call the face tagging service, parse response, and extract relevant info, as
        // per the rules.
        FaceTaggingResult taggingResult = new FaceTaggingResult();
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_id", userId);
            requestBody.put("image_url", selfieUrl);
            requestBody.put("event_id", eventId);
            requestBody.put("face_matching", true);

            String jsonRequest = objectMapper.writeValueAsString(requestBody);

            HttpPost httpPost = new HttpPost(faceTaggingServiceUrl + "/api/v1/face-embeddings/selfie/process");
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonRequest));

            logger.info("Calling face tagging service for selfie processing: {}", jsonRequest);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                taggingResult.setRawResponse(responseBody);
                taggingResult.setHttpStatus(statusCode);

                // Default to failure unless proven otherwise
                taggingResult.setSuccess(false);

                if (statusCode >= 200 && statusCode < 300) {
                    logger.info("Selfie processing successful for user: {}, response: {}", userId, responseBody);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);

                    // 1. Parse face count
                    int faceCount = 0;
                    if (parsed.containsKey("face_count")) {
                        faceCount = ((Number) parsed.get("face_count")).intValue();
                    }
                    taggingResult.setFaceCount(faceCount);

                    // Rule 1: If no face detected
                    if (faceCount == 0) {
                        taggingResult.setSuccess(false);
                        taggingResult.setHttpStatus(400);
                        taggingResult.setMessage("No face detected! Please try again.");
                        taggingResult.setMatchCount(0);
                        taggingResult.setAverageQualityScore(null);
                        return taggingResult;
                    }

                    // 2. Parse quality_score, only first embedding
                    Double faceQualityScore = null;
                    if (parsed.containsKey("embeddings")) {
                        Object embeddingsObj = parsed.get("embeddings");
                        if (embeddingsObj instanceof java.util.List) {
                            java.util.List<?> embeddingsList = (java.util.List<?>) embeddingsObj;
                            if (!embeddingsList.isEmpty() && embeddingsList.get(0) instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> embMap = (Map<String, Object>) embeddingsList.get(0);
                                if (embMap.get("quality_score") != null) {
                                    try {
                                        faceQualityScore = ((Number) embMap.get("quality_score")).doubleValue();
                                    } catch (Exception e) {
                                        logger.warn("Error parsing quality_score: {}", e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    taggingResult.setAverageQualityScore(faceQualityScore);

                    // Rule 2: Face detected but quality too low
                    double QUALITY_THRESHOLD = 0.5;
                    if (faceQualityScore == null || faceQualityScore < QUALITY_THRESHOLD) {
                        taggingResult.setSuccess(false);
                        taggingResult.setHttpStatus(400);
                        taggingResult.setMessage("Ask user to use better lighting and background");
                        taggingResult.setMatchCount(0);
                        return taggingResult;
                    }

                    // 3. Parse matches list
                    int matchCount = 0;
                    if (parsed.containsKey("matches")) {
                        Object matchesObj = parsed.get("matches");
                        if (matchesObj instanceof java.util.List) {
                            matchCount = ((java.util.List<?>) matchesObj).size();
                        }
                    }
                    taggingResult.setMatchCount(matchCount);

                    // Rule 3: If 1 face detected, check matches
                    if (matchCount > 0) {
                        taggingResult.setSuccess(true);
                        taggingResult.setHttpStatus(200);
                        taggingResult.setMessage(matchCount + " moments found");
                    } else {
                        taggingResult.setSuccess(false);
                        taggingResult.setHttpStatus(400);
                        taggingResult.setMessage("No moments found");
                    }
                    return taggingResult;
                } else {
                    logger.warn("Selfie processing failed for user: {}, status: {}, response: {}",
                            userId, statusCode, responseBody);
                    taggingResult.setSuccess(false);
                    taggingResult.setHttpStatus(400);
                    taggingResult.setMessage("Please try again with a better selfie!");
                    taggingResult.setFaceCount(0);
                    taggingResult.setMatchCount(0);
                    taggingResult.setAverageQualityScore(null);
                    return taggingResult;
                }
            }

        } catch (Exception e) {
            logger.error("Error processing selfie for user: {}, error: {}", userId, e.getMessage(), e);
            taggingResult.setSuccess(false);
            taggingResult.setHttpStatus(500);
            taggingResult.setMessage("Internal server error during face tagging.");
            taggingResult.setFaceCount(0);
            taggingResult.setMatchCount(0);
            taggingResult.setAverageQualityScore(null);
            // Don't throw exception - fail silently to not break the main flow
            return taggingResult;
        }
    }

    /**
     * Asynchronously process moment for face tagging
     * This method runs in background and won't block the main moment creation
     * process
     */
    @Async
    public CompletableFuture<Void> processMomentAsync(String momentId, String imageUrl, String eventId) {
        logger.info("Starting async moment processing for moment: {}, event: {}", momentId, eventId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("moment_id", momentId);
            requestBody.put("image_url", imageUrl);
            requestBody.put("event_id", eventId);
            requestBody.put("match_faces", true);

            String jsonRequest = objectMapper.writeValueAsString(requestBody);

            HttpPost httpPost = new HttpPost(faceTaggingServiceUrl + "/api/v1/face-embeddings/moment/process");
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonRequest));

            logger.info("Calling face tagging service for moment processing: {}", jsonRequest);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode >= 200 && statusCode < 300) {
                    logger.info("Moment processing successful for moment: {}, response: {}", momentId, responseBody);
                } else {
                    logger.warn("Moment processing failed for moment: {}, status: {}, response: {}",
                            momentId, statusCode, responseBody);
                }
            }

        } catch (Exception e) {
            logger.error("Error processing moment for moment: {}, error: {}", momentId, e.getMessage(), e);
            // Don't throw exception - fail silently to not break the main flow
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Asynchronously process multiple moments in batch for face tagging
     * This method runs in background and won't block the main moment creation process
     */
    @Async
    public CompletableFuture<Void> processMomentsBatchAsync(List<Moment> moments) {
        logger.info("Starting async batch moment processing for {} moments", moments.size());

        try {
            if (moments == null || moments.isEmpty()) {
                logger.warn("Empty moments list provided for batch processing");
                return CompletableFuture.completedFuture(null);
            }

            // Prepare batch request
            Map<String, Object> batchRequest = new HashMap<>();
            List<Map<String, Object>> momentsList = new ArrayList<>();

            for (Moment moment : moments) {
                if (moment.getEventId() != null && !moment.getEventId().trim().isEmpty() &&
                        moment.getMedia() != null && moment.getMedia().getUrl() != null
                        && !moment.getMedia().getUrl().trim().isEmpty()) {

                    Map<String, Object> momentRequest = new HashMap<>();
                    momentRequest.put("moment_id", moment.getMomentId());
                    momentRequest.put("image_url", moment.getMedia().getUrl());
                    momentRequest.put("event_id", moment.getEventId());
                    momentRequest.put("match_faces", true);
                    
                    // user_id is optional, only add if available
                    if (moment.getCreatorId() != null && !moment.getCreatorId().trim().isEmpty()) {
                        momentRequest.put("user_id", moment.getCreatorId());
                    }

                    momentsList.add(momentRequest);
                } else {
                    logger.warn("Skipping moment {} due to missing eventId or imageUrl", moment.getMomentId());
                }
            }

            if (momentsList.isEmpty()) {
                logger.warn("No valid moments found for batch processing");
                return CompletableFuture.completedFuture(null);
            }

            batchRequest.put("moments", momentsList);
            String jsonRequest = objectMapper.writeValueAsString(batchRequest);

            HttpPost httpPost = new HttpPost(faceTaggingServiceUrl + "/api/v1/face-embeddings/moments/batch");
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonRequest));

            logger.info("Calling face tagging service for batch processing: {} moments", momentsList.size());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode >= 200 && statusCode < 300) {
                    logger.info("Batch moment processing successful for {} moments, response: {}", 
                            momentsList.size(), responseBody);
                } else {
                    logger.warn("Batch moment processing failed, status: {}, response: {}",
                            statusCode, responseBody);
                }
            }

        } catch (Exception e) {
            logger.error("Error processing moments batch, error: {}", e.getMessage(), e);
            // Don't throw exception - fail silently to not break the main flow
        }

        return CompletableFuture.completedFuture(null);
    }

}
