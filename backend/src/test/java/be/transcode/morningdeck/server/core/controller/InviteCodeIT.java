package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.model.InviteCode;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.InviteCodeRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "application.closed-beta=true",
        "application.email.verification.enabled=false"
})
@DisplayName("Invite Code - Closed Beta Enabled")
class InviteCodeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    private static final String AUTH_URL = "/auth";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        inviteCodeRepository.deleteAll();
    }

    private InviteCode createInviteCode(String code, boolean enabled, Integer maxUses, Instant expiresAt) {
        return inviteCodeRepository.save(InviteCode.builder()
                .code(code.toUpperCase())
                .enabled(enabled)
                .maxUses(maxUses)
                .useCount(0)
                .expiresAt(expiresAt)
                .build());
    }

    @Test
    @DisplayName("Registration with valid invite code succeeds")
    void register_withValidInviteCode_succeeds() throws Exception {
        createInviteCode("VALIDCODE", true, null, null);

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("validcode")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        assertThat(userRepository.findByUsername("testuser")).isPresent();
    }

    @Test
    @DisplayName("Registration with invalid invite code returns 400")
    void register_withInvalidInviteCode_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("INVALIDCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired invite code"));

        assertThat(userRepository.findByUsername("testuser")).isEmpty();
    }

    @Test
    @DisplayName("Registration with expired invite code returns 400")
    void register_withExpiredInviteCode_returns400() throws Exception {
        createInviteCode("EXPIREDCODE", true, null, Instant.now().minus(1, ChronoUnit.HOURS));

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("EXPIREDCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired invite code"));
    }

    @Test
    @DisplayName("Registration with disabled invite code returns 400")
    void register_withDisabledCode_returns400() throws Exception {
        createInviteCode("DISABLEDCODE", false, null, null);

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("DISABLEDCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired invite code"));
    }

    @Test
    @DisplayName("Registration with exhausted invite code returns 400")
    void register_withExhaustedCode_returns400() throws Exception {
        InviteCode code = createInviteCode("LIMITEDCODE", true, 1, null);
        code.setUseCount(1);
        inviteCodeRepository.save(code);

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("LIMITEDCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired invite code"));
    }

    @Test
    @DisplayName("Code usage count increments after registration")
    void register_codeUsageIncremented_afterRegistration() throws Exception {
        InviteCode code = createInviteCode("TRACKCODE", true, 10, null);
        int initialCount = code.getUseCount();

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("TRACKCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        InviteCode updatedCode = inviteCodeRepository.findByCodeIgnoreCase("TRACKCODE").orElseThrow();
        assertThat(updatedCode.getUseCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("User is linked to invite code after registration")
    void register_userLinkedToInviteCode_afterRegistration() throws Exception {
        InviteCode code = createInviteCode("LINKCODE", true, null, null);

        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .inviteCode("LINKCODE")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(user.getInviteCode()).isNotNull();
        assertThat(user.getInviteCode().getId()).isEqualTo(code.getId());
    }

    @Test
    @DisplayName("Registration without invite code returns 400")
    void register_withoutInviteCode_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired invite code"));
    }
}
