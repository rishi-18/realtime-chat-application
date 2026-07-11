package com.chat.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreSignedUrlResponse {

    private String uploadUrl;
    private String fileUrl;
    private boolean fallback;
}
