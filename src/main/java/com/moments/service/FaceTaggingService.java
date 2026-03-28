package com.moments.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.dao.EventDao;
import com.moments.dao.MomentDao;
import com.moments.models.FaceTaggingResult;
import com.moments.models.Moment;
import com.moments.models.MomentMemoryUsage;

@Service
public class FaceTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(FaceTaggingService.class);

    @Autowired
    private CloseableHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MomentDao momentDao;

    @Autowired
    private EventDao eventDao;

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
     * Synchronous batch call to the face service (use during Cloud Run requests so CPU stays allocated).
     */
    public void processMomentsBatchSync(List<Moment> moments) {
        logger.info("Starting batch moment processing (sync) for {} moments", moments != null ? moments.size() : 0);
        try {
            if (moments == null || moments.isEmpty()) {
                logger.warn("Empty moments list provided for batch processing");
                return;
            }

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
                    momentsList.add(momentRequest);
                } else {
                    logger.warn("Skipping moment {} due to missing eventId or imageUrl", moment.getMomentId());
                }
            }

            if (momentsList.isEmpty()) {
                logger.warn("No valid moments found for batch processing");
                return;
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
                    applyBatchFaceTaggingStorageUpdates(responseBody);
                } else {
                    logger.warn("Batch moment processing failed, status: {}, response: {}",
                            statusCode, responseBody);
                }
            }

        } catch (Exception e) {
            logger.error("Error processing moments batch, error: {}", e.getMessage(), e);
        }
    }

    /**
     * Asynchronously process multiple moments in batch for face tagging
     * This method runs in background and won't block the main moment creation
     * process
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> processMomentsBatchAsync(List<Moment> moments) {
        processMomentsBatchSync(moments);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Persists optimised/thumbnail URLs and byte sizes returned by the face-tagging batch API.
     * Accepts several JSON shapes, e.g. {@code results}, {@code moments}, or {@code data} arrays
     * with objects containing {@code moment_id}, {@code feed_url} / {@code optimised_url},
     * {@code thumbnail_url}, and size fields such as {@code optimised_size_bytes},
     * {@code thumbnail_size_bytes}.
     */
    private void applyBatchFaceTaggingStorageUpdates(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode arr = pickResultsArray(root);
            if (arr == null || !arr.isArray()) {
                return;
            }
            for (JsonNode item : arr) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String momentId = textField(item, "moment_id", "momentId", "id");
                if (momentId == null || momentId.isBlank()) {
                    continue;
                }
                String feedUrl = textField(item, "feed_url", "feedUrl", "optimised_url", "optimisedUrl",
                        "optimized_url", "optimizedUrl");
                String thumbnailUrl = textField(item, "thumbnail_url", "thumbnailUrl");
                Long optimisedSize = longField(item, "optimised_size_bytes", "optimisedImageSizeBytes",
                        "optimized_size_bytes", "optimisedSizeBytes", "optimizedSizeBytes");
                Long thumbnailSize = longField(item, "thumbnail_size_bytes", "thumbnailImageSizeBytes",
                        "thumbnailSizeBytes");
                if (feedUrl == null && thumbnailUrl == null && optimisedSize == null && thumbnailSize == null) {
                    continue;
                }
                Moment before = null;
                try {
                    before = momentDao.getMomentById(momentId);
                } catch (Exception ignored) {
                    // moment missing — skip aggregate update
                }
                try {
                    momentDao.updateMomentFaceTaggingStorage(momentId, feedUrl, thumbnailUrl, optimisedSize,
                            thumbnailSize);
                    if (before != null && before.getEventId() != null && !before.getEventId().isBlank()) {
                        MomentMemoryUsage u = before.getMemoryUsage();
                        long oldOpt = u == null ? 0L : nz(u.getOptimisedSizeBytes());
                        long oldTh = u == null ? 0L : nz(u.getThumbnailSizeBytes());
                        long newOpt = optimisedSize != null ? optimisedSize : oldOpt;
                        long newTh = thumbnailSize != null ? thumbnailSize : oldTh;
                        long dOpt = newOpt - oldOpt;
                        long dTh = newTh - oldTh;
                        if (dOpt != 0L || dTh != 0L) {
                            try {
                                eventDao.adjustAggregatedStorage(before.getEventId(), 0L, dOpt, dTh);
                            } catch (Exception ex) {
                                logger.warn("Event aggregate adjust after face-tagging failed: {}", ex.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Face-tagging storage update interrupted for {}: {}", momentId, e.getMessage());
                } catch (ExecutionException e) {
                    logger.warn("Face-tagging storage update failed for {}: {}", momentId, e.getMessage());
                } catch (RuntimeException e) {
                    logger.debug("Face-tagging storage update skipped for {}: {}", momentId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse face-tagging batch response for storage fields: {}", e.getMessage());
        }
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static JsonNode pickResultsArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        for (String key : new String[] { "results", "moments", "data", "processed_moments", "processedMoments" }) {
            JsonNode n = root.get(key);
            if (n != null && n.isArray()) {
                return n;
            }
        }
        if (root.isArray()) {
            return root;
        }
        return null;
    }

    private static String textField(JsonNode item, String... keys) {
        for (String k : keys) {
            JsonNode n = item.get(k);
            if (n != null && n.isTextual()) {
                String v = n.asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Long longField(JsonNode item, String... keys) {
        for (String k : keys) {
            if (!item.has(k) || item.get(k).isNull()) {
                continue;
            }
            JsonNode n = item.get(k);
            if (n.isNumber()) {
                return n.longValue();
            }
            if (n.isTextual()) {
                try {
                    return Long.parseLong(n.asText().trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

}
