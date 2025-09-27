package com.moments.dao;

import com.moments.models.FaceEmbedding;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface FaceEmbeddingDao {
    String saveFaceEmbedding(FaceEmbedding faceEmbedding) throws ExecutionException, InterruptedException;
    List<FaceEmbedding> getFaceEmbeddingsByUserId(String userId) throws ExecutionException, InterruptedException;
    List<FaceEmbedding> getFaceEmbeddingsByMomentId(String momentId) throws ExecutionException, InterruptedException;
    List<FaceEmbedding> getAllFaceEmbeddings() throws ExecutionException, InterruptedException;
    void deleteFaceEmbedding(String embeddingId) throws ExecutionException, InterruptedException;
    void deleteFaceEmbeddingsByMomentId(String momentId) throws ExecutionException, InterruptedException;
}
