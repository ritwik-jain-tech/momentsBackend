package com.moments.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


@Configuration
public class FirestoreConfig {

    @Value("${spring.cloud.gcp.firestore.project-id}")
    private String projectId;

    @Value("${firebase.credentials.path}")
    private String credentialsPath;

    private static final String ENV ="PROD";

    @Bean
    public Firestore getFirestore() throws IOException {
        GoogleCredentials credentials;
        if(ENV.equals("PROD")) {
            credentials = GoogleCredentials.getApplicationDefault();
        }else {
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("serviceAccountKey.json");
            credentials = GoogleCredentials.fromStream(serviceAccount);
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        return FirestoreClient.getFirestore();
    }
}
