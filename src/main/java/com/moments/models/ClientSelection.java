package com.moments.models;

/**
 * Client-side (bride/groom) decision on a photographer-approved moment during the
 * delivery review flow. Kept separate from {@link MomentStatus} (photographer moderation).
 */
public enum ClientSelection {
    PENDING,
    SELECTED,
    REJECTED
}
