package com.moments.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudStorageConfig {

    @Value("${spring.cloud.gcp.firestore.project-id}")
    private String projectId;

    @Autowired
    private GoogleCredentials googleCredentials;

    @Bean
    public Storage getStorage() {
        return StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(projectId)
                .build()
                .getService();
    }
}
