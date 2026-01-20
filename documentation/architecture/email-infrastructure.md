# Email Infrastructure

Morning Deck uses email for user communication (welcome, verification) and report delivery. It also receives emails for the email source type.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            OUTBOUND EMAIL                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────┐ │
│  │  EmailService   │───►│ TemplateEngine  │───►│     EmailSender         │ │
│  │  (high-level)   │    │ (Freemarker)    │    │  (AWS/SMTP/Logs)        │ │
│  └─────────────────┘    └─────────────────┘    └─────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                            INBOUND EMAIL                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────────────┐    ┌─────────────────────────────────────────┐  │
│  │   SQSEmailProvider    │───►│ ApplicationEventPublisher               │  │
│  │   (listens SQS)       │    │ EmailReceivedEvent                      │  │
│  └───────────────────────┘    └────────────────────┬────────────────────┘  │
│                                                     │                       │
│  ┌───────────────────────┐                         │                       │
│  │   ImapEmailProvider   │────────────────────────►│                       │
│  │   (polls IMAP)        │                         ▼                       │
│  └───────────────────────┘    ┌─────────────────────────────────────────┐  │
│                               │  EmailIngestionListener                  │  │
│                               │  (creates NewsItems)                     │  │
│                               └─────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Outbound Email

### EmailService

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/emailsend/EmailService.java`

High-level API for sending templated emails:

| Method | Template | Purpose |
|--------|----------|---------|
| `sendWelcomeEmail()` | `welcome.ftl` | New user welcome |
| `sendVerificationEmail()` | `email_verification.ftl` | Email verification link |
| `sendNoCreditsEmail()` | `no_credits.ftl` | Credit exhaustion notification |
| `sendDailyReportEmail()` | `daily_report.ftl` | Daily briefing report |
| `sendPasswordResetByAdminEmail()` | `password_reset_by_admin.ftl` | Admin password reset notification |
| `sendEmailChangedByAdminEmail()` | `email_changed_by_admin.ftl` | Admin email change notification |

### Templates

Freemarker templates in `backend/src/main/resources/templates/email/`:

```
templates/email/
├── welcome.ftl
├── email_verification.ftl
├── no_credits.ftl
├── daily_report.ftl
├── password_reset_by_admin.ftl
└── email_changed_by_admin.ftl
```

Templates receive parameters via `Map<String, Object>`:
- `fullName` - User's display name
- `domain` - Application domain
- `verificationLink` - Email verification URL (for verification template)
- `items` - Report items (for daily report template)

### EmailSender Interface

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/emailsend/sender/EmailSender.java`

```java
public interface EmailSender {
    void send(Email email);
}
```

### Sender Implementations

#### AWSEmailSender (Production)

**File:** `backend/.../emailsend/sender/AWSEmailSender.java`

Uses AWS SES (Simple Email Service):
- Requires AWS credentials
- Production email delivery
- Supports HTML content

```properties
application.email.sender=aws
```

#### SmtpEmailSender

**File:** `backend/.../emailsend/sender/SmtpEmailSender.java`

Uses SMTP protocol:
- Standard SMTP server
- Useful for self-hosted deployments
- Supports any SMTP-compatible service

```properties
application.email.sender=smtp
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=user
spring.mail.password=pass
```

#### LogsEmailSender (Development)

**File:** `backend/.../emailsend/sender/LogsEmailSender.java`

Logs emails instead of sending:
- No external dependencies
- Perfect for local development
- Emails appear in console logs

```properties
application.email.sender=logs
```

### Configuration

```properties
# Sender implementation (aws, smtp, logs)
application.email.sender=aws

# From address
application.email.from=noreply@morningdeck.com

# App display name (appears in From header)
application.display-name=Morning Deck
```

## Inbound Email

For email sources, the system receives emails and extracts news items.

### Email Flow (AWS SES → SQS)

1. **AWS SES** receives email at `*@inbound.yourdomain.com`
2. **SES Rule** stores raw email in **S3**
3. **SES Rule** sends notification to **SNS**
4. **SNS** pushes message to **SQS**
5. **SQSEmailProvider** listens to SQS queue
6. Provider fetches raw email from S3
7. Provider parses email (Jakarta Mail)
8. Provider publishes `EmailReceivedEvent`
9. **EmailIngestionListener** processes event

### EmailMessage

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/emailreceive/EmailMessage.java`

```java
public class EmailMessage {
    private String messageId;
    private String from;
    private List<String> to;
    private String subject;
    private String content;
    private Instant receivedDate;
    private List<AttachmentInfo> attachments;
}
```

### SQSEmailProvider

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/emailreceive/SQSEmailProvider.java`

- Enabled when `application.email.provider=sqs`
- Listens to configured SQS queue
- Parses SNS wrapper → SES message → S3 key
- Fetches and parses MIME message from S3
- Handles multipart emails (extracts text/plain)

### ImapEmailProvider

- Enabled when `application.email.provider=imap`
- Polls IMAP mailbox for new messages
- Alternative to SQS for simpler deployments
- Useful for testing with standard email accounts

### EmailReceivedEvent

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/emailreceive/EmailReceivedEvent.java`

Spring `ApplicationEvent` carrying an `EmailMessage`:

```java
public class EmailReceivedEvent extends ApplicationEvent {
    private final EmailMessage email;

    public EmailReceivedEvent(Object source, EmailMessage email) {
        super(source);
        this.email = email;
    }
}
```

### EmailIngestionListener

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/EmailIngestionListener.java`

Subscribes to `EmailReceivedEvent`:

1. Finds source by recipient email address
2. Stores raw email in `RawEmail` table (audit)
3. Checks user credit availability
4. Calls `AiService.extractFromEmail()` to parse newsletter
5. Creates `NewsItem` entities for extracted items
6. Items enter normal processing pipeline

### Configuration

```properties
# Provider (sqs, imap)
application.email.provider=sqs

# SQS configuration
application.email.sqs.queue-name=incoming-emails

# S3 bucket for raw emails
application.email.s3.bucket-name=email-storage

# IMAP configuration (alternative)
application.email.imap.host=imap.example.com
application.email.imap.port=993
application.email.imap.username=user
application.email.imap.password=pass
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| EmailService | `backend/.../provider/emailsend/EmailService.java` |
| EmailSender Interface | `backend/.../provider/emailsend/sender/EmailSender.java` |
| AWSEmailSender | `backend/.../provider/emailsend/sender/AWSEmailSender.java` |
| SmtpEmailSender | `backend/.../provider/emailsend/sender/SmtpEmailSender.java` |
| LogsEmailSender | `backend/.../provider/emailsend/sender/LogsEmailSender.java` |
| EmailTemplateEngine | `backend/.../provider/emailsend/templating/EmailTemplateEngine.java` |
| FreemarkerTemplateEngine | `backend/.../provider/emailsend/templating/FreemarkerTemplateEngine.java` |
| SQSEmailProvider | `backend/.../provider/emailreceive/SQSEmailProvider.java` |
| ImapEmailProvider | `backend/.../provider/emailreceive/ImapEmailProvider.java` |
| EmailReceivedEvent | `backend/.../provider/emailreceive/EmailReceivedEvent.java` |
| EmailMessage | `backend/.../provider/emailreceive/EmailMessage.java` |
| EmailIngestionListener | `backend/.../core/service/EmailIngestionListener.java` |
| Email Templates | `backend/src/main/resources/templates/email/*.ftl` |

## Related Documentation

- [Sources](../domain/sources.md) - Email source type
- [Users](../domain/users.md) - Email verification flow
- [Briefings](../domain/briefings.md) - Report email delivery
- [Configuration](../operations/configuration.md) - Email configuration options
