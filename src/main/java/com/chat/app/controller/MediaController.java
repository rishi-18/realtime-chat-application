package com.chat.app.controller;

import com.chat.app.dto.AttachmentResponse;
import com.chat.app.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@Slf4j
public class MediaController {

    private final com.chat.app.service.S3Service s3Service;

    public MediaController(com.chat.app.service.S3Service s3Service) {
        this.s3Service = s3Service;
    }

    private static final String UPLOAD_DIR = "uploads";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> PERMITTED_TYPES = List.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf", "text/plain"
    );

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Uploaded file must not be empty."));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "File size exceeds maximum limit of 10MB."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !PERMITTED_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsupported file content type: " + contentType));
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path destinationFile = uploadPath.resolve(uniqueFilename);

            file.transferTo(destinationFile.toFile());

            AttachmentResponse response = AttachmentResponse.builder()
                    .fileName(originalFilename != null ? originalFilename : uniqueFilename)
                    .fileUrl("/uploads/" + uniqueFilename)
                    .fileType(contentType)
                    .fileSize(file.getSize())
                    .build();

            log.info("Successfully uploaded media file: {} -> {}", originalFilename, uniqueFilename);
            return ResponseEntity.ok(response);

        } catch (IOException ex) {
            log.error("Failed to write uploaded file to disk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal server error during file upload."));
        }
    }

    @PostMapping("/pre-signed-url")
    public ResponseEntity<?> getPreSignedUrl(@RequestBody @jakarta.validation.Valid com.chat.app.dto.PreSignedUrlRequest request) {
        log.info("Request received to generate pre-signed upload URL for file: {}", request.getFileName());
        
        String contentType = request.getContentType();
        if (contentType == null || !PERMITTED_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsupported file content type: " + contentType));
        }

        com.chat.app.dto.PreSignedUrlResponse response = s3Service.generatePreSignedUrl(request.getFileName(), contentType);
        return ResponseEntity.ok(response);
    }
}
