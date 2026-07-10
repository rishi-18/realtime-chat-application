package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {
    private UUID id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private long fileSize;
}
