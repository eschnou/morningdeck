package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.DailyReportDTO;
import be.transcode.morningdeck.server.core.dto.ReportItemDTO;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.provider.ai.AiService;
import be.transcode.morningdeck.server.provider.ai.AiUsageContext;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.emailsend.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for sending daily report emails.
 * Orchestrates AI content generation and email delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportEmailDeliveryService {

    private final AiService aiService;
    private final EmailService emailService;
    private final UserService userService;

    @Value("${application.root-domain:localhost}")
    private String domain;

    /**
     * Send a report email if email delivery is enabled for the briefing.
     *
     * @param dayBrief The briefing configuration
     * @param report   The generated report with items
     */
    public void sendReportEmail(DayBrief dayBrief, DailyReportDTO report) {
        if (!dayBrief.isEmailDeliveryEnabled()) {
            log.debug("Email delivery disabled for briefing {}", dayBrief.getId());
            return;
        }

        if (report.getItems() == null || report.getItems().isEmpty()) {
            log.debug("Skipping email for empty report {}", report.getId());
            return;
        }

        User user;
        try {
            user = userService.getInternalUserById(dayBrief.getUserId());
        } catch (ResourceNotFoundException e) {
            log.error("Cannot send report email for briefing {}: user {} not found",
                    dayBrief.getId(), dayBrief.getUserId());
            return;
        }

        try {
            // Set user context for usage tracking
            AiUsageContext.setUserId(dayBrief.getUserId());

            // Format items for AI prompt
            String formattedItems = formatItemsForAi(report.getItems());

            // Generate AI content
            ReportEmailContent emailContent = aiService.generateReportEmailContent(
                    dayBrief.getTitle(),
                    dayBrief.getDescription(),
                    formattedItems
            );

            // Build full subject: "{briefing name} report: {AI subject}"
            String fullSubject = dayBrief.getTitle() + " report: " + emailContent.subject();

            // Send email
            emailService.sendDailyReportEmail(
                    user.getEmail(),
                    fullSubject,
                    emailContent.summary(),
                    report.getItems(),
                    domain
            );

            log.info("Sent report email for briefing {} to {}", dayBrief.getId(), user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send report email for briefing {}: {}", dayBrief.getId(), e.getMessage(), e);
            // Don't rethrow - email failures shouldn't fail the entire report generation
        } finally {
            AiUsageContext.clear();
        }
    }

    /**
     * Format report items into a string suitable for the AI prompt.
     */
    private String formatItemsForAi(List<ReportItemDTO> items) {
        StringBuilder sb = new StringBuilder();
        for (ReportItemDTO item : items) {
            sb.append(String.format("#%d: %s (Score: %d)\n%s\n\n",
                    item.getPosition(),
                    item.getTitle(),
                    item.getScore() != null ? item.getScore() : 0,
                    item.getSummary() != null ? item.getSummary() : "No summary available"
            ));
        }
        return sb.toString();
    }
}
