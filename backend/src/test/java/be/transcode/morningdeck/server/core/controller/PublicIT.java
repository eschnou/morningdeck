package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@Transactional
class PublicIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageProvider storageProvider;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = userRepository.save(User.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .build());
    }

    @Test
    @DisplayName("Should get avatar without authentication")
    void shouldGetAvatarWithoutAuth() throws Exception {
        // Store an avatar for the test user
        byte[] avatarContent = "test image content".getBytes();
        storageProvider.store(testUser.getId(), avatarContent, MediaType.IMAGE_PNG_VALUE);

        // Get it through public endpoint
        mockMvc.perform(get("/public/avatars/{id}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(avatarContent))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    @DisplayName("Should return 404 for non-existent user avatar")
    void shouldReturn404ForNonExistentAvatar() throws Exception {
        mockMvc.perform(get("/public/avatars/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
