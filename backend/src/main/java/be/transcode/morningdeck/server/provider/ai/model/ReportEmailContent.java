package be.transcode.morningdeck.server.provider.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * AI-generated content for daily report emails.
 * Contains subject line and summary for the email body.
 */
@JsonPropertyOrder({"subject", "summary"})
public record ReportEmailContent(
        String subject,
        String summary
) {
}
