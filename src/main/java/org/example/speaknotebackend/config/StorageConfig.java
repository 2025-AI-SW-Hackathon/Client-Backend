package org.example.speaknotebackend.config;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${gcp.credentials.location:}")   // GCP_CREDENTIALS
    private String credentialsLocation;

    @Value("${gcp.project-id:}")            // GCP_PROJECT_ID (옵션)
    private String projectId;

    @Bean
    public Storage storage() throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();

        if (StringUtils.hasText(credentialsLocation)) {
            InputStream in;
            if (credentialsLocation.startsWith("classpath:")) {
                String path = credentialsLocation.substring("classpath:".length());
                Resource res = new ClassPathResource(path);
                in = res.getInputStream();
            } else if (credentialsLocation.startsWith("file:")) {
                String path = credentialsLocation.substring("file:".length());
                in = Files.newInputStream(Paths.get(path));
            } else {
                // 일반 파일 경로(절대/상대)
                in = Files.newInputStream(Paths.get(credentialsLocation));
            }
            GoogleCredentials creds = GoogleCredentials.fromStream(in);
            builder.setCredentials(creds);
        } else {
            // 설정이 없으면 ADC로 폴백 (GOOGLE_APPLICATION_CREDENTIALS 또는 GCE 메타데이터)
            builder.setCredentials(GoogleCredentials.getApplicationDefault());
        }

        if (StringUtils.hasText(projectId)) {
            builder.setProjectId(projectId);
        }

        return builder.build().getService();
    }
}
