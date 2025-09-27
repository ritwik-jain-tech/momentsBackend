# Face Recognition System Documentation

## Overview

The Moments Backend now includes a comprehensive face recognition system that uses vector embeddings to automatically tag users in moments. This system replaces the previous URL-based face recognition approach with a more efficient and scalable solution.

## Architecture

### Core Components

1. **FaceRecognitionService** - Main service for face detection and embedding extraction
2. **FaceEmbeddingDao** - Data access layer for face embeddings in Firestore
3. **UserFaceEmbeddingService** - Service for managing user face embeddings
4. **FaceEmbeddingController** - REST API for face embedding operations
5. **SelfieController** - REST API for selfie uploads and user face registration

### Data Models

- **FaceEmbedding** - Stores face vector embeddings in Firestore
- **FaceEmbeddingRequest/Response** - API request/response models
- **FaceEmbeddingResult** - Result model for embedding extraction

## How It Works

### 1. User Selfie Upload
When a user uploads a selfie:
1. The image is processed to detect faces
2. Face embeddings are extracted using a deep learning model
3. Embeddings are stored in Firestore with user ID
4. This creates a "face signature" for the user

### 2. Moment Upload with Face Recognition
When a moment is uploaded:
1. The image is processed to detect all faces
2. Face embeddings are extracted for each detected face
3. The system searches existing user face embeddings
4. Matching users are automatically tagged in the moment
5. Face embeddings are stored for future matching

### 3. Face Matching Process
- Uses cosine similarity to compare face embeddings
- Threshold of 0.6 for face matching
- Supports multiple faces per image
- Handles both single person and group photos

## API Endpoints

### Selfie Upload
```
POST /api/selfie/upload
Content-Type: multipart/form-data

Parameters:
- imageFile: MultipartFile (required)
- userId: String (required)

Response:
{
  "message": "Selfie uploaded and face embedding saved successfully",
  "status": 200,
  "data": {
    "embeddingId": "unique_embedding_id",
    "userId": "user_id"
  }
}
```

### Moment Upload with Face Recognition
```
POST /api/moments/with-face-recognition
Content-Type: multipart/form-data

Parameters:
- imageFile: MultipartFile (required)
- moment: String (JSON string of Moment object)

Response:
{
  "message": "Created moment with face recognition, Id: moment_id",
  "status": 200,
  "data": {
    "momentId": "moment_id",
    "taggedUserIds": ["user1", "user2", ...]
  }
}
```

### Face Embedding Processing
```
POST /api/face-embedding/process-moment
Content-Type: multipart/form-data

Parameters:
- imageFile: MultipartFile (required)
- momentId: String (required)

Response:
{
  "message": "Successfully processed moment image",
  "status": 200,
  "data": {
    "success": true,
    "message": "Successfully processed moment image",
    "taggedUserIds": ["user1", "user2"],
    "facesDetected": 3,
    "facesMatched": 2
  }
}
```

## Configuration

### Application Properties
```properties
# Enable/disable face recognition
face.recognition.enabled=true

# Face matching threshold (0.0 to 1.0)
face.recognition.threshold=0.6

# Minimum face size ratio
face.recognition.min.face.size.ratio=0.05

# Maximum face size ratio
face.recognition.max.face.size.ratio=0.8
```

## Database Schema

### Firestore Collection: face_embeddings
```json
{
  "embeddingId": "unique_id",
  "userId": "user_id",
  "momentId": "moment_id_or_null",
  "embedding": [0.1, 0.2, 0.3, ...], // 512-dimensional vector
  "createdAt": 1234567890,
  "imageUrl": "image_url",
  "faceId": "unique_face_id"
}
```

## Performance Considerations

1. **Embedding Storage**: Face embeddings are stored as arrays of doubles in Firestore
2. **Similarity Calculation**: Uses cosine similarity for efficient comparison
3. **Batch Processing**: Supports processing multiple faces per image
4. **Caching**: Consider implementing caching for frequently accessed embeddings

## Error Handling

The system includes comprehensive error handling:
- Graceful degradation when face recognition is disabled
- Continues moment creation even if face recognition fails
- Detailed logging for debugging
- Validation of input parameters

## Security Considerations

1. **Data Privacy**: Face embeddings are stored securely in Firestore
2. **Access Control**: Ensure proper authentication for all endpoints
3. **Data Retention**: Consider implementing data retention policies
4. **GDPR Compliance**: Users should be able to delete their face data

## Future Enhancements

1. **Batch Processing**: Process multiple images simultaneously
2. **Face Clustering**: Group similar faces for better organization
3. **Confidence Scores**: Return confidence scores for face matches
4. **Face Quality Assessment**: Filter out low-quality face images
5. **Real-time Processing**: WebSocket support for real-time face recognition

## Troubleshooting

### Common Issues

1. **No faces detected**: Check image quality and lighting
2. **Low matching accuracy**: Adjust threshold or improve image quality
3. **Performance issues**: Consider implementing caching or batch processing
4. **Memory issues**: Monitor OpenCV memory usage

### Logs to Monitor

- Face detection success/failure rates
- Embedding extraction performance
- Database operation timing
- Error rates and types

## Dependencies

- OpenCV Java bindings
- Spring Boot
- Google Cloud Firestore
- Jackson for JSON processing
- SLF4J for logging
