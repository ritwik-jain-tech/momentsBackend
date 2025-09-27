package com.moments.dao.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.moments.dao.FaceEmbeddingDao;
import com.moments.models.FaceEmbedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
public class FaceEmbeddingDaoImpl implements FaceEmbeddingDao {

    @Autowired
    private Firestore firestore;

    private static final String COLLECTION_NAME = "face_embeddings";

    @Override
    public String saveFaceEmbedding(FaceEmbedding faceEmbedding) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = faceEmbedding.getEmbeddingId() == null || faceEmbedding.getEmbeddingId().isEmpty()
                ? firestore.collection(COLLECTION_NAME).document()
                : firestore.collection(COLLECTION_NAME).document(faceEmbedding.getEmbeddingId());

        if (faceEmbedding.getEmbeddingId() == null || faceEmbedding.getEmbeddingId().isEmpty()) {
            faceEmbedding.setEmbeddingId(documentReference.getId());
        }

        ApiFuture<WriteResult> future = documentReference.set(faceEmbedding);
        future.get();

        return faceEmbedding.getEmbeddingId();
    }

    @Override
    public List<FaceEmbedding> getFaceEmbeddingsByUserId(String userId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId);
        
        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot querySnapshot = future.get();
        
        List<FaceEmbedding> faceEmbeddings = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            faceEmbeddings.add(document.toObject(FaceEmbedding.class));
        }
        
        return faceEmbeddings;
    }

    @Override
    public List<FaceEmbedding> getFaceEmbeddingsByMomentId(String momentId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("momentId", momentId);
        
        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot querySnapshot = future.get();
        
        List<FaceEmbedding> faceEmbeddings = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            faceEmbeddings.add(document.toObject(FaceEmbedding.class));
        }
        
        return faceEmbeddings;
    }

    @Override
    public List<FaceEmbedding> getAllFaceEmbeddings() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
        QuerySnapshot querySnapshot = future.get();
        
        List<FaceEmbedding> faceEmbeddings = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            faceEmbeddings.add(document.toObject(FaceEmbedding.class));
        }
        
        return faceEmbeddings;
    }

    @Override
    public void deleteFaceEmbedding(String embeddingId) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection(COLLECTION_NAME).document(embeddingId);
        ApiFuture<WriteResult> future = documentReference.delete();
        future.get();
    }

    @Override
    public void deleteFaceEmbeddingsByMomentId(String momentId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("momentId", momentId);
        
        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot querySnapshot = future.get();
        
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            document.getReference().delete();
        }
    }
}
