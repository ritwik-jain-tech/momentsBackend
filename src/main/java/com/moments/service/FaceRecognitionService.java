package com.moments.service;

import com.moments.models.FaceEmbeddingResponse;
import com.moments.models.FaceEmbeddingResult;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_java;
import org.bytedeco.opencv.opencv_objdetect.*;
import org.bytedeco.opencv.opencv_face.*;
import org.bytedeco.opencv.opencv_dnn.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;

import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_dnn.*;

@Service
public class FaceRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionService.class);
    private static final double FACE_MATCH_THRESHOLD = 0.6;
    private static final double MIN_FACE_SIZE_RATIO = 0.05;
    private static final double MAX_FACE_SIZE_RATIO = 0.8;
    private static final int MIN_NEIGHBORS = 3;
    private static final double SCALE_FACTOR = 1.1;
    
    // Balanced parameters for group image
    private static final double GROUP_MIN_FACE_SIZE_RATIO = 0.08;
    private static final double GROUP_MAX_FACE_SIZE_RATIO = 0.6;
    private static final int GROUP_MIN_NEIGHBORS = 4;
    private CascadeClassifier faceDetector;
    private Net faceNet;
    private Size inputSize;
    private Scalar mean;
    private double scale;
    
    @Value("${face.recognition.enabled:true}")
    private boolean faceRecognitionEnabled;

    @Autowired(required = false)
    private CloseableHttpClient httpClient;

    public FaceRecognitionService() {
        // Constructor will be called, but initialization will be conditional
    }
    
    @PostConstruct
    public void initialize() {
        if (!faceRecognitionEnabled) {
            logger.info("Face recognition is disabled. Skipping initialization.");
            return;
        }
        
        try {
            logger.info("Initializing FaceRecognitionService...");
            // Load OpenCV native library
            Loader.load(opencv_java.class);
            logger.debug("OpenCV native library loaded successfully");
            
            // Initialize face detector
            faceDetector = new CascadeClassifier();
            String cascadePath = getClass().getResource("/opencv/haarcascade_frontalface_default.xml").getPath();
            logger.debug("Loading cascade classifier from: {}", cascadePath);
            
            if (!faceDetector.load(cascadePath)) {
                String error = "Failed to load face cascade classifier from: " + cascadePath;
                logger.error(error);
                throw new RuntimeException(error);
            }
            logger.info("Face cascade classifier loaded successfully");

            // Initialize deep learning model
            String modelPath = getClass().getResource("/models/face_recognition_model.onnx").getPath();
            faceNet = readNetFromONNX(modelPath);
            if (faceNet.empty()) {
                throw new RuntimeException("Failed to load face recognition model");
            }
            
            // Set model parameters for SFace model
            inputSize = new Size(112, 112);
            // SFace model doesn't require mean subtraction or scaling
            mean = new Scalar(0, 0, 0, 0);
            scale = 1.0;
            
            logger.info("Face recognition model loaded successfully");
        } catch (Exception e) {
            logger.error("Error initializing FaceRecognitionService: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize face recognition service", e);
        }
    }

    public FaceEmbeddingResult extractUserFaceEmbedding(MultipartFile imageFile) {
            if (!faceRecognitionEnabled) {
                logger.warn("Face recognition is disabled. Skipping face embedding extraction.");
                return new FaceEmbeddingResult(false, "Face recognition is disabled", null, 0);
            }

            logger.info("Extracting face embedding from user selfie");

            try {
                // Save uploaded file to temporary location
                java.io.File tempFile = java.nio.file.Files.createTempFile("face_recognition_", ".jpg").toFile();
                java.nio.file.Files.write(tempFile.toPath(), imageFile.getBytes());

                try {
                    // Detect faces in the selfie
                    List<Mat> faces = detectFaces(tempFile);

                    if (faces.isEmpty()) {
                        logger.info("No faces detected in selfie image");
                        return new com.moments.models.FaceEmbeddingResult(true, "No faces detected in the selfie", null, 0);
                    }

                    // Extract face embedding from the first (and usually only) face in selfie
                    Mat face = faces.get(0);
                    Mat embedding = extractFaceEmbedding(face);

                    // Convert Mat to List<Double>
                    List<Double> embeddingList = new java.util.ArrayList<>();
                    org.bytedeco.javacpp.FloatPointer floatPointer = new org.bytedeco.javacpp.FloatPointer(embedding);
                    for (int i = 0; i < embedding.total(); i++) {
                        embeddingList.add((double) floatPointer.get(i));
                    }

                    logger.info("Successfully extracted face embedding from selfie: {} dimensions", embeddingList.size());

                    return new FaceEmbeddingResult(
                        true,
                        "Successfully extracted face embedding",
                        embeddingList,
                        faces.size()
                    );

                } finally {
                    // Clean up temporary file
                    java.nio.file.Files.deleteIfExists(tempFile.toPath());
                }
            } catch (Exception e) {
                logger.error("Error extracting face embedding from selfie: {}", e.getMessage(), e);
                return new com.moments.models.FaceEmbeddingResult(false, "Error extracting embedding: " + e.getMessage(), null, 0);
            }
        }

    private Mat extractFaceEmbedding(Mat face) {
        try {
            // Convert grayscale to BGR if needed
            Mat bgrFace = new Mat();
            if (face.channels() == 1) {
                cvtColor(face, bgrFace, COLOR_GRAY2BGR);
            } else {
                face.copyTo(bgrFace);
            }
            
            // Resize to input size (112x112 for SFace model)
            Mat resizedFace = new Mat();
            resize(bgrFace, resizedFace, inputSize);
            
            // Convert to blob format expected by the model
            Mat blob = blobFromImage(resizedFace, 1.0, inputSize, new Scalar(0.0, 0.0, 0.0, 0.0), true, false, CV_32F);
            
            // Set input and run forward pass
            faceNet.setInput(blob);
            Mat embeddings = faceNet.forward();
            
            // Normalize embeddings
            Mat normalizedEmbeddings = new Mat();
            normalize(embeddings, normalizedEmbeddings, 1.0, 0.0, NORM_L2, -1, new Mat());
            
            return normalizedEmbeddings;
        } catch (Exception e) {
            logger.error("Error extracting face embedding: {}", e.getMessage(), e);
            throw e;
        }
    }

    private double calculateSimilarity(Mat embedding1, Mat embedding2) {
        // Calculate cosine similarity
        return embedding1.dot(embedding2);
    }

    private boolean compareFaces(Mat face1, Mat face2) {
        logger.debug("Comparing two faces using deep learning model");
        try {
            // Extract face embeddings
            Mat embedding1 = extractFaceEmbedding(face1);
            Mat embedding2 = extractFaceEmbedding(face2);
            
            // Calculate similarity
            double similarity = calculateSimilarity(embedding1, embedding2);
            logger.info("Face similarity score: {}", similarity);
            
            return similarity > FACE_MATCH_THRESHOLD;
        } catch (Exception e) {
            logger.error("Error comparing faces: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean isPersonInImage(String personImageUrl, String groupImageUrl) throws IOException {
        if (!faceRecognitionEnabled) {
            logger.warn("Face recognition is disabled. Returning false for face recognition request.");
            return false;
        }
        
        logger.info("Starting face recognition process for person image: {} and group image: {}", 
            personImageUrl, groupImageUrl);
        
        try {
            // Download images from URLs
            logger.debug("Downloading person image from: {}", personImageUrl);
            byte[] personImageBytes = downloadImage(personImageUrl);
            logger.debug("Downloading group image from: {}", groupImageUrl);
            byte[] groupImageBytes = downloadImage(groupImageUrl);

            // Save images to temporary files
            Path personImagePath = saveTemporaryFile(personImageBytes, "person");
            Path groupImagePath = saveTemporaryFile(groupImageBytes, "group");
            logger.debug("Temporary files created: {} and {}", personImagePath, groupImagePath);

            try {
                // Detect and extract face from person image
                logger.debug("Detecting face in person image");
                Mat personFace = detectAndExtractFace(personImagePath.toFile());
                if (personFace == null) {
                    String error = "No face detected in the person image";
                    logger.error(error);
                    throw new RuntimeException(error);
                }
                logger.debug("Face detected in person image");

                // Detect faces in group image
                logger.debug("Detecting faces in group image");
                List<Mat> groupFaces = detectFaces(groupImagePath.toFile());
                if (groupFaces.isEmpty()) {
                    String error = "No faces detected in the group image";
                    logger.error(error);
                    throw new RuntimeException(error);
                }
                logger.info("Found {} faces in group image", groupFaces.size());

                // Compare person's face with each face in the group
                for (int i = 0; i < groupFaces.size(); i++) {
                    logger.debug("Comparing person face with group face {}", i + 1);
                    if (compareFaces(personFace, groupFaces.get(i))) {
                        logger.info("Match found! Person is in the group image");
                        return true;
                    }
                }

                logger.info("No match found. Person is not in the group image");
                return false;
            } finally {
                // Clean up temporary files
                logger.debug("Cleaning up temporary files");
                Files.deleteIfExists(personImagePath);
                Files.deleteIfExists(groupImagePath);
            }
        } catch (Exception e) {
            logger.error("Error during face recognition process: {}", e.getMessage(), e);
            throw e;
        }
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        if (httpClient == null) {
            throw new IOException("HttpClient is not available. Face recognition may be disabled.");
        }
        
        logger.debug("Downloading image from URL: {}", imageUrl);
        HttpGet request = new HttpGet(imageUrl);
        try {
            HttpEntity entity = httpClient.execute(request).getEntity();
            if (entity != null) {
                byte[] imageBytes = EntityUtils.toByteArray(entity);
                logger.debug("Successfully downloaded image, size: {} bytes", imageBytes.length);
                return imageBytes;
            }
            String error = "Failed to download image from URL: " + imageUrl;
            logger.error(error);
            throw new IOException(error);
        } catch (Exception e) {
            logger.error("Error downloading image from {}: {}", imageUrl, e.getMessage(), e);
            throw e;
        } finally {
            request.releaseConnection();
        }
    }

    private Path saveTemporaryFile(byte[] imageBytes, String prefix) throws IOException {
        try {
            Path tempFile = Files.createTempFile("face_recognition_" + prefix + "_", ".jpg");
            Files.write(tempFile, imageBytes);
            logger.debug("Created temporary file: {}", tempFile);
            return tempFile;
        } catch (IOException e) {
            logger.error("Error creating temporary file: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Mat detectAndExtractFace(File imageFile) {
        logger.debug("Detecting face in file: {}", imageFile.getAbsolutePath());
        Mat image = imread(imageFile.getAbsolutePath());
        if (image.empty()) {
            logger.error("Failed to read image file: {}", imageFile.getAbsolutePath());
            return null;
        }

        // Convert to grayscale
        Mat gray = new Mat();
        cvtColor(image, gray, COLOR_BGR2GRAY);

        // Calculate minimum and maximum face sizes with more lenient parameters
        int minFaceSize = (int) (Math.min(image.rows(), image.cols()) * MIN_FACE_SIZE_RATIO);
        int maxFaceSize = (int) (Math.min(image.rows(), image.cols()) * MAX_FACE_SIZE_RATIO);
        
        logger.debug("Face detection parameters - minSize: {}, maxSize: {}, minNeighbors: {}, scaleFactor: {}", 
            minFaceSize, maxFaceSize, MIN_NEIGHBORS, SCALE_FACTOR);
        
        // Detect faces with more lenient parameters
        RectVector faces = new RectVector();
        faceDetector.detectMultiScale(gray, faces, SCALE_FACTOR, MIN_NEIGHBORS, 0, 
            new Size(minFaceSize, minFaceSize), new Size(maxFaceSize, maxFaceSize));

        if (faces.size() > 0) {
            // Get the largest face (usually the most prominent one)
            Rect largestFace = null;
            double maxArea = 0;
            
            for (int i = 0; i < faces.size(); i++) {
                Rect face = faces.get(i);
                double area = face.width() * face.height();
                if (area > maxArea) {
                    maxArea = area;
                    largestFace = face;
                }
            }
            
            if (largestFace != null) {
                Mat faceROI = new Mat(gray, largestFace);
                logger.debug("Face detected at position: x={}, y={}, width={}, height={}, area={}", 
                    largestFace.x(), largestFace.y(), largestFace.width(), largestFace.height(), maxArea);
                return faceROI;
            }
        }

        logger.warn("No face detected in image: {}", imageFile.getAbsolutePath());
        return null;
    }

    private List<Mat> detectFaces(File imageFile) {
        logger.debug("Detecting multiple faces in file: {}", imageFile.getAbsolutePath());
        List<Mat> faces = new ArrayList<>();
        Mat image = imread(imageFile.getAbsolutePath());
        if (image.empty()) {
            logger.error("Failed to read image file: {}", imageFile.getAbsolutePath());
            return faces;
        }

        // Convert to grayscale
        Mat gray = new Mat();
        cvtColor(image, gray, COLOR_BGR2GRAY);

        // Calculate minimum and maximum face sizes with balanced parameters for group image
        int minFaceSize = (int) (Math.min(image.rows(), image.cols()) * GROUP_MIN_FACE_SIZE_RATIO);
        int maxFaceSize = (int) (Math.min(image.rows(), image.cols()) * GROUP_MAX_FACE_SIZE_RATIO);
        
        logger.debug("Group face detection parameters - minSize: {}, maxSize: {}, minNeighbors: {}, scaleFactor: {}", 
            minFaceSize, maxFaceSize, GROUP_MIN_NEIGHBORS, SCALE_FACTOR);
        
        // Try multiple detection attempts with different parameters if needed
        RectVector detectedFaces = new RectVector();
        faceDetector.detectMultiScale(gray, detectedFaces, SCALE_FACTOR, GROUP_MIN_NEIGHBORS, 0,
            new Size(minFaceSize, minFaceSize), new Size(maxFaceSize, maxFaceSize));

        // If no faces detected, try with more lenient parameters
        if (detectedFaces.size() == 0) {
            logger.debug("No faces detected with standard parameters, trying with more lenient parameters");
            int moreLenientMinSize = (int) (minFaceSize * 0.8);
            int moreLenientMaxSize = (int) (maxFaceSize * 1.2);
            faceDetector.detectMultiScale(gray, detectedFaces, SCALE_FACTOR, GROUP_MIN_NEIGHBORS - 1, 0,
                new Size(moreLenientMinSize, moreLenientMinSize), new Size(moreLenientMaxSize, moreLenientMaxSize));
        }

        // Extract each face and validate
        for (int i = 0; i < detectedFaces.size(); i++) {
            Rect face = detectedFaces.get(i);
            double area = face.width() * face.height();
            double imageArea = image.rows() * image.cols();
            double faceRatio = area / imageArea;
            
            // More lenient size validation for group images
            if (faceRatio >= (GROUP_MIN_FACE_SIZE_RATIO * 0.8) * (GROUP_MIN_FACE_SIZE_RATIO * 0.8) && 
                faceRatio <= (GROUP_MAX_FACE_SIZE_RATIO * 1.2) * (GROUP_MAX_FACE_SIZE_RATIO * 1.2)) {
                Mat faceROI = new Mat(gray, face);
                faces.add(faceROI);
                logger.debug("Face {} detected at position: x={}, y={}, width={}, height={}, ratio={}", 
                    i + 1, face.x(), face.y(), face.width(), face.height(), faceRatio);
            } else {
                logger.debug("Skipping face {} due to invalid size ratio: {}", i + 1, faceRatio);
            }
        }

        logger.info("Detected {} valid faces in image: {}", faces.size(), imageFile.getAbsolutePath());
        return faces;
    }


    public FaceEmbeddingResponse processMomentImage(MultipartFile imageFile, String momentId) {
        if (!faceRecognitionEnabled) {
            logger.warn("Face recognition is disabled. Skipping face processing.");
            return new com.moments.models.FaceEmbeddingResponse(false, "Face recognition is disabled", new ArrayList<>(), 0, 0);
        }

        logger.info("Processing moment image for momentId: {}", momentId);
        
        try {
            // Save uploaded file to temporary location
            java.io.File tempFile = java.nio.file.Files.createTempFile("face_recognition_", ".jpg").toFile();
            java.nio.file.Files.write(tempFile.toPath(), imageFile.getBytes());
            
            try {
                // Detect faces in the image
                List<Mat> faces = detectFaces(tempFile);
                
                if (faces.isEmpty()) {
                    logger.info("No faces detected in moment image");
                    return new com.moments.models.FaceEmbeddingResponse(true, "No faces detected in the image", new ArrayList<>(), 0, 0);
                }

                List<String> taggedUserIds = new ArrayList<>();
                int facesMatched = 0;

                // Process each detected face
                for (int i = 0; i < faces.size(); i++) {
                    Mat face = faces.get(i);
                    String faceId = java.util.UUID.randomUUID().toString();
                    
                    // Extract face embedding
                    Mat embedding = extractFaceEmbedding(face);
                    
                    // Convert Mat to List<Double>
                    List<Double> embeddingList = new java.util.ArrayList<>();
                    org.bytedeco.javacpp.FloatPointer floatPointer = new org.bytedeco.javacpp.FloatPointer(embedding);
                    for (int j = 0; j < embedding.total(); j++) {
                        embeddingList.add((double) floatPointer.get(j));
                    }
                    
                    // For now, we'll just return empty tagged users since we don't have the DAO integration
                    // In a full implementation, you would search for matching users here
                    logger.debug("Extracted face embedding for face {}: {} dimensions", i + 1, embeddingList.size());
                }

                logger.info("Processed moment image: {} faces detected, {} faces matched to users", 
                    faces.size(), facesMatched);

                return new com.moments.models.FaceEmbeddingResponse(
                    true, 
                    "Successfully processed moment image", 
                    taggedUserIds, 
                    faces.size(), 
                    facesMatched
                );

            } finally {
                // Clean up temporary file
                java.nio.file.Files.deleteIfExists(tempFile.toPath());
            }
        } catch (Exception e) {
            logger.error("Error processing moment image: {}", e.getMessage(), e);
            return new com.moments.models.FaceEmbeddingResponse(false, "Error processing image: " + e.getMessage(), new ArrayList<>(), 0, 0);
        }
    }

    public FaceEmbeddingResponse processSelfieImage(MultipartFile imageFile, String userId) {
        if (!faceRecognitionEnabled) {
            logger.warn("Face recognition is disabled. Skipping face processing.");
            return new FaceEmbeddingResponse(false, "Face recognition is disabled", new ArrayList<>(), 0, 0);
        }

        logger.info("Processing selfie image for userId: {}", userId);
        try {
            java.io.File tempFile = java.nio.file.Files.createTempFile("face_recognition_", ".jpg").toFile();
            java.nio.file.Files.write(tempFile.toPath(), imageFile.getBytes());
            try {
                List<Mat> faces = detectFaces(tempFile);
                if (faces.isEmpty()) {
                    logger.info("No faces detected in selfie image");
                    return new FaceEmbeddingResponse(true, "No faces detected in the selfie", new ArrayList<>(), 0, 0);
                }
                Mat face = faces.get(0);
                Mat embedding = extractFaceEmbedding(face);
                List<Double> embeddingList = new java.util.ArrayList<>();
                org.bytedeco.javacpp.FloatPointer floatPointer = new org.bytedeco.javacpp.FloatPointer(embedding);
                for (int i = 0; i < embedding.total(); i++) {
                    embeddingList.add((double) floatPointer.get(i));
                }
                // TODO: match embedding against stored user/moment embeddings
                List<String> matchedMomentIds = new ArrayList<>();
                logger.info("Processed selfie: {} faces detected, {} moments matched", faces.size(), matchedMomentIds.size());
                return new FaceEmbeddingResponse(true, "Successfully processed selfie image", matchedMomentIds, faces.size(), matchedMomentIds.size());
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile.toPath());
            }
        } catch (Exception e) {
            logger.error("Error processing selfie image: {}", e.getMessage(), e);
            return new FaceEmbeddingResponse(false, "Error processing selfie: " + e.getMessage(), new ArrayList<>(), 0, 0);
        }
    }
}
