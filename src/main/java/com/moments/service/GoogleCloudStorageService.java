package com.moments.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.moments.models.FileType;
import com.moments.models.FileUploadResponse;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
public class GoogleCloudStorageService {
    @Autowired
    private Storage storage;

    private final String bucketName = System.getProperty("gcp.bucket.name", "momentslive");
    private final String cdnDomain = "images.moments.live";
    
    // Initialize ImageIO plugins
    static {
        try {
            // Scan for ImageIO plugins
            ImageIO.scanForPlugins();
        } catch (Exception e) {
            // Plugin registration failed, but continue - will handle gracefully
            System.err.println("Warning: Could not scan for ImageIO plugins: " + e.getMessage());
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file, FileType fileType) throws IOException {
        byte[] fileBytes;
        String contentType;
        String originalFilename = file.getOriginalFilename();
        String blobName = "full/"+ originalFilename;
        
        // Process images: compress JPEG only with 80% quality
        if (fileType == FileType.IMAGE) {
            // Check if it's a JPEG image
            String fileContentType = file.getContentType();
            String lowerFilename = originalFilename != null ? originalFilename.toLowerCase() : "";
            boolean isJpeg = (fileContentType != null && (fileContentType.contains("jpeg") || fileContentType.contains("jpg"))) ||
                            lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg");
            
            // Check if it's HEIC - skip compression for HEIC
            boolean isHeic = (fileContentType != null && (fileContentType.contains("heic") || fileContentType.contains("heif"))) ||
                            lowerFilename.endsWith(".heic") || lowerFilename.endsWith(".heif");
            
            if (isHeic) {
                // Skip compression for HEIC images - upload as-is
                fileBytes = file.getBytes();
                contentType = fileContentType != null ? fileContentType : "image/heic";
                blobName = "full/" + System.currentTimeMillis() + "_" + originalFilename;
            } else if (isJpeg) {
                // Compress JPEG images with 80% quality
                fileBytes = convertToCompressedJPEG(file, 0.8f);
                contentType = "image/jpeg";
            } else {
                // For other image formats, upload as-is without compression
                fileBytes = file.getBytes();
                contentType = fileContentType != null ? fileContentType : "image/png";
            }
        } else {
            // For videos, use original file
            fileBytes = file.getBytes();
                contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";

        }
        // Define the blob (object) metadata
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType).build();

        // Upload the file to the bucket
        Blob blob = storage.create(blobInfo, fileBytes);

        // Return the CDN URL of the uploaded file with HTTPS
        String publicURL = String.format("https://%s/%s", cdnDomain, blob.getName());

        return new FileUploadResponse(blobName, contentType, publicURL);
    }
    
    /**
     * Converts an image to JPEG format with specified quality compression
     * Uses progressive JPEG and optimization techniques
     * @param file The image file to convert
     * @param quality Quality level (0.0 to 1.0, where 1.0 is highest quality)
     * @return Compressed JPEG bytes
     */
    private byte[] convertToCompressedJPEG(MultipartFile file, float quality) throws IOException {
        // Read the original image
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Could not read image from file");
        }
        
        long originalSize = file.getSize();
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
        
        try {
            // Use Thumbnailator to optimize the image
            // Scale down if too large, but maintain aspect ratio
            int maxDimension = 4096; // Maximum dimension to prevent extremely large files
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            
            BufferedImage processedImage = originalImage;
            
            // Scale down if necessary (this can reduce file size significantly)
            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                processedImage = Thumbnails.of(originalImage)
                    .scale(scale)
                    .asBufferedImage();
            }
            
            // Convert to RGB (JPEG doesn't support transparency, so remove alpha channel)
            BufferedImage rgbImage;
            if (processedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                rgbImage = new BufferedImage(processedImage.getWidth(), processedImage.getHeight(), 
                                             BufferedImage.TYPE_INT_RGB);
                rgbImage.getGraphics().drawImage(processedImage, 0, 0, null);
            } else {
                rgbImage = processedImage;
            }
            
            // Use the specified quality level for compression
            writeJPEGWithQuality(rgbImage, compressedStream, quality, true);
            
        } catch (Exception e) {
            // Fallback: use specified quality if optimization fails
            writeJPEGWithQuality(originalImage, compressedStream, quality, false);
        }
        
        return compressedStream.toByteArray();
    }
    
    /**
     * Writes a BufferedImage as JPEG with specified quality and optimization settings
     * @param image The image to compress
     * @param outputStream The output stream to write to
     * @param quality Quality level (0.0 to 1.0, where 1.0 is highest quality)
     * @param progressive Whether to use progressive JPEG encoding (better compression)
     */
    private void writeJPEGWithQuality(BufferedImage image, 
                                      ByteArrayOutputStream outputStream, 
                                      float quality, 
                                      boolean progressive) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            // Try "jpeg" if "jpg" doesn't work
            writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IOException("No JPEG writer found");
            }
        }
        
        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        
        // Enable compression with quality setting
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
            
            // Enable progressive JPEG for better compression (if supported)
            if (progressive) {
                try {
                    if (writeParam instanceof javax.imageio.plugins.jpeg.JPEGImageWriteParam) {
                        javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParam = 
                            (javax.imageio.plugins.jpeg.JPEGImageWriteParam) writeParam;
                        jpegParam.setProgressiveMode(javax.imageio.plugins.jpeg.JPEGImageWriteParam.MODE_DEFAULT);
                    }
                } catch (Exception e) {
                    // Progressive mode not supported, continue with standard JPEG
                    // Quality setting is still applied
                }
            }
        }
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(ios);
            IIOImage iioImage = new IIOImage(image, null, null);
            writer.write(null, iioImage, writeParam);
        } finally {
            writer.dispose();
        }
    }
    
    /**
     * Compresses an existing image in GCS by downloading, compressing, and overwriting it
     * Only uploads if the compressed version is actually smaller than the original
     * @param blobName The name of the blob in GCS
     * @param quality Compression quality (0.0 to 1.0, where 1.0 is highest quality)
     * @return Compression result with original and new sizes
     * @throws IOException If download, compression, or upload fails
     */
    public CompressionResult compressExistingImage(String blobName, Float quality) throws IOException {
        // Download the existing blob
        Blob existingBlob = storage.get(bucketName, blobName);
        if (existingBlob == null) {
            throw new IOException("Blob not found: " + blobName);
        }
        
        byte[] originalBytes = existingBlob.getContent();
        long originalSize = originalBytes != null ? originalBytes.length : 0;
        
        if (originalSize == 0) {
            throw new IOException("Blob is empty: " + blobName);
        }
        
        // Skip HEIC images - they cannot be compressed without HEIC reader
        String lowerBlobName = blobName.toLowerCase();
        if (lowerBlobName.endsWith(".heic") || lowerBlobName.endsWith(".heif") || 
            lowerBlobName.contains(".heic") || lowerBlobName.contains(".heif")) {
            throw new IOException("Skipping HEIC image. HEIC format cannot be compressed without HEIC reader library. Blob: " + blobName);
        }
        
        // Try to read the image - will work for formats supported by ImageIO
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (originalImage == null) {
            throw new IOException("Could not read image from blob. Format may not be supported or file may be corrupted: " + blobName);
        }
        
        // Convert to JPEG with specified quality (default to 0.6 if not provided)
        float compressionQuality = quality != null ? quality : 0.6f;
        byte[] jpegBytes = compressBufferedImage(originalImage, originalSize, compressionQuality);
        long jpegSize = jpegBytes.length;
        
        // Update blob name to have .jpg extension
        String jpegBlobName = blobName;
        if (!jpegBlobName.toLowerCase().endsWith(".jpg") && !jpegBlobName.toLowerCase().endsWith(".jpeg")) {
            // Remove existing extension and add .jpg
            int lastDot = jpegBlobName.lastIndexOf('.');
            if (lastDot > 0) {
                jpegBlobName = jpegBlobName.substring(0, lastDot) + ".jpg";
            } else {
                jpegBlobName = jpegBlobName + ".jpg";
            }
        }
        
        // Upload as JPEG (overwrite original or create new with .jpg extension)
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, jpegBlobName)
                .setContentType("image/jpeg")
                .build();
        
        storage.create(blobInfo, jpegBytes);
        
        // If the blob name changed, delete the original file
        if (!jpegBlobName.equals(blobName)) {
            try {
                storage.delete(bucketName, blobName);
            } catch (Exception e) {
                // Log but don't fail if deletion fails
                System.err.println("Warning: Could not delete original file " + blobName + ": " + e.getMessage());
            }
        }
        
        double compressionRatio = originalSize > 0 ? (double) originalSize / jpegSize : 1.0;
        
        return new CompressionResult(jpegBlobName, originalSize, jpegSize, compressionRatio);
    }
    
    /**
     * Compresses a BufferedImage using the specified quality
     * @param originalImage The image to compress
     * @param originalSize The original file size (for comparison)
     * @param quality Compression quality (0.0 to 1.0, where 1.0 is highest quality)
     * @return Compressed image bytes
     */
    private byte[] compressBufferedImage(BufferedImage originalImage, long originalSize, float quality) throws IOException {
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
        
        try {
            // Use Thumbnailator to optimize the image
            int maxDimension = 4096;
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            
            BufferedImage processedImage = originalImage;
            
            // Scale down if necessary
            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                processedImage = Thumbnails.of(originalImage)
                    .scale(scale)
                    .asBufferedImage();
            }
            
            // Convert to RGB
            BufferedImage rgbImage;
            if (processedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                rgbImage = new BufferedImage(processedImage.getWidth(), processedImage.getHeight(), 
                                             BufferedImage.TYPE_INT_RGB);
                rgbImage.getGraphics().drawImage(processedImage, 0, 0, null);
            } else {
                rgbImage = processedImage;
            }
            
            // Use the specified quality level for compression
            writeJPEGWithQuality(rgbImage, compressedStream, quality, true);
            
            // Check if compression actually reduced size
            byte[] compressedBytes = compressedStream.toByteArray();
            if (compressedBytes.length >= originalSize) {
                throw new IOException("Compression did not reduce size enough - compressed size would be larger or equal");
            }
            
        } catch (Exception e) {
            // If optimization fails, try one more time with the same quality
            try {
                ByteArrayOutputStream fallbackStream = new ByteArrayOutputStream();
                writeJPEGWithQuality(originalImage, fallbackStream, quality, false);
                byte[] fallbackBytes = fallbackStream.toByteArray();
                
                if (fallbackBytes.length < originalSize) {
                    return fallbackBytes;
                } else {
                    throw new IOException("Compression failed to reduce size: " + e.getMessage(), e);
                }
            } catch (Exception fallbackException) {
                throw new IOException("Compression failed: " + e.getMessage(), e);
            }
        }
        
        return compressedStream.toByteArray();
    }
    
    /**
     * Compresses an image from a URL and uploads it to /compressed/ folder at 50% quality
     * Used for feed images (mobile feed - doesn't need high quality)
     * @param imageUrl The original image URL
     * @return The compressed image URL (images.moments.live/compressed/filename.jpg)
     * @throws IOException If download, compression, or upload fails
     */
    public String compressAndUploadForFeed(String imageUrl) throws IOException {
        // Extract blob name from URL
        String blobName = extractBlobNameFromUrl(imageUrl);
        if (blobName == null || blobName.isEmpty()) {
            throw new IOException("Could not extract blob name from URL: " + imageUrl);
        }
        
        // Download the original image
        Blob existingBlob = storage.get(bucketName, blobName);
        if (existingBlob == null) {
            throw new IOException("Blob not found: " + blobName);
        }
        
        byte[] originalBytes = existingBlob.getContent();
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IOException("Blob is empty: " + blobName);
        }
        
        // Skip HEIC images
        String lowerBlobName = blobName.toLowerCase();
        if (lowerBlobName.endsWith(".heic") || lowerBlobName.endsWith(".heif") || 
            lowerBlobName.contains(".heic") || lowerBlobName.contains(".heif")) {
            throw new IOException("Skipping HEIC image for feed compression. HEIC format not supported.");
        }
        
        // Try to read the image
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (originalImage == null) {
            throw new IOException("Could not read image from blob: " + blobName);
        }
        
        // Compress to JPEG with 50% quality (high compression for mobile feed)
        byte[] compressedBytes = compressBufferedImageForFeed(originalImage);
        
        // Extract filename from blob name (remove folder path if any)
        String filename = blobName;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filename.length() - 1) {
            filename = filename.substring(lastSlash + 1);
        }
        
        // Remove extension and add .jpg
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot) + ".jpg";
        } else {
            filename = filename + ".jpg";
        }
        
        // Upload to compressed folder
        String compressedBlobName = "compressed/" + filename;
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, compressedBlobName)
                .setContentType("image/jpeg")
                .build();
        
        storage.create(blobInfo, compressedBytes);
        
        // Return the CDN URL
        return String.format("https://%s/%s", cdnDomain, compressedBlobName);
    }
    
    /**
     * Compresses a BufferedImage to JPEG with 50% quality for feed display
     * High compression ratio for mobile feed (doesn't need very high quality)
     */
    private byte[] compressBufferedImageForFeed(BufferedImage originalImage) throws IOException {
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
        
        try {
            // Use Thumbnailator to optimize the image
            int maxDimension = 2048; // Smaller max dimension for feed images
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            
            BufferedImage processedImage = originalImage;
            
            // Scale down if necessary (smaller for feed)
            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                processedImage = Thumbnails.of(originalImage)
                    .scale(scale)
                    .asBufferedImage();
            }
            
            // Convert to RGB
            BufferedImage rgbImage;
            if (processedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                rgbImage = new BufferedImage(processedImage.getWidth(), processedImage.getHeight(), 
                                             BufferedImage.TYPE_INT_RGB);
                rgbImage.getGraphics().drawImage(processedImage, 0, 0, null);
            } else {
                rgbImage = processedImage;
            }
            
            // Compress with 50% quality for feed
            writeJPEGWithQuality(rgbImage, compressedStream, 0.5f, true);
            
        } catch (Exception e) {
            // Fallback: use 50% quality if optimization fails
            writeJPEGWithQuality(originalImage, compressedStream, 0.5f, false);
        }
        
        return compressedStream.toByteArray();
    }
    
    /**
     * Extracts blob name from a CDN URL
     * @param url The CDN URL (e.g., https://images.moments.live/blobName)
     * @return The blob name
     */
    public String extractBlobNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // Remove protocol and domain
        String blobName = url;
        if (url.startsWith("https://")) {
            blobName = url.substring(8);
        } else if (url.startsWith("http://")) {
            blobName = url.substring(7);
        }
        
        // Remove domain part
        int firstSlash = blobName.indexOf('/');
        if (firstSlash >= 0 && firstSlash < blobName.length() - 1) {
            blobName = blobName.substring(firstSlash + 1);
        }
        
        // Remove query parameters if any
        int questionMark = blobName.indexOf('?');
        if (questionMark >= 0) {
            blobName = blobName.substring(0, questionMark);
        }
        
        return blobName;
    }
    
    /**
     * Result of compression operation
     */
    public static class CompressionResult {
        private final String blobName;
        private final long originalSize;
        private final long compressedSize;
        private final double compressionRatio;
        
        public CompressionResult(String blobName, long originalSize, long compressedSize, double compressionRatio) {
            this.blobName = blobName;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRatio = compressionRatio;
        }
        
        public String getBlobName() {
            return blobName;
        }
        
        public long getOriginalSize() {
            return originalSize;
        }
        
        public long getCompressedSize() {
            return compressedSize;
        }
        
        public double getCompressionRatio() {
            return compressionRatio;
        }
        
        public long getSizeReduction() {
            return originalSize - compressedSize;
        }
    }
}

