package com.chat.app.service;

import com.chat.app.dto.PreSignedUrlResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class S3Service {

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    @Value("${aws.s3.cdn-url:}")
    private String cdnUrl;

    public PreSignedUrlResponse generatePreSignedUrl(String fileName, String contentType) {
        if (bucketName == null || bucketName.trim().isEmpty() ||
            accessKey == null || accessKey.trim().isEmpty() ||
            secretKey == null || secretKey.trim().isEmpty()) {
            
            log.info("S3 configuration properties are missing. Triggering local storage fallback upload path.");
            return PreSignedUrlResponse.builder()
                    .uploadUrl("/api/v1/media/upload")
                    .fileUrl(null)
                    .fallback(true)
                    .build();
        }

        try {
            String extension = "";
            if (fileName != null && fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf("."));
            }
            String uniqueKey = "uploads/" + UUID.randomUUID().toString() + extension;

            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            try (S3Presigner presigner = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .build()) {

                PutObjectRequest objectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(uniqueKey)
                        .contentType(contentType)
                        .build();

                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(objectRequest)
                        .build();

                PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
                String uploadUrl = presignedRequest.url().toString();

                String publicUrl;
                if (cdnUrl != null && !cdnUrl.trim().isEmpty()) {
                    publicUrl = cdnUrl.endsWith("/") ? cdnUrl + uniqueKey : cdnUrl + "/" + uniqueKey;
                } else {
                    publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, uniqueKey);
                }

                log.info("Generated S3 pre-signed URL successfully for key: {}", uniqueKey);

                return PreSignedUrlResponse.builder()
                        .uploadUrl(uploadUrl)
                        .fileUrl(publicUrl)
                        .fallback(false)
                        .build();
            }
        } catch (Exception ex) {
            log.error("Failed to generate S3 pre-signed URL. Falling back to local storage.", ex);
            return PreSignedUrlResponse.builder()
                    .uploadUrl("/api/v1/media/upload")
                    .fileUrl(null)
                    .fallback(true)
                    .build();
        }
    }
}
