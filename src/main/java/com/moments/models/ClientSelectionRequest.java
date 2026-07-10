package com.moments.models;

/** Public (token-scoped) request: set one moment's client selection during delivery review. */
public class ClientSelectionRequest {
    private String reviewToken;
    private String momentId;
    private ClientSelection selection;

    public String getReviewToken() {
        return reviewToken;
    }

    public void setReviewToken(String reviewToken) {
        this.reviewToken = reviewToken;
    }

    public String getMomentId() {
        return momentId;
    }

    public void setMomentId(String momentId) {
        this.momentId = momentId;
    }

    public ClientSelection getSelection() {
        return selection;
    }

    public void setSelection(ClientSelection selection) {
        this.selection = selection;
    }
}
