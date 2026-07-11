package com.chat.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionRequest {

    @NotBlank(message = "Emoji must not be blank")
    @Size(max = 32, message = "Emoji length cannot exceed 32 characters")
    private String emoji;
}
