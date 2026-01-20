package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.*;
import be.transcode.morningdeck.server.core.model.Role;
import be.transcode.morningdeck.server.core.model.Subscription;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class AdminIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String ADMIN_URL = "/admin";
    private static final String AUTH_URL = "/auth";
    private String adminToken;
    private String userToken;
    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        // Register admin user
        RegisterRequest adminRequest = RegisterRequest.builder()
                .username("adminuser")
                .name("Admin User")
                .email("admin@example.com")
                .password("password123")
                .build();

        MvcResult adminResult = mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse adminResponse = objectMapper.readValue(
                adminResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        adminToken = adminResponse.getToken();

        // Set admin role directly in database
        adminUser = userRepository.findByUsername("adminuser").orElseThrow();
        adminUser.setRole(Role.ADMIN);
        userRepository.save(adminUser);

        // Re-login to get token with admin role
        AuthRequest loginRequest = AuthRequest.builder()
                .username("adminuser")
                .password("password123")
                .build();

        adminResult = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        adminResponse = objectMapper.readValue(
                adminResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        adminToken = adminResponse.getToken();

        // Register regular user
        RegisterRequest userRequest = RegisterRequest.builder()
                .username("regularuser")
                .name("Regular User")
                .email("user@example.com")
                .password("password123")
                .build();

        MvcResult userResult = mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse userResponse = objectMapper.readValue(
                userResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        userToken = userResponse.getToken();
        regularUser = userRepository.findByUsername("regularuser").orElseThrow();
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Non-admin user should get 403 on admin endpoints")
        void nonAdminShouldGetForbidden() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request should get 403")
        void unauthenticatedShouldGetForbidden() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("List Users Tests")
    class ListUsersTests {

        @Test
        @DisplayName("Admin can list users")
        void adminCanListUsers() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Admin can search users by username")
        void adminCanSearchByUsername() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users")
                            .param("search", "regular")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("regularuser"));
        }

        @Test
        @DisplayName("Admin can filter users by enabled status")
        void adminCanFilterByEnabled() throws Exception {
            // Disable regular user
            regularUser.setEnabled(false);
            userRepository.save(regularUser);

            mockMvc.perform(get(ADMIN_URL + "/users")
                            .param("enabled", "false")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("regularuser"));
        }
    }

    @Nested
    @DisplayName("Get User Detail Tests")
    class GetUserDetailTests {

        @Test
        @DisplayName("Admin can view user details")
        void adminCanViewUserDetails() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users/" + regularUser.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(regularUser.getId().toString()))
                    .andExpect(jsonPath("$.username").value("regularuser"))
                    .andExpect(jsonPath("$.email").value("user@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.subscription").exists());
        }

        @Test
        @DisplayName("Admin gets 404 for non-existent user")
        void adminGets404ForNonExistentUser() throws Exception {
            mockMvc.perform(get(ADMIN_URL + "/users/00000000-0000-0000-0000-000000000000")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Enable/Disable User Tests")
    class EnableDisableTests {

        @Test
        @DisplayName("Admin can disable user")
        void adminCanDisableUser() throws Exception {
            AdminUpdateEnabledDTO dto = new AdminUpdateEnabledDTO();
            dto.setEnabled(false);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/enabled")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(updated.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Admin cannot disable themselves")
        void adminCannotDisableThemselves() throws Exception {
            AdminUpdateEnabledDTO dto = new AdminUpdateEnabledDTO();
            dto.setEnabled(false);

            mockMvc.perform(put(ADMIN_URL + "/users/" + adminUser.getId() + "/enabled")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot change your own enabled status"));
        }

        @Test
        @DisplayName("Disabled user cannot authenticate")
        void disabledUserCannotAuthenticate() throws Exception {
            // Disable the user
            regularUser.setEnabled(false);
            userRepository.save(regularUser);

            // Try to use the token
            mockMvc.perform(get("/users/me")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Reset Password Tests")
    class ResetPasswordTests {

        @Test
        @DisplayName("Admin can reset user password")
        void adminCanResetPassword() throws Exception {
            AdminResetPasswordDTO dto = new AdminResetPasswordDTO();
            dto.setNewPassword("newPassword123");

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/password")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            // Verify user can login with new password
            AuthRequest loginRequest = AuthRequest.builder()
                    .username("regularuser")
                    .password("newPassword123")
                    .build();

            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Update Email Tests")
    class UpdateEmailTests {

        @Test
        @DisplayName("Admin can change user email")
        void adminCanChangeEmail() throws Exception {
            AdminUpdateEmailDTO dto = new AdminUpdateEmailDTO();
            dto.setEmail("newemail@example.com");

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/email")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(updated.getEmail()).isEqualTo("newemail@example.com");
        }

        @Test
        @DisplayName("Admin cannot change email to existing email")
        void adminCannotChangeToExistingEmail() throws Exception {
            AdminUpdateEmailDTO dto = new AdminUpdateEmailDTO();
            dto.setEmail("admin@example.com");

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/email")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Email already exists"));
        }
    }

    @Nested
    @DisplayName("Subscription Management Tests")
    class SubscriptionTests {

        @Test
        @DisplayName("Admin can upgrade user subscription")
        void adminCanUpgradeSubscription() throws Exception {
            AdminUpdateSubscriptionDTO dto = new AdminUpdateSubscriptionDTO();
            dto.setPlan(Subscription.SubscriptionPlan.PRO);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/subscription")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(updated.getSubscription().getPlan()).isEqualTo(Subscription.SubscriptionPlan.PRO);
            assertThat(updated.getSubscription().getCreditsBalance()).isEqualTo(Subscription.SubscriptionPlan.PRO.getMonthlyCredits());
        }
    }

    @Nested
    @DisplayName("Credits Management Tests")
    class CreditsTests {

        @Test
        @DisplayName("Admin can set credits balance")
        void adminCanSetCredits() throws Exception {
            AdminAdjustCreditsDTO dto = new AdminAdjustCreditsDTO();
            dto.setAmount(5000);
            dto.setMode(AdminAdjustCreditsDTO.CreditAdjustmentMode.SET);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/credits")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(updated.getSubscription().getCreditsBalance()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Admin can add credits")
        void adminCanAddCredits() throws Exception {
            int initialCredits = regularUser.getSubscription().getCreditsBalance();

            AdminAdjustCreditsDTO dto = new AdminAdjustCreditsDTO();
            dto.setAmount(100);
            dto.setMode(AdminAdjustCreditsDTO.CreditAdjustmentMode.ADD);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/credits")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(updated.getSubscription().getCreditsBalance()).isEqualTo(initialCredits + 100);
        }

        @Test
        @DisplayName("Credits cannot go negative")
        void creditsCannotGoNegative() throws Exception {
            AdminAdjustCreditsDTO dto = new AdminAdjustCreditsDTO();
            dto.setAmount(-999999);
            dto.setMode(AdminAdjustCreditsDTO.CreditAdjustmentMode.ADD);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/credits")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Credits balance cannot go negative"));
        }

        @Test
        @DisplayName("Cannot set negative credits")
        void cannotSetNegativeCredits() throws Exception {
            AdminAdjustCreditsDTO dto = new AdminAdjustCreditsDTO();
            dto.setAmount(-100);
            dto.setMode(AdminAdjustCreditsDTO.CreditAdjustmentMode.SET);

            mockMvc.perform(put(ADMIN_URL + "/users/" + regularUser.getId() + "/credits")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Credits balance cannot be negative"));
        }
    }
}
