package com.chat.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageUpdateRequest {

    @NotBlank(message = "Message content must not be blank")
    @Size(max = 4000, message = "Message content cannot exceed 4000 characters")
    private String content;
}
