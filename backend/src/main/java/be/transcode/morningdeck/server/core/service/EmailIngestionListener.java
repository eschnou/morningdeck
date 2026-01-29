package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.RawEmail;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.RawEmailRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.ai.AiService;
import be.transcode.morningdeck.server.provider.ai.AiUsageContext;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.emailreceive.EmailMessage;
import be.transcode.morningdeck.server.provider.emailreceive.EmailReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Listens for incoming emails and processes them for EMAIL type sources.
 * Extracts news items using AI and creates NewsItem entities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailIngestionListener {

    private final SourceRepository sourceRepository;
    private final NewsItemRepository newsItemRepository;
    private final RawEmailRepository rawEmailRepository;
    private final AiService aiService;
    private final HtmlToMarkdownConverter htmlToMarkdownConverter;
    private final SubscriptionService subscriptionService;

    @Value("${application.email.domain:inbound.morningdeck.com}")
    private String emailDomain;

    // Pattern to extract UUID from email address like: uuid@inbound.morningdeck.com
    private static final Pattern EMAIL_UUID_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})@"
    );

    @EventListener
    @Async
    @Transactional
    public void handleEmailReceived(EmailReceivedEvent event) {
        EmailMessage email = event.getEmail();
        log.info("Processing incoming email: {} from {}", email.getSubject(), email.getFrom());

        // Extract UUID from recipient address
        UUID emailUuid = extractEmailUuid(email.getTo());
        if (emailUuid == null) {
            log.warn("No valid recipient found in email {} - recipients: {}", email.getMessageId(), email.getTo());
            return;
        }

        // Find source by email address
        Source source = sourceRepository.findByEmailAddress(emailUuid).orElse(null);
        if (source == null) {
            log.warn("No source found for email address UUID: {}", emailUuid);
            return;
        }

        if (source.getStatus() != SourceStatus.ACTIVE) {
            log.warn("Source {} is not active (status: {}), skipping email {}", source.getId(), source.getStatus(), email.getMessageId());
            return;
        }

        log.info("Processing email {} for source {} ({})", email.getMessageId(), source.getId(), source.getName());

        // Store raw email for audit
        storeRawEmail(source, email);

        // Process the email and extract news items
        processEmail(source, email);
    }

    private void processEmail(Source source, EmailMessage email) {
        // Set user context for usage tracking
        UUID userId = source.getDayBrief().getUserId();

        // Check credits before AI extraction
        if (!subscriptionService.hasCredits(userId)) {
            log.warn("Email received but user {} has no credits. Stored but not processed: messageId={}",
                    userId, email.getMessageId());
            return;
        }

        try {
            AiUsageContext.setUserId(userId);

            List<ExtractedNewsItem> items = aiService.extractFromEmail(email.getSubject(), email.getContent());

            log.info("Extracted {} items from email {} for source {}", items.size(), email.getMessageId(), source.getId());

            for (int i = 0; i < items.size(); i++) {
                createNewsItem(source, email, items.get(i), i);
            }

            // Update source fetch timestamp
            source.setLastFetchedAt(Instant.now());
            source.setLastError(null);
            sourceRepository.save(source);

        } catch (Exception e) {
            log.error("Failed to extract items from email {}: {}", email.getMessageId(), e.getMessage(), e);
            // Create fallback item on extraction failure
            String markdownContent = htmlToMarkdownConverter.convert(email.getContent());
            createFallbackNewsItem(source, email, markdownContent);
        } finally {
            AiUsageContext.clear();
        }
    }

    private void createNewsItem(Source source, EmailMessage email, ExtractedNewsItem item, int index) {
        String guid = email.getMessageId() + "#" + index;

        // Skip if already exists (idempotency)
        if (newsItemRepository.existsBySourceIdAndGuid(source.getId(), guid)) {
            log.debug("NewsItem with guid {} already exists, skipping", guid);
            return;
        }

        // Use extracted URL or fallback to mailto: link, truncate to fit column
        String link = item.url() != null && !item.url().isBlank()
                ? truncate(item.url(), 4096)
                : "mailto:" + email.getMessageId();

        NewsItem newsItem = NewsItem.builder()
                .source(source)
                .guid(guid)
                .title(truncate(item.title(), 1024))
                .link(link)
                .author(email.getFrom())
                .publishedAt(email.getReceivedDate())
                .rawContent(email.getContent())
                .cleanContent(item.summary())
                .summary(item.summary())
                .status(NewsItemStatus.NEW) // Will be processed by the standard processing pipeline
                .build();

        newsItemRepository.save(newsItem);
        log.debug("Created NewsItem {} for source {}", newsItem.getId(), source.getId());
    }

    private void createFallbackNewsItem(Source source, EmailMessage email, String errorMessage) {
        String guid = email.getMessageId() + "#fallback";

        // Skip if already exists
        if (newsItemRepository.existsBySourceIdAndGuid(source.getId(), guid)) {
            log.debug("Fallback NewsItem with guid {} already exists, skipping", guid);
            return;
        }

        NewsItem newsItem = NewsItem.builder()
                .source(source)
                .guid(guid)
                .title(truncate(email.getSubject(), 1024))
                .link("mailto:" + email.getMessageId())
                .author(email.getFrom())
                .publishedAt(email.getReceivedDate())
                .rawContent(email.getContent())
                .status(NewsItemStatus.ERROR)
                .errorMessage(truncate("AI extraction failed: " + errorMessage, 1024))
                .build();

        newsItemRepository.save(newsItem);

        // Update source with error
        source.setLastError("Email extraction failed: " + errorMessage);
        sourceRepository.save(source);

        log.warn("Created fallback NewsItem {} for source {} due to extraction failure", newsItem.getId(), source.getId());
    }

    private void storeRawEmail(Source source, EmailMessage email) {
        // Skip if already stored (idempotency)
        if (rawEmailRepository.existsBySourceIdAndMessageId(source.getId(), email.getMessageId())) {
            log.debug("Raw email {} already stored for source {}", email.getMessageId(), source.getId());
            return;
        }

        RawEmail rawEmail = RawEmail.builder()
                .source(source)
                .messageId(email.getMessageId())
                .fromAddress(email.getFrom())
                .subject(email.getSubject())
                .rawContent(email.getContent())
                .receivedAt(email.getReceivedDate())
                .build();

        rawEmailRepository.save(rawEmail);
        log.debug("Stored raw email {} for source {}", email.getMessageId(), source.getId());
    }

    /**
     * Extracts the UUID from recipient email addresses.
     * Looks for addresses matching pattern: {uuid}@{emailDomain}
     */
    private UUID extractEmailUuid(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return null;
        }

        for (String recipient : recipients) {
            // Check if this recipient is for our email domain
            if (!recipient.toLowerCase().endsWith("@" + emailDomain.toLowerCase())) {
                continue;
            }

            Matcher matcher = EMAIL_UUID_PATTERN.matcher(recipient);
            if (matcher.find()) {
                try {
                    return UUID.fromString(matcher.group(1));
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid UUID in email address: {}", recipient);
                }
            }
        }

        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("Truncating value from {} to {} characters", value.length(), maxLength);
        return value.substring(0, maxLength);
    }
}
