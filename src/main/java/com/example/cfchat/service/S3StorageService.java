package com.example.cfchat.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Value("${storage.s3.bucket}") private String bucket;
    @Value("${storage.s3.region:us-east-1}") private String region;
    @Value("${storage.s3.access-key:}") private String accessKey;
    @Value("${storage.s3.secret-key:}") private String secretKey;
    @Value("${storage.s3.endpoint:}") private String endpoint;
    @Value("${storage.s3.path-style-access:false}") private Boolean pathStyleAccess;
    @Value("${storage.s3.prefix:}") private String prefix;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        log.info("Initializing S3 storage service with bucket={}, region={}", bucket, region);

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));

        // Use explicit credentials if provided, otherwise fall back to default chain
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        // Custom endpoint for S3-compatible services (MinIO, etc.)
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        // Path-style access for S3-compatible services
        if (Boolean.TRUE.equals(pathStyleAccess)) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        s3Client = builder.build();
        log.info("S3 storage service initialized successfully");
    }

    @PreDestroy
    public void shutdown() {
        if (s3Client != null) {
            log.info("Shutting down S3 storage client");
            try {
                s3Client.close();
            } catch (Exception e) {
                log.warn("Error closing S3 client: {}", e.getMessage());
            }
        }
    }

    @Override
    public String store(String key, byte[] data, String contentType) {
        String objectKey = buildKey(key);
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey);

            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.contentType(contentType);
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(data));
            log.debug("Stored object in S3: s3://{}/{}", bucket, objectKey);
            return key;
        } catch (S3Exception e) {
            log.error("Failed to store object in S3: s3://{}/{} - {}", bucket, objectKey, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to store file in S3: " + key, e);
        }
    }

    @Override
    public byte[] retrieve(String key) {
        String objectKey = buildKey(key);
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            byte[] data = s3Client.getObjectAsBytes(request).asByteArray();
            log.debug("Retrieved object from S3: s3://{}/{} ({} bytes)", bucket, objectKey, data.length);
            return data;
        } catch (NoSuchKeyException e) {
            log.error("Object not found in S3: s3://{}/{}", bucket, objectKey);
            throw new RuntimeException("File not found in S3: " + key, e);
        } catch (S3Exception e) {
            log.error("Failed to retrieve object from S3: s3://{}/{} - {}", bucket, objectKey, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to retrieve file from S3: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        String objectKey = buildKey(key);
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(request);
            log.debug("Deleted object from S3: s3://{}/{}", bucket, objectKey);
        } catch (S3Exception e) {
            log.warn("Failed to delete object from S3: s3://{}/{} - {}", bucket, objectKey, e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Build the full S3 object key by prepending the configured prefix.
     */
    private String buildKey(String key) {
        if (prefix != null && !prefix.isBlank()) {
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
            return normalizedPrefix + key;
        }
        return key;
    }
}
