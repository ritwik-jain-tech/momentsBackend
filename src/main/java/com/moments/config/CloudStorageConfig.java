package com.moments.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class CloudStorageConfig {

    @Value("${spring.cloud.gcp.firestore.project-id}")
    private String projectId;

    private static final String ENV ="PROD";

    @Bean
   public  Storage getStorage() throws IOException {


        GoogleCredentials credentials;
        if(ENV.equals("PROD")) {
            credentials = GoogleCredentials.getApplicationDefault();
        }else {
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("serviceAccountKey.json");
            credentials = GoogleCredentials.fromStream(serviceAccount);
        }

        return StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
                .getService();
   }
}
