package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.WaitlistRequest;
import be.transcode.morningdeck.server.core.model.Waitlist;
import be.transcode.morningdeck.server.core.repository.WaitlistRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@Transactional
class WaitlistIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WaitlistRepository waitlistRepository;

    private static final String JOIN_URL = "/waitlist/join";
    private static final String STATS_URL = "/waitlist/stats";

    @BeforeEach
    void setUp() {
        waitlistRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /waitlist/join")
    class JoinWaitlist {

        @Test
        @DisplayName("Should successfully add email to waitlist")
        void validEmail_succeeds() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("test@example.com")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Verify email is in database
            assertThat(waitlistRepository.existsByEmail("test@example.com")).isTrue();
        }

        @Test
        @DisplayName("Should normalize email to lowercase")
        void uppercaseEmail_normalizedAndSaved() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("TEST@EXAMPLE.COM")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Verify email is stored lowercase
            assertThat(waitlistRepository.existsByEmail("test@example.com")).isTrue();
            assertThat(waitlistRepository.existsByEmail("TEST@EXAMPLE.COM")).isFalse();
        }

        @Test
        @DisplayName("Should fail with duplicate email")
        void duplicateEmail_returnsBadRequest() throws Exception {
            // First signup
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("duplicate@example.com")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Second signup with same email
            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Email already on waitlist"));

            // Verify only one entry exists
            assertThat(waitlistRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should fail with duplicate email different case")
        void duplicateEmailDifferentCase_returnsBadRequest() throws Exception {
            // First signup with lowercase
            WaitlistRequest request1 = WaitlistRequest.builder()
                    .email("test@example.com")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isOk());

            // Second signup with uppercase
            WaitlistRequest request2 = WaitlistRequest.builder()
                    .email("TEST@EXAMPLE.COM")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail with empty email")
        void emptyEmail_returnsBadRequest() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail with null email")
        void nullEmail_returnsBadRequest() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email(null)
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail with invalid email format")
        void invalidEmailFormat_returnsBadRequest() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("not-an-email")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail with missing @ symbol")
        void emailMissingAtSymbol_returnsBadRequest() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("testexample.com")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /waitlist/stats")
    class GetStats {

        @Test
        @DisplayName("Should return zero count when waitlist is empty")
        void emptyWaitlist_returnsZero() throws Exception {
            mockMvc.perform(get(STATS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        @DisplayName("Should return correct count after signups")
        void withSignups_returnsCorrectCount() throws Exception {
            // Add some entries directly to repository
            waitlistRepository.save(Waitlist.builder().email("user1@example.com").build());
            waitlistRepository.save(Waitlist.builder().email("user2@example.com").build());
            waitlistRepository.save(Waitlist.builder().email("user3@example.com").build());

            mockMvc.perform(get(STATS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(3));
        }

        @Test
        @DisplayName("Should return updated count after new signup")
        void afterNewSignup_returnsUpdatedCount() throws Exception {
            // Initial count
            mockMvc.perform(get(STATS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));

            // Add signup
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("new@example.com")
                    .build();

            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Updated count
            mockMvc.perform(get(STATS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1));
        }
    }

    @Nested
    @DisplayName("Public Access")
    class PublicAccess {

        @Test
        @DisplayName("Join endpoint should be accessible without authentication")
        void joinEndpoint_noAuthRequired() throws Exception {
            WaitlistRequest request = WaitlistRequest.builder()
                    .email("public@example.com")
                    .build();

            // No auth token provided - should still work
            mockMvc.perform(post(JOIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Stats endpoint should be accessible without authentication")
        void statsEndpoint_noAuthRequired() throws Exception {
            // No auth token provided - should still work
            mockMvc.perform(get(STATS_URL))
                    .andExpect(status().isOk());
        }
    }
}
