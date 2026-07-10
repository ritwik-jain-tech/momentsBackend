package com.moments.models;

import java.util.ArrayList;
import java.util.List;

/** Public (token-scoped) request: set the same client selection on many moments at once. */
public class BatchClientSelectionRequest {
    private String reviewToken;
    private List<String> momentIds = new ArrayList<>();
    private ClientSelection selection;

    public String getReviewToken() {
        return reviewToken;
    }

    public void setReviewToken(String reviewToken) {
        this.reviewToken = reviewToken;
    }

    public List<String> getMomentIds() {
        return momentIds;
    }

    public void setMomentIds(List<String> momentIds) {
        this.momentIds = momentIds != null ? momentIds : new ArrayList<>();
    }

    public ClientSelection getSelection() {
        return selection;
    }

    public void setSelection(ClientSelection selection) {
        this.selection = selection;
    }
}
