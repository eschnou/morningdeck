package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.model.Waitlist;
import be.transcode.morningdeck.server.core.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("WaitlistService Unit Tests")
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    private WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(waitlistRepository);
    }

    @Nested
    @DisplayName("addToWaitlist")
    class AddToWaitlist {

        @Test
        @DisplayName("Should successfully add new email to waitlist")
        void newEmail_succeeds() {
            String email = "test@example.com";
            when(waitlistRepository.existsByEmail(email.toLowerCase())).thenReturn(false);
            when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.addToWaitlist(email);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo(email.toLowerCase());
        }

        @Test
        @DisplayName("Should normalize email to lowercase")
        void uppercaseEmail_normalized() {
            String email = "TEST@EXAMPLE.COM";
            when(waitlistRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.addToWaitlist(email);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should trim whitespace from email")
        void whitespaceEmail_trimmed() {
            String email = "  test@example.com  ";
            when(waitlistRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.addToWaitlist(email);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should throw BadRequest for duplicate email")
        void duplicateEmail_throwsBadRequest() {
            String email = "existing@example.com";
            when(waitlistRepository.existsByEmail(email.toLowerCase())).thenReturn(true);

            assertThatThrownBy(() -> waitlistService.addToWaitlist(email))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Email already on waitlist");

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequest for duplicate email with different case")
        void duplicateEmailDifferentCase_throwsBadRequest() {
            String email = "EXISTING@EXAMPLE.COM";
            when(waitlistRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> waitlistService.addToWaitlist(email))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Email already on waitlist");

            verify(waitlistRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getCount")
    class GetCount {

        @Test
        @DisplayName("Should return count from repository")
        void returnsCount() {
            when(waitlistRepository.count()).thenReturn(42L);

            long result = waitlistService.getCount();

            assertThat(result).isEqualTo(42L);
            verify(waitlistRepository).count();
        }

        @Test
        @DisplayName("Should return zero when waitlist is empty")
        void emptyWaitlist_returnsZero() {
            when(waitlistRepository.count()).thenReturn(0L);

            long result = waitlistService.getCount();

            assertThat(result).isZero();
        }
    }
}
