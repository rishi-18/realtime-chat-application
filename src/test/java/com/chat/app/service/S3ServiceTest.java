package com.chat.app.service;

import com.chat.app.dto.PreSignedUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class S3ServiceTest {

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service();
    }

    @Test
    void generatePreSignedUrl_Fallback_WhenConfigMissing() {
        // Arrange (no properties set)

        // Act
        PreSignedUrlResponse response = s3Service.generatePreSignedUrl("test.png", "image/png");

        // Assert
        assertNotNull(response);
        assertTrue(response.isFallback());
        assertEquals("/api/v1/media/upload", response.getUploadUrl());
        assertNull(response.getFileUrl());
    }

    @Test
    void generatePreSignedUrl_Success_WhenConfigured() {
        // Arrange
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "accessKey", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(s3Service, "secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");

        // Act
        PreSignedUrlResponse response = s3Service.generatePreSignedUrl("test.png", "image/png");

        // Assert
        assertNotNull(response);
        assertFalse(response.isFallback());
        assertNotNull(response.getUploadUrl());
        assertTrue(response.getUploadUrl().contains("test-bucket"));
        assertNotNull(response.getFileUrl());
        assertTrue(response.getFileUrl().contains("test-bucket.s3.us-east-1.amazonaws.com"));
    }
}
