package be.transcode.morningdeck.server.provider.emailsend;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailPreparationException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import be.transcode.morningdeck.server.provider.emailsend.sender.EmailSender;
import be.transcode.morningdeck.server.provider.emailsend.templating.EmailTemplateEngine;
import be.transcode.morningdeck.server.core.dto.ReportItemDTO;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    private final EmailSender emailSender;
    private final EmailTemplateEngine templateEngine;

    @Value("${application.email.from:noreply@example.com}")
    private String defaultFromAddress;

    private String appDisplayName;

    public EmailService(EmailSender emailSender,
                        EmailTemplateEngine templateEngine,
                        @Value("${application.display-name}") String appDisplayName) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.appDisplayName = appDisplayName;
    }

    public void sendWelcomeEmail(@NotNull String to, @NotNull String fullName, String domain) {
        if (to == null || fullName == null || domain == null) {
            throw new IllegalArgumentException("Email, name and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "domain", domain
        );

        sendEmail("welcome", "Welcome to " + appDisplayName, to, parameters);
    }

    public void sendNoCreditsEmail(@NotNull String to, @NotNull String fullName, String domain) {
        if (to == null || fullName == null || domain == null) {
            throw new IllegalArgumentException("Email, name and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "domain", domain
        );

        sendEmail("no_credits", "[" + appDisplayName + "] You have no credits left", to, parameters);
    }


    public void sendFirstPodcastEmail(@NotNull String to, @NotNull String fullName, String domain, String podcastName, String podcastId) {
        if (to == null || fullName == null || domain == null || podcastId == null) {
            throw new IllegalArgumentException("Email, name and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "domain", domain,
                "podcastName", podcastName,
                "podcastId", podcastId
        );

        sendEmail("first_podcast", "[" + appDisplayName + "] Your first episode is ready!", to, parameters);
    }

    public void sendPasswordResetByAdminEmail(@NotNull String to, @NotNull String fullName, String domain) {
        if (to == null || fullName == null || domain == null) {
            throw new IllegalArgumentException("Email, name and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "domain", domain
        );

        sendEmail("password_reset_by_admin", "[" + appDisplayName + "] Your password has been reset", to, parameters);
    }

    public void sendEmailChangedByAdminEmail(@NotNull String to, @NotNull String fullName, String newEmail, String domain) {
        if (to == null || fullName == null || newEmail == null || domain == null) {
            throw new IllegalArgumentException("Email, name, newEmail and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "newEmail", newEmail,
                "domain", domain
        );

        sendEmail("email_changed_by_admin", "[" + appDisplayName + "] Your email address has been changed", to, parameters);
    }

    public void sendVerificationEmail(@NotNull String to, @NotNull String fullName, String verificationLink,
                                       int expirationHours, String domain) {
        if (to == null || fullName == null || verificationLink == null || domain == null) {
            throw new IllegalArgumentException("Email, name, verificationLink and domain must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "fullName", fullName,
                "verificationLink", verificationLink,
                "expirationHours", expirationHours,
                "domain", domain
        );

        sendEmail("email_verification", "[" + appDisplayName + "] Verify your email address", to, parameters);
    }

    public void sendDailyReportEmail(@NotNull String to, @NotNull String subject,
                                     @NotNull String summary, @NotNull List<ReportItemDTO> items,
                                     @NotNull String domain) {
        if (to == null || subject == null || summary == null || items == null || domain == null) {
            throw new IllegalArgumentException("All parameters must be provided");
        }

        Map<String, Object> parameters = Map.of(
                "subject", subject,
                "summary", summary,
                "items", items,
                "domain", domain
        );

        sendEmail("daily_report", subject, to, parameters);
    }

    private void sendEmail(String templateName, String subject, String to, Map<String, Object> parameters) {
        Email email = prepareEmail(templateName, to, subject, parameters);
        emailSender.send(email);
    }

    private Email prepareEmail(String templateName, String to, String subject, Map<String, Object> parameters) {
        try {
            String content = templateEngine.processTemplate(templateName, parameters);

            return Email.builder()
                    .to(to)
                    .from(appDisplayName + " <" + defaultFromAddress + ">")
                    .subject(subject)
                    .content(content)
                    .build();

        } catch (Exception e) {
            log.error("Failed to prepare email using template: {}", templateName, e);
            throw new EmailPreparationException("Failed to prepare email", e);
        }
    }
}
