package com.moments.service;

import com.moments.dao.FaceEmbeddingDao;
import com.moments.models.FaceEmbedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class UserFaceEmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserFaceEmbeddingService.class);
    
    @Autowired
    private FaceEmbeddingDao faceEmbeddingDao;
    
    @Autowired
    private FaceRecognitionService faceRecognitionService;
    
    public String saveUserFaceEmbedding(String userId, MultipartFile imageFile) {
        try {
            // Extract face embedding from the selfie
            com.moments.models.FaceEmbeddingResult embeddingResult = faceRecognitionService.extractUserFaceEmbedding(imageFile);
            
            if (!embeddingResult.isSuccess() || embeddingResult.getEmbedding() == null) {
                logger.warn("Failed to extract face embedding from selfie for user: {}", userId);
                return null;
            }
            
            List<Double> embedding = embeddingResult.getEmbedding();
            
            // Create face embedding record
            FaceEmbedding faceEmbedding = new FaceEmbedding(
                userId, 
                null, // No moment ID for user selfie
                embedding, 
                "selfie_url", // This should be replaced with actual image URL
                "selfie_" + userId
            );
            
            String embeddingId = faceEmbeddingDao.saveFaceEmbedding(faceEmbedding);
            logger.info("Saved user face embedding for userId: {}, embeddingId: {}", userId, embeddingId);
            
            return embeddingId;
            
        } catch (Exception e) {
            logger.error("Error saving user face embedding for userId {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }
    
    public List<FaceEmbedding> getUserFaceEmbeddings(String userId) {
        try {
            return faceEmbeddingDao.getFaceEmbeddingsByUserId(userId);
        } catch (Exception e) {
            logger.error("Error getting user face embeddings for userId {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }
    
    public void deleteUserFaceEmbeddings(String userId) {
        try {
            List<FaceEmbedding> embeddings = getUserFaceEmbeddings(userId);
            for (FaceEmbedding embedding : embeddings) {
                faceEmbeddingDao.deleteFaceEmbedding(embedding.getEmbeddingId());
            }
            logger.info("Deleted all face embeddings for userId: {}", userId);
        } catch (Exception e) {
            logger.error("Error deleting user face embeddings for userId {}: {}", userId, e.getMessage(), e);
        }
    }
}
