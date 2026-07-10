package com.chat.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentRequest {

    @NotBlank(message = "File name must not be blank")
    private String fileName;

    @NotBlank(message = "File url must not be blank")
    private String fileUrl;

    @NotBlank(message = "File type must not be blank")
    private String fileType;

    @NotNull(message = "File size must not be null")
    @Min(value = 1, message = "File size must be greater than 0")
    private Long fileSize;
}
