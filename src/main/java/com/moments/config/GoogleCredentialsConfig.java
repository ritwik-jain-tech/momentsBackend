package com.moments.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class GoogleCredentialsConfig {

    @Value("${app.environment:DEV}")
    private String environment;

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        if ("PROD".equals(environment)) {
            return GoogleCredentials.getApplicationDefault();
        } else {
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("serviceAccountKey.json");
            if (serviceAccount == null) {
                throw new IOException("Service account key file not found. Please ensure serviceAccountKey.json is in src/main/resources/");
            }
            return GoogleCredentials.fromStream(serviceAccount);
        }
    }
} 