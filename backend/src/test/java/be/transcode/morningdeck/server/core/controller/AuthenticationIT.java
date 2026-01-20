package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.AuthRequest;
import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@Transactional
class AuthenticationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String REGISTER_URL = "/auth/register";
    private static final String LOGIN_URL = "/auth/login";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register a new user")
        void shouldRegisterNewUser() throws Exception {
            // Given
            RegisterRequest request = RegisterRequest.builder()
                    .username("testuser")
                    .name("Test User")
                    .email("test@example.com")
                    .password("password123")
                    .build();

            // When
            MvcResult result = mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.user.username").value(request.getUsername()))
                    .andExpect(jsonPath("$.user.email").value(request.getEmail()))
                    .andReturn();

            // Then
            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    AuthResponse.class
            );

            assertThat(response.getToken()).isNotBlank();
            assertThat(response.getUser().getUsername()).isEqualTo(request.getUsername());

            // Verify user is in database
            User savedUser = userRepository.findByUsername(request.getUsername())
                    .orElseThrow();
            assertThat(savedUser.getEmail()).isEqualTo(request.getEmail());
        }

        @Test
        @DisplayName("Should fail when registering with existing username")
        void shouldFailWithExistingUsername() throws Exception {
            // Given
            RegisterRequest request = RegisterRequest.builder()
                    .username("existinguser")
                    .name("Existing User")
                    .email("existing@example.com")
                    .password("password123")
                    .build();

            // Register first user
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Try to register same username with different email
            request.setEmail("another@example.com");

            // When/Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Username already exists"));
        }

        @Test
        @DisplayName("Should fail when registering with existing email")
        void shouldFailWithExistingEmail() throws Exception {
            // Given
            RegisterRequest request = RegisterRequest.builder()
                    .username("user1")
                    .name("User One")
                    .email("same@example.com")
                    .password("password123")
                    .build();

            // Register first user
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Try to register same email with different username
            request.setUsername("user2");

            // When/Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already exists"));
        }

        @Test
        @DisplayName("Should fail with invalid registration data")
        void shouldFailWithInvalidData() throws Exception {
            // Given
            RegisterRequest request = RegisterRequest.builder()
                    .username("")  // empty username
                    .email("invalid-email")  // invalid email
                    .password("123")  // too short password
                    .build();

            // When/Then
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        private RegisterRequest validUser;

        @BeforeEach
        void setUp() {
            validUser = RegisterRequest.builder()
                    .username("validuser")
                    .name("Valid User")
                    .email("valid@example.com")
                    .password("password123")
                    .build();

            try {
                mockMvc.perform(post(REGISTER_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validUser)))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("Should successfully login with valid credentials")
        void shouldLoginSuccessfully() throws Exception {
            // Given
            AuthRequest request = AuthRequest.builder()
                    .username(validUser.getUsername())
                    .password(validUser.getPassword())
                    .build();

            // When/Then
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.user.username").value(validUser.getUsername()))
                    .andReturn();

            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    AuthResponse.class
            );
            assertThat(response.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("Should fail login with incorrect password")
        void shouldFailLoginWithWrongPassword() throws Exception {
            // Given
            AuthRequest request = AuthRequest.builder()
                    .username(validUser.getUsername())
                    .password("wrongpassword")
                    .build();

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should fail login with non-existent username")
        void shouldFailLoginWithNonExistentUsername() throws Exception {
            // Given
            AuthRequest request = AuthRequest.builder()
                    .username("nonexistent")
                    .password("password123")
                    .build();

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should fail login with empty credentials")
        void shouldFailLoginWithEmptyCredentials() throws Exception {
            // Given
            AuthRequest request = AuthRequest.builder()
                    .username("")
                    .password("")
                    .build();

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
