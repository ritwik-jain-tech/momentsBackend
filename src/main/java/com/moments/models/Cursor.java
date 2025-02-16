package com.moments.models;

public class Cursor {
    private int total;
    private int offset;
    private int limit;
    private Long lastCreatedTime;
    private boolean isLastPage;

    public Cursor(int total, int offset, int limit, Long lastCreatedTime, boolean isLastPage) {
        this.total = total;
        this.offset = offset;
        this.limit = limit;
        this.lastCreatedTime = lastCreatedTime;
        this.isLastPage = isLastPage;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Long getLastCreatedTime() {
        return lastCreatedTime;
    }

    public void setLastCreatedTime(Long lastCreatedTime) {
        this.lastCreatedTime = lastCreatedTime;
    }

    public boolean isLastPage() {
        return isLastPage;
    }
    public void setLastPage(boolean lastPage) {
        this.isLastPage = lastPage;
    }
}
