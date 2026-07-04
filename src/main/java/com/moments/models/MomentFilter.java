package com.moments.models;

public class MomentFilter {
    private String createdById;
    private String taggedUserId;
    private String source;
    private String likedById;
    private String TaggedToId;

    /** Admin feed: moderation tab ({@code all} | pending | approved | rejected). */
    private String adminModerationTab;

    /** Admin feed: folder bucket — {@code videos} restricts to VIDEO; omit or {@code images} loads all media (client trims). */
    private String adminMediaBucket;

    /** Admin feed: creator role filter key ({@code all} | guest | photographer | groom | bride). */
    private String adminCreatorRoleFilter;

    /** Admin feed: aligns with dashboard sort keys (capture-time uses upload time desc). */
    private String adminSort;

    public String getAdminModerationTab() {
        return adminModerationTab;
    }

    public void setAdminModerationTab(String adminModerationTab) {
        this.adminModerationTab = adminModerationTab;
    }

    public String getAdminMediaBucket() {
        return adminMediaBucket;
    }

    public void setAdminMediaBucket(String adminMediaBucket) {
        this.adminMediaBucket = adminMediaBucket;
    }

    public String getAdminCreatorRoleFilter() {
        return adminCreatorRoleFilter;
    }

    public void setAdminCreatorRoleFilter(String adminCreatorRoleFilter) {
        this.adminCreatorRoleFilter = adminCreatorRoleFilter;
    }

    public String getAdminSort() {
        return adminSort;
    }

    public void setAdminSort(String adminSort) {
        this.adminSort = adminSort;
    }

    public String getLikedById() {
        return likedById;
    }

    public void setLikedById(String likedById) {
        this.likedById = likedById;
    }

    public String getTaggedToId() {
        return TaggedToId;
    }

    public void setTaggedToId(String taggedToId) {
        TaggedToId = taggedToId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }


    public String getCreatedById() {
        return createdById;
    }

    public void setCreatedById(String createdById) {
        this.createdById = createdById;
    }

    public String getTaggedUserId() {
        return taggedUserId;
    }

    public void setTaggedUserId(String taggedUserId) {
        this.taggedUserId = taggedUserId;
    }
}
