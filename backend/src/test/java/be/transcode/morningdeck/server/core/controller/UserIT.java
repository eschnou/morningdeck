package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.*;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.storage.StorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class UserIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageProvider storageProvider;

    private static final String BASE_URL = "/users";
    private static final String AUTH_URL = "/auth";
    private String authToken;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        // Register a test user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );

        authToken = authResponse.getToken();
        testUser = userRepository.findByUsername(registerRequest.getUsername()).orElseThrow();
    }

    @Nested
    @DisplayName("Current User Profile Tests")
    class CurrentUserProfileTests {

        @Test
        @DisplayName("Should get current user profile")
        void shouldGetCurrentUserProfile() throws Exception {
            mockMvc.perform(get(BASE_URL + "/me")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                    .andExpect(jsonPath("$.username").value(testUser.getUsername()))
                    .andExpect(jsonPath("$.name").value(testUser.getName()))
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()));
        }

        @Test
        @DisplayName("Should update user profile successfully")
        void shouldUpdateProfile() throws Exception {
            // Given
            UpdateUserProfileDTO updateDto = UpdateUserProfileDTO.builder()
                    .name("Updated Name")
                    .email("updated@example.com")
                    .build();

            // When/Then
            mockMvc.perform(patch(BASE_URL + "/me")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(updateDto.getName()))
                    .andExpect(jsonPath("$.email").value(updateDto.getEmail()));

            // Verify database update
            User updatedUser = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
            assertThat(updatedUser.getName()).isEqualTo(updateDto.getName());
            assertThat(updatedUser.getEmail()).isEqualTo(updateDto.getEmail());
        }

        @Test
        @DisplayName("Should fail to get profile without authentication")
        void shouldFailToGetProfileWithoutAuth() throws Exception {
            mockMvc.perform(get(BASE_URL + "/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should fail to get profile with invalid token")
        void shouldFailToGetProfileWithInvalidToken() throws Exception {
            mockMvc.perform(get(BASE_URL + "/me")
                            .header("Authorization", "Bearer invalid_token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should fail to update profile with invalid email")
        void shouldFailToUpdateProfileWithInvalidEmail() throws Exception {
            UpdateUserProfileDTO updateDto = UpdateUserProfileDTO.builder()
                    .email("invalid-email")
                    .build();

            mockMvc.perform(patch(BASE_URL + "/me")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isBadRequest());

            // Verify email wasn't updated
            User unchangedUser = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
            assertThat(unchangedUser.getEmail()).isEqualTo(testUser.getEmail());
        }

        @Test
        @DisplayName("Should fail to update profile with existing email")
        void shouldFailToUpdateProfileWithExistingEmail() throws Exception {
            // Create another user with a different email
            RegisterRequest anotherUser = RegisterRequest.builder()
                    .username("anotheruser")
                    .name("Another User")
                    .email("another@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(anotherUser)))
                    .andExpect(status().isOk());

            // Try to update first user's email to the second user's email
            UpdateUserProfileDTO updateDto = UpdateUserProfileDTO.builder()
                    .email("another@example.com")
                    .build();

            mockMvc.perform(patch(BASE_URL + "/me")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already exists"));
        }
    }

    @Nested
    @DisplayName("Avatar Management Tests")
    class AvatarTests {

        @Test
        @DisplayName("Should upload avatar successfully")
        void shouldUploadAvatar() throws Exception {
            MockMultipartFile avatarFile = new MockMultipartFile(
                    "avatar",
                    "test-avatar.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test image content".getBytes()
            );

            MvcResult result = mockMvc.perform(multipart(BASE_URL + "/me/avatar")
                            .file(avatarFile)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").value(
                            Matchers.matchesPattern("http://test\\.example\\.com/public/avatars/[\\w-]+")
                    ))
                    .andReturn();
        }

        @Test
        @DisplayName("Should delete avatar successfully")
        void shouldDeleteAvatar() throws Exception {
            // First upload an avatar
            MockMultipartFile avatarFile = new MockMultipartFile(
                    "avatar",
                    "test-avatar.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test image content".getBytes()
            );

            mockMvc.perform(multipart(BASE_URL + "/me/avatar")
                            .file(avatarFile)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());

            // Then delete it
            mockMvc.perform(delete(BASE_URL + "/me/avatar")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNoContent());

            // Verify database update
            User updatedUser = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
            assertThat(updatedUser.getAvatarUrl()).isNull();
        }

        @Test
        @DisplayName("Should fail to upload avatar without authentication")
        void shouldFailToUploadAvatarWithoutAuth() throws Exception {
            MockMultipartFile avatarFile = new MockMultipartFile(
                    "avatar",
                    "test-avatar.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test image content".getBytes()
            );

            mockMvc.perform(multipart(BASE_URL + "/me/avatar")
                            .file(avatarFile))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should fail to upload avatar with unsupported type")
        void shouldFailToUploadAvatarWithUnsupportedType() throws Exception {
            MockMultipartFile avatarFile = new MockMultipartFile(
                    "avatar",
                    "test-avatar.txt",
                    MediaType.TEXT_PLAIN_VALUE,
                    "test content".getBytes()
            );

            mockMvc.perform(multipart(BASE_URL + "/me/avatar")
                            .file(avatarFile)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unsupported file type. Supported types are: image/jpeg, image/png, image/gif, audio/mpeg"));
        }

        @Test
        @DisplayName("Should fail to upload empty avatar")
        void shouldFailToUploadEmptyAvatar() throws Exception {
            MockMultipartFile avatarFile = new MockMultipartFile(
                    "avatar",
                    "test-avatar.png",
                    MediaType.IMAGE_PNG_VALUE,
                    new byte[0]
            );

            mockMvc.perform(multipart(BASE_URL + "/me/avatar")
                            .file(avatarFile)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Avatar file cannot be empty"));
        }

        @Test
        @DisplayName("Should fail to delete non-existent avatar")
        void shouldFailToDeleteNonExistentAvatar() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/me/avatar")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User does not have an avatar"));
        }
    }

    @Nested
    @DisplayName("Password Management Tests")
    class PasswordTests {

        @Test
        @DisplayName("Should change password successfully")
        void shouldChangePassword() throws Exception {
            // Given
            ChangePasswordDTO passwordDto = ChangePasswordDTO.builder()
                    .currentPassword("password123")
                    .newPassword("newPassword123")
                    .build();

            // When/Then
            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordDto)))
                    .andExpect(status().isOk());

            // Verify can login with new password
            AuthRequest loginRequest = AuthRequest.builder()
                    .username(testUser.getUsername())
                    .password("newPassword123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should fail to change password with incorrect current password")
        void shouldFailToChangePasswordWithIncorrectCurrent() throws Exception {
            ChangePasswordDTO passwordDto = ChangePasswordDTO.builder()
                    .currentPassword("wrongpassword")
                    .newPassword("newPassword123")
                    .build();

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordDto)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        }

        @Test
        @DisplayName("Should fail to change password with invalid new password")
        void shouldFailToChangePasswordWithInvalidNew() throws Exception {
            ChangePasswordDTO passwordDto = ChangePasswordDTO.builder()
                    .currentPassword("password123")
                    .newPassword("short")  // too short
                    .build();

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to change password without required fields")
        void shouldFailToChangePasswordWithMissingFields() throws Exception {
            ChangePasswordDTO passwordDto = ChangePasswordDTO.builder().build();

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordDto)))
                    .andExpect(status().isBadRequest());
        }
    }
}
