package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.AuthRequest;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.dto.ResendVerificationRequest;
import be.transcode.morningdeck.server.core.model.EmailVerificationToken;
import be.transcode.morningdeck.server.core.model.Role;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.EmailVerificationTokenRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.core.service.EmailVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "application.email.verification.enabled=true",
        "application.email.verification.expiration-hours=24"
})
class EmailVerificationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    private static final String AUTH_URL = "/auth";
    private static final String ADMIN_URL = "/admin";

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Registration with Verification Enabled")
    class RegistrationTests {

        @Test
        @DisplayName("Registration returns RegisterResponse with masked email")
        void registrationReturnsRegisterResponse() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .username("testuser")
                    .name("Test User")
                    .email("test@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.email").value("t***@example.com"))
                    .andExpect(jsonPath("$.token").doesNotExist());

            // Verify user is created but not verified
            User user = userRepository.findByUsername("testuser").orElseThrow();
            assertThat(user.isEmailVerified()).isFalse();

            // Verify token was created
            assertThat(tokenRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Unverified user cannot login")
        void unverifiedUserCannotLogin() throws Exception {
            // Register user
            RegisterRequest registerRequest = RegisterRequest.builder()
                    .username("testuser")
                    .name("Test User")
                    .email("test@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk());

            // Try to login
            AuthRequest loginRequest = AuthRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Email Verification")
    class VerificationTests {

        @Test
        @DisplayName("Valid token verifies email successfully")
        void validTokenVerifiesEmail() throws Exception {
            // Create unverified user with token
            User user = createUnverifiedUser("testuser", "test@example.com");
            String token = createVerificationToken(user, 24);

            // Verify email
            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Email verified successfully. You can now login."));

            // Check user is now verified
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.isEmailVerified()).isTrue();

            // Token should be deleted
            assertThat(tokenRepository.count()).isZero();
        }

        @Test
        @DisplayName("Verified user can login")
        void verifiedUserCanLogin() throws Exception {
            // Create and verify user
            User user = createUnverifiedUser("testuser", "test@example.com");
            String token = createVerificationToken(user, 24);

            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", token))
                    .andExpect(status().isOk());

            // Now login should work
            AuthRequest loginRequest = AuthRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists());
        }

        @Test
        @DisplayName("Expired token returns error")
        void expiredTokenReturnsError() throws Exception {
            User user = createUnverifiedUser("testuser", "test@example.com");
            String token = createVerificationToken(user, -1); // Expired

            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Verification link has expired. Please request a new one."));

            // User should still be unverified
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.isEmailVerified()).isFalse();
        }

        @Test
        @DisplayName("Invalid token returns error")
        void invalidTokenReturnsError() throws Exception {
            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", "invalid-token-12345"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired verification link"));
        }

        @Test
        @DisplayName("Used token cannot be reused")
        void usedTokenCannotBeReused() throws Exception {
            User user = createUnverifiedUser("testuser", "test@example.com");
            String token = createVerificationToken(user, 24);

            // First use
            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Second use
            mockMvc.perform(get(AUTH_URL + "/verify-email")
                            .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Resend Verification")
    class ResendTests {

        @Test
        @DisplayName("Can resend verification email")
        void canResendVerification() throws Exception {
            User user = createUnverifiedUser("testuser", "test@example.com");
            createVerificationToken(user, 24);

            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("test@example.com");

            mockMvc.perform(post(AUTH_URL + "/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Should have new token (old one deleted)
            assertThat(tokenRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Resend for non-existent email returns OK (no leak)")
        void resendForNonExistentEmailReturnsOk() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("nonexistent@example.com");

            mockMvc.perform(post(AUTH_URL + "/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Resend for already verified user returns OK (no action)")
        void resendForVerifiedUserReturnsOk() throws Exception {
            User user = createUnverifiedUser("testuser", "test@example.com");
            user.setEmailVerified(true);
            userRepository.save(user);

            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("test@example.com");

            mockMvc.perform(post(AUTH_URL + "/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // No token should be created
            assertThat(tokenRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Admin Verification")
    class AdminTests {

        @Test
        @DisplayName("Admin can manually verify user")
        void adminCanManuallyVerifyUser() throws Exception {
            // Create admin
            User admin = createVerifiedUser("admin", "admin@example.com", Role.ADMIN);
            String adminToken = getAuthToken("admin", "password123");

            // Create unverified user
            User user = createUnverifiedUser("testuser", "test@example.com");

            // Admin verifies user
            mockMvc.perform(put(ADMIN_URL + "/users/" + user.getId() + "/verify-email")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // User should now be verified
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.isEmailVerified()).isTrue();
        }

        @Test
        @DisplayName("Admin cannot verify already verified user")
        void adminCannotVerifyAlreadyVerifiedUser() throws Exception {
            // Create admin
            User admin = createVerifiedUser("admin", "admin@example.com", Role.ADMIN);
            String adminToken = getAuthToken("admin", "password123");

            // Create verified user
            User user = createVerifiedUser("testuser", "test@example.com", Role.USER);

            // Try to verify again
            mockMvc.perform(put(ADMIN_URL + "/users/" + user.getId() + "/verify-email")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User email is already verified"));
        }

        @Test
        @DisplayName("Admin user list shows emailVerified status")
        void adminUserListShowsEmailVerifiedStatus() throws Exception {
            // Create admin
            User admin = createVerifiedUser("admin", "admin@example.com", Role.ADMIN);
            String adminToken = getAuthToken("admin", "password123");

            // Create unverified user
            createUnverifiedUser("unverified", "unverified@example.com");

            // Get user list
            mockMvc.perform(get(ADMIN_URL + "/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.username=='admin')].emailVerified").value(true))
                    .andExpect(jsonPath("$.content[?(@.username=='unverified')].emailVerified").value(false));
        }
    }

    // Helper methods

    private User createUnverifiedUser(String username, String email) {
        User user = User.builder()
                .username(username)
                .name("Test User")
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    private User createVerifiedUser(String username, String email, Role role) {
        User user = User.builder()
                .username(username)
                .name("Test User")
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .enabled(true)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private String createVerificationToken(User user, int expirationHours) {
        String token = generateToken();
        String tokenHash = hashToken(token);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().atZone(ZoneOffset.UTC).plusHours(expirationHours).toInstant())
                .build();
        tokenRepository.save(verificationToken);

        return token;
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getAuthToken(String username, String password) throws Exception {
        AuthRequest loginRequest = AuthRequest.builder()
                .username(username)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }
}
