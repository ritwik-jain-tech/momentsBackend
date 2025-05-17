package com.moments.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.moments.models.Moment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
public class FacialRecognitionService {

    @Autowired
    private MomentService momentService;

    @Autowired
    private GoogleCloudStorageService storageService;

    private final ConcurrentHashMap<String, CompletableFuture<List<Moment>>> searchResults = new ConcurrentHashMap<>();

    public String uploadImage(MultipartFile image) throws IOException {
        // Upload the image to Google Cloud Storage
        return storageService.uploadFile(image, FileType.IMAGE).getPublicUrl();
    }

    public CompletableFuture<List<Moment>> findSimilarFaces(String eventId, String imageUrl) {
        String searchId = generateSearchId(eventId, imageUrl);
        CompletableFuture<List<Moment>> future = CompletableFuture.supplyAsync(() -> {
            try {
                List<Moment> allMoments = momentService.getAllMoments();
                List<Moment> matchingMoments = new ArrayList<>();
                
                // Get face features from the input image
                List<FaceAnnotation> inputFaces = detectFaces(imageUrl);
                
                for (Moment moment : allMoments) {
                    if (!moment.getEventId().equals(eventId)) {
                        continue;
                    }
                    
                    List<FaceAnnotation> momentFaces = detectFaces(moment.getImageUrl());
                    if (hasMatchingFaces(inputFaces, momentFaces)) {
                        matchingMoments.add(moment);
                    }
                }
                
                return matchingMoments;
            } catch (Exception e) {
                throw new RuntimeException("Error in facial recognition: " + e.getMessage());
            }
        });
        
        searchResults.put(searchId, future);
        return future;
    }

    public List<Moment> getSearchResults(String eventId, String imageId) throws ExecutionException, InterruptedException {
        String searchId = generateSearchId(eventId, imageId);
        CompletableFuture<List<Moment>> future = searchResults.get(searchId);
        
        if (future == null) {
            throw new RuntimeException("Search results not found");
        }
        
        if (future.isDone()) {
            return future.get();
        } else {
            throw new RuntimeException("Search is still in progress");
        }
    }

    private String generateSearchId(String eventId, String imageId) {
        return eventId + "_" + imageId;
    }

    private List<FaceAnnotation> detectFaces(String imageUrl) throws IOException {
        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            // Download image from GCS
            byte[] imageBytes = storageService.downloadFile(imageUrl);
            
            // Create the image
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            
            // Perform face detection
            List<AnnotateImageRequest> requests = new ArrayList<>();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(Feature.newBuilder().setType(Feature.Type.FACE_DETECTION))
                    .setImage(img)
                    .build();
            requests.add(request);

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (responses.isEmpty()) {
                return new ArrayList<>();
            }

            return responses.get(0).getFaceAnnotationsList();
        }
    }

    private boolean hasMatchingFaces(List<FaceAnnotation> inputFaces, List<FaceAnnotation> momentFaces) {
        // Simple matching logic - can be enhanced with more sophisticated algorithms
        if (inputFaces.isEmpty() || momentFaces.isEmpty()) {
            return false;
        }

        // Compare each face in input with each face in moment
        for (FaceAnnotation inputFace : inputFaces) {
            for (FaceAnnotation momentFace : momentFaces) {
                if (isSimilarFace(inputFace, momentFace)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSimilarFace(FaceAnnotation face1, FaceAnnotation face2) {
        // Implement face similarity logic
        // This is a simplified version - you might want to use more sophisticated algorithms
        // like face embeddings and cosine similarity
        
        // Compare facial landmarks
        float similarityScore = calculateFaceSimilarity(face1, face2);
        return similarityScore > 0.8; // Threshold can be adjusted
    }

    private float calculateFaceSimilarity(FaceAnnotation face1, FaceAnnotation face2) {
        // Implement actual face similarity calculation
        // This is a placeholder - you should implement proper face comparison logic
        return 0.9f;
    }
} 