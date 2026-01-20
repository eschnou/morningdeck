package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
        "application.closed-beta=false",
        "application.email.verification.enabled=false"
})
@DisplayName("Invite Code - Closed Beta Disabled")
class InviteCodeDisabledIT {

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

    @Test
    @DisplayName("Registration without invite code succeeds when closed beta disabled")
    void register_closedBetaDisabled_noCodeRequired() throws Exception {
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
                .andExpect(jsonPath("$.token").exists());

        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(user.getInviteCode()).isNull();
    }
}
