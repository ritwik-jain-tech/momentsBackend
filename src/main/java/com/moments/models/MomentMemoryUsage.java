package com.moments.models;

/**
 * Per-moment storage breakdown. Original is set at upload; optimised and thumbnail
 * are filled when the face-tagging pipeline processes the image.
 */
public class MomentMemoryUsage {

    private Long originalUploadSizeBytes;
    private Long optimisedSizeBytes;
    private Long thumbnailSizeBytes;

    public Long getOriginalUploadSizeBytes() {
        return originalUploadSizeBytes;
    }

    public void setOriginalUploadSizeBytes(Long originalUploadSizeBytes) {
        this.originalUploadSizeBytes = originalUploadSizeBytes;
    }

    public Long getOptimisedSizeBytes() {
        return optimisedSizeBytes;
    }

    public void setOptimisedSizeBytes(Long optimisedSizeBytes) {
        this.optimisedSizeBytes = optimisedSizeBytes;
    }

    public Long getThumbnailSizeBytes() {
        return thumbnailSizeBytes;
    }

    public void setThumbnailSizeBytes(Long thumbnailSizeBytes) {
        this.thumbnailSizeBytes = thumbnailSizeBytes;
    }
}
