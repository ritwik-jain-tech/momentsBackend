package com.moments.models;

/**
 * Guest-facing app settings for an event (matches admin create payload shape).
 */
public class GuestAppConfig {

    private Boolean enabled;
    private String thumbnailImage;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getThumbnailImage() {
        return thumbnailImage;
    }

    public void setThumbnailImage(String thumbnailImage) {
        this.thumbnailImage = thumbnailImage;
    }
}
