package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.RawEmailRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.ai.AiService;
import java.util.Set;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.emailreceive.EmailMessage;
import be.transcode.morningdeck.server.provider.emailreceive.EmailReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailIngestionListener Unit Tests")
class EmailIngestionListenerTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private NewsItemRepository newsItemRepository;

    @Mock
    private RawEmailRepository rawEmailRepository;

    @Mock
    private AiService aiService;

    @Mock
    private HtmlToMarkdownConverter htmlToMarkdownConverter;

    @Mock
    private SubscriptionService subscriptionService;

    private EmailIngestionListener listener;

    private static final String EMAIL_DOMAIN = "inbound.morningdeck.com";

    @BeforeEach
    void setUp() {
        listener = new EmailIngestionListener(
                sourceRepository,
                newsItemRepository,
                rawEmailRepository,
                aiService,
                htmlToMarkdownConverter,
                subscriptionService
        );
        ReflectionTestUtils.setField(listener, "emailDomain", EMAIL_DOMAIN);
        // Default: user has credits
        // Use lenient() because not all tests reach the credit check (some bail out earlier)
        lenient().when(subscriptionService.hasCredits(any(UUID.class))).thenReturn(true);
    }

    @Nested
    @DisplayName("Email Routing Tests")
    class EmailRoutingTests {

        @Test
        @DisplayName("Should find source by email address UUID")
        void shouldFindSourceByEmailAddressUuid() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;

            Source source = createTestSource(emailUuid);
            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(aiService.extractFromEmail(any(), any())).thenReturn(List.of(
                    new ExtractedNewsItem("Test Title", "Test Summary", null)
            ));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(false);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(sourceRepository).findByEmailAddress(emailUuid);
            verify(aiService).extractFromEmail(eq(email.getSubject()), any());
        }

        @Test
        @DisplayName("Should ignore email with invalid recipient domain")
        void shouldIgnoreEmailWithInvalidRecipientDomain() {
            String recipient = UUID.randomUUID() + "@other-domain.com";

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(sourceRepository, never()).findByEmailAddress(any());
            verify(aiService, never()).extractFromEmail(any(), any());
        }

        @Test
        @DisplayName("Should ignore email with no matching source")
        void shouldIgnoreEmailWithNoMatchingSource() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.empty());

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(sourceRepository).findByEmailAddress(emailUuid);
            verify(aiService, never()).extractFromEmail(any(), any());
        }

        @Test
        @DisplayName("Should ignore email for paused source")
        void shouldIgnoreEmailForPausedSource() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;

            Source source = createTestSource(emailUuid);
            source.setStatus(SourceStatus.PAUSED);
            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(aiService, never()).extractFromEmail(any(), any());
        }
    }

    @Nested
    @DisplayName("News Item Creation Tests")
    class NewsItemCreationTests {

        @Test
        @DisplayName("Should create news items from AI extraction")
        void shouldCreateNewsItemsFromAiExtraction() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;
            Source source = createTestSource(emailUuid);

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(aiService.extractFromEmail(any(), any())).thenReturn(List.of(
                    new ExtractedNewsItem("Title 1", "Summary 1", "http://example.com/1"),
                    new ExtractedNewsItem("Title 2", "Summary 2", null)
            ));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(false);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
            verify(newsItemRepository, times(2)).save(captor.capture());

            List<NewsItem> savedItems = captor.getAllValues();
            assertThat(savedItems).hasSize(2);

            NewsItem item1 = savedItems.get(0);
            assertThat(item1.getTitle()).isEqualTo("Title 1");
            assertThat(item1.getSummary()).isEqualTo("Summary 1");
            assertThat(item1.getLink()).isEqualTo("http://example.com/1");
            assertThat(item1.getGuid()).contains("#0");

            NewsItem item2 = savedItems.get(1);
            assertThat(item2.getTitle()).isEqualTo("Title 2");
            assertThat(item2.getLink()).startsWith("mailto:");
        }

        @Test
        @DisplayName("Should skip duplicate items by GUID")
        void shouldSkipDuplicateItemsByGuid() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;
            Source source = createTestSource(emailUuid);

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(aiService.extractFromEmail(any(), any())).thenReturn(List.of(
                    new ExtractedNewsItem("Title 1", "Summary 1", null)
            ));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(true);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(newsItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create fallback item on AI extraction failure")
        void shouldCreateFallbackItemOnAiFailure() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;
            Source source = createTestSource(emailUuid);

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(htmlToMarkdownConverter.convert(any())).thenReturn("converted content");
            when(aiService.extractFromEmail(any(), any())).thenThrow(new RuntimeException("AI error"));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(false);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
            verify(newsItemRepository).save(captor.capture());

            NewsItem fallbackItem = captor.getValue();
            assertThat(fallbackItem.getTitle()).isEqualTo(email.getSubject());
            assertThat(fallbackItem.getStatus()).isEqualTo(NewsItemStatus.ERROR);
            assertThat(fallbackItem.getErrorMessage()).contains("AI extraction failed");
        }
    }

    @Nested
    @DisplayName("Raw Email Storage Tests")
    class RawEmailStorageTests {

        @Test
        @DisplayName("Should store raw email in database for audit")
        void shouldStoreRawEmailInDatabase() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;
            Source source = createTestSource(emailUuid);

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(rawEmailRepository.existsBySourceIdAndMessageId(any(), any())).thenReturn(false);
            when(aiService.extractFromEmail(any(), any())).thenReturn(List.of(
                    new ExtractedNewsItem("Title", "Summary", null)
            ));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(false);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            ArgumentCaptor<RawEmail> captor = ArgumentCaptor.forClass(RawEmail.class);
            verify(rawEmailRepository).save(captor.capture());

            RawEmail savedEmail = captor.getValue();
            assertThat(savedEmail.getMessageId()).isEqualTo(email.getMessageId());
            assertThat(savedEmail.getFromAddress()).isEqualTo(email.getFrom());
            assertThat(savedEmail.getSubject()).isEqualTo(email.getSubject());
            assertThat(savedEmail.getRawContent()).isEqualTo(email.getContent());
        }

        @Test
        @DisplayName("Should skip storing duplicate raw email")
        void shouldSkipStoringDuplicateRawEmail() {
            UUID emailUuid = UUID.randomUUID();
            String recipient = emailUuid + "@" + EMAIL_DOMAIN;
            Source source = createTestSource(emailUuid);

            when(sourceRepository.findByEmailAddress(emailUuid)).thenReturn(Optional.of(source));
            when(rawEmailRepository.existsBySourceIdAndMessageId(any(), any())).thenReturn(true);
            when(aiService.extractFromEmail(any(), any())).thenReturn(List.of(
                    new ExtractedNewsItem("Title", "Summary", null)
            ));
            when(newsItemRepository.existsBySourceIdAndGuid(any(), any())).thenReturn(false);

            EmailMessage email = createTestEmail(recipient);
            listener.handleEmailReceived(new EmailReceivedEvent(this, email));

            verify(rawEmailRepository, never()).save(any());
        }
    }

    private Source createTestSource(UUID emailUuid) {
        DayBrief dayBrief = DayBrief.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .title("Test Brief")
                .position(0)
                .build();

        return Source.builder()
                .id(UUID.randomUUID())
                .dayBrief(dayBrief)
                .name("Test Source")
                .type(SourceType.EMAIL)
                .emailAddress(emailUuid)
                .status(SourceStatus.ACTIVE)
                .build();
    }

    private EmailMessage createTestEmail(String recipient) {
        return EmailMessage.builder()
                .messageId("test-message-id-" + UUID.randomUUID())
                .from("sender@example.com")
                .to(List.of(recipient))
                .subject("Test Newsletter Subject")
                .content("<html><body>Test content</body></html>")
                .receivedDate(Instant.now())
                .build();
    }
}
