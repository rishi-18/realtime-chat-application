package com.chat.app.controller;

import com.chat.app.dto.PreSignedUrlResponse;
import com.chat.app.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        MediaController mediaController = new MediaController(s3Service);
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController).build();
    }

    @Test
    void uploadFile_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "some content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/media/upload").file(mockFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test.png"))
                .andExpect(jsonPath("$.fileType").value("image/png"))
                .andExpect(jsonPath("$.fileUrl").exists());
    }

    @Test
    void uploadFile_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "",
                "image/png",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/media/upload").file(mockFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Uploaded file must not be empty."));
    }

    @Test
    void uploadFile_UnsupportedMimeType_ReturnsBadRequest() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "test.exe",
                "application/x-msdownload",
                "some executable content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/media/upload").file(mockFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unsupported file content type: application/x-msdownload"));
    }

    @Test
    void getPreSignedUrl_Success() throws Exception {
        PreSignedUrlResponse mockResponse = PreSignedUrlResponse.builder()
                .uploadUrl("https://s3.amazonaws.com/test-url")
                .fileUrl("https://cdn.com/test.png")
                .fallback(false)
                .build();

        when(s3Service.generatePreSignedUrl(anyString(), anyString())).thenReturn(mockResponse);

        String requestBody = "{\"fileName\":\"test.png\",\"contentType\":\"image/png\"}";

        mockMvc.perform(post("/api/v1/media/pre-signed-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.amazonaws.com/test-url"))
                .andExpect(jsonPath("$.fallback").value(false));
    }

    @Test
    void getPreSignedUrl_UnsupportedMimeType_ReturnsBadRequest() throws Exception {
        String requestBody = "{\"fileName\":\"test.exe\",\"contentType\":\"application/x-msdownload\"}";

        mockMvc.perform(post("/api/v1/media/pre-signed-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unsupported file content type: application/x-msdownload"));
    }
}
