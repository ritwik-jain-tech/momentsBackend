package com.moments.models;

public class MomentFilter {
    private String createdById;
    private String taggedUserId;
    private String source;
    private String likedById;
    private String TaggedToId;

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
