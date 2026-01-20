package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.model.InviteCode;
import be.transcode.morningdeck.server.core.repository.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("InviteCodeService Unit Tests")
@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private AppConfig appConfig;

    private InviteCodeService inviteCodeService;

    @BeforeEach
    void setUp() {
        inviteCodeService = new InviteCodeService(inviteCodeRepository, appConfig);
    }

    private InviteCode createValidCode(String code) {
        return InviteCode.builder()
                .id(UUID.randomUUID())
                .code(code.toUpperCase())
                .enabled(true)
                .useCount(0)
                .build();
    }

    @Nested
    @DisplayName("validateAndUse")
    class ValidateAndUse {

        @Test
        @DisplayName("Should validate and increment count for valid code")
        void validCode_incrementsCount() {
            InviteCode code = createValidCode("TESTCODE");
            when(inviteCodeRepository.findByCodeIgnoreCase("TESTCODE")).thenReturn(Optional.of(code));
            when(inviteCodeRepository.incrementUseCount(code.getId())).thenReturn(1);

            InviteCode result = inviteCodeService.validateAndUse("testcode");

            assertThat(result).isEqualTo(code);
            verify(inviteCodeRepository).incrementUseCount(code.getId());
        }

        @Test
        @DisplayName("Should handle case-insensitive codes")
        void caseInsensitive_succeeds() {
            InviteCode code = createValidCode("MYCODE");
            when(inviteCodeRepository.findByCodeIgnoreCase("MYCODE")).thenReturn(Optional.of(code));
            when(inviteCodeRepository.incrementUseCount(code.getId())).thenReturn(1);

            InviteCode result = inviteCodeService.validateAndUse("mycode");

            assertThat(result).isEqualTo(code);
            verify(inviteCodeRepository).findByCodeIgnoreCase("MYCODE");
        }

        @Test
        @DisplayName("Should throw BadRequest for non-existent code")
        void codeNotFound_throwsBadRequest() {
            when(inviteCodeRepository.findByCodeIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inviteCodeService.validateAndUse("UNKNOWN"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).incrementUseCount(any());
        }

        @Test
        @DisplayName("Should throw BadRequest for null code")
        void nullCode_throwsBadRequest() {
            assertThatThrownBy(() -> inviteCodeService.validateAndUse(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).findByCodeIgnoreCase(any());
        }

        @Test
        @DisplayName("Should throw BadRequest for blank code")
        void blankCode_throwsBadRequest() {
            assertThatThrownBy(() -> inviteCodeService.validateAndUse("   "))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).findByCodeIgnoreCase(any());
        }

        @Test
        @DisplayName("Should throw BadRequest for disabled code")
        void disabledCode_throwsBadRequest() {
            InviteCode code = createValidCode("DISABLED");
            code.setEnabled(false);
            when(inviteCodeRepository.findByCodeIgnoreCase("DISABLED")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> inviteCodeService.validateAndUse("DISABLED"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).incrementUseCount(any());
        }

        @Test
        @DisplayName("Should throw BadRequest for expired code")
        void expiredCode_throwsBadRequest() {
            InviteCode code = createValidCode("EXPIRED");
            code.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(inviteCodeRepository.findByCodeIgnoreCase("EXPIRED")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> inviteCodeService.validateAndUse("EXPIRED"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).incrementUseCount(any());
        }

        @Test
        @DisplayName("Should throw BadRequest when max uses reached")
        void maxUsesReached_throwsBadRequest() {
            InviteCode code = createValidCode("LIMITED");
            code.setMaxUses(5);
            code.setUseCount(5);
            when(inviteCodeRepository.findByCodeIgnoreCase("LIMITED")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> inviteCodeService.validateAndUse("LIMITED"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired invite code");

            verify(inviteCodeRepository, never()).incrementUseCount(any());
        }

        @Test
        @DisplayName("Should succeed for unlimited uses code")
        void unlimitedUses_succeeds() {
            InviteCode code = createValidCode("UNLIMITED");
            code.setMaxUses(null);
            code.setUseCount(1000);
            when(inviteCodeRepository.findByCodeIgnoreCase("UNLIMITED")).thenReturn(Optional.of(code));
            when(inviteCodeRepository.incrementUseCount(code.getId())).thenReturn(1);

            InviteCode result = inviteCodeService.validateAndUse("UNLIMITED");

            assertThat(result).isEqualTo(code);
            verify(inviteCodeRepository).incrementUseCount(code.getId());
        }

        @Test
        @DisplayName("Should succeed when under max uses limit")
        void underMaxUses_succeeds() {
            InviteCode code = createValidCode("LIMITED");
            code.setMaxUses(10);
            code.setUseCount(5);
            when(inviteCodeRepository.findByCodeIgnoreCase("LIMITED")).thenReturn(Optional.of(code));
            when(inviteCodeRepository.incrementUseCount(code.getId())).thenReturn(1);

            InviteCode result = inviteCodeService.validateAndUse("LIMITED");

            assertThat(result).isEqualTo(code);
            verify(inviteCodeRepository).incrementUseCount(code.getId());
        }

        @Test
        @DisplayName("Should succeed for code with future expiry")
        void futureExpiry_succeeds() {
            InviteCode code = createValidCode("FUTURE");
            code.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
            when(inviteCodeRepository.findByCodeIgnoreCase("FUTURE")).thenReturn(Optional.of(code));
            when(inviteCodeRepository.incrementUseCount(code.getId())).thenReturn(1);

            InviteCode result = inviteCodeService.validateAndUse("FUTURE");

            assertThat(result).isEqualTo(code);
            verify(inviteCodeRepository).incrementUseCount(code.getId());
        }
    }

    @Nested
    @DisplayName("isClosedBeta")
    class IsClosedBeta {

        @Test
        @DisplayName("Should return true when closed beta is enabled")
        void closedBetaEnabled_returnsTrue() {
            when(appConfig.isClosedBeta()).thenReturn(true);

            assertThat(inviteCodeService.isClosedBeta()).isTrue();
        }

        @Test
        @DisplayName("Should return false when closed beta is disabled")
        void closedBetaDisabled_returnsFalse() {
            when(appConfig.isClosedBeta()).thenReturn(false);

            assertThat(inviteCodeService.isClosedBeta()).isFalse();
        }
    }
}
