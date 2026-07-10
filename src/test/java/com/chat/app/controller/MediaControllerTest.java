package com.chat.app.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MediaController mediaController = new MediaController();
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
}
