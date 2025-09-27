package com.moments.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.project-id:}")
    private String projectId;

    @Autowired
    private GoogleCredentials googleCredentials;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder();
                
                if (projectId != null && !projectId.isEmpty()) {
                    builder.setProjectId(projectId);
                }
                
                builder.setCredentials(googleCredentials);
                
                FirebaseApp.initializeApp(builder.build());
                System.out.println("Firebase Admin SDK initialized successfully");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize Firebase Admin SDK: " + e.getMessage());
            System.err.println("Please ensure you have either:");
            System.err.println("1. Set GOOGLE_APPLICATION_CREDENTIALS environment variable");
            System.err.println("2. Set app.environment=PROD for production deployment");
            System.err.println("3. Place serviceAccountKey.json in src/main/resources/ for development");
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}
