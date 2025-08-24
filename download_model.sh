#!/bin/bash

# Create models directory if it doesn't exist
mkdir -p src/main/resources/models

# Download a proper face recognition model (ArcFace/InsightFace)
curl -L "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx" \
     -o src/main/resources/models/face_recognition_model.onnx

echo "Face recognition model downloaded successfully!" 