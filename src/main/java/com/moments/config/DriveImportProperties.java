package com.moments.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Drive import runs {@code @Async} after the HTTP response. On Cloud Run, CPU is throttled when no request is
 * active, so that work often never completes. Default: synchronous import when {@code K_SERVICE} is set unless
 * {@code google.drive.import.async} / {@code GOOGLE_DRIVE_IMPORT_ASYNC} overrides.
 */
@Component
public class DriveImportProperties {

    private static final Logger log = LoggerFactory.getLogger(DriveImportProperties.class);

    private final boolean asyncImport;

    public DriveImportProperties(@Value("${google.drive.import.async:}") String asyncProp) {
        String p = asyncProp != null ? asyncProp.trim() : "";
        if (!p.isEmpty()) {
            this.asyncImport = Boolean.parseBoolean(p);
        } else {
            this.asyncImport = !runningOnCloudRun();
        }
    }

    private static boolean runningOnCloudRun() {
        String k = System.getenv("K_SERVICE");
        return k != null && !k.isBlank();
    }

    @PostConstruct
    void logMode() {
        log.info(
                "Drive import: asyncImport={} (set GOOGLE_DRIVE_IMPORT_ASYNC or google.drive.import.async to override; Cloud Run K_SERVICE={})",
                asyncImport, System.getenv("K_SERVICE"));
    }

    public boolean isAsyncImport() {
        return asyncImport;
    }

    /**
     * When import runs on the request thread, run face-tagging HTTP calls there too so the instance still has CPU.
     */
    public boolean isSynchronousFaceTaggingDuringDriveImport() {
        return !asyncImport;
    }
}
