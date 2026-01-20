# Email Verification Requirements

## Introduction

Email verification ensures users own the email address they register with. Users receive a verification link after registration and must click it before their account becomes fully active.

## Requirements

### REQ-1: Verification Token Generation

**User Story**: As a system, I want to generate secure verification tokens, so that email verification links cannot be guessed or forged.

**Acceptance Criteria**:
- Generate cryptographically secure random token on registration
- Token stored in database with expiration timestamp
- Token is single-use (invalidated after verification)
- Default expiration: 24 hours (configurable)

### REQ-2: Verification Email

**User Story**: As a new user, I want to receive a verification email after registration, so that I can activate my account.

**Acceptance Criteria**:
- Email sent immediately after registration
- Email contains clickable verification link
- Link format: `{APP_URL}/verify-email?token={token}`
- Email includes expiration information
- Uses existing EmailService infrastructure

### REQ-3: Unverified User State

**User Story**: As a system, I want to restrict unverified users, so that only verified email addresses can access the application.

**Acceptance Criteria**:
- New users created with `emailVerified = false`
- Unverified users cannot login (authentication fails with specific error)
- Unverified users can request new verification email
- JWT token NOT returned on registration (only after verification)

### REQ-4: Email Verification Endpoint

**User Story**: As a user, I want to verify my email by clicking the link, so that my account becomes active.

**Acceptance Criteria**:
- Public GET endpoint `/auth/verify-email?token={token}`
- Valid token: set `emailVerified = true`, delete token, return success
- Expired token: return error with option to resend
- Invalid/used token: return appropriate error
- On success, user can now login and the welcome email is sent to the user

### REQ-6: Registration Flow Change

**User Story**: As a user, I want clear feedback after registration, so that I know to check my email.

**Acceptance Criteria**:
- Registration returns success message (not JWT token)
- Response indicates verification email was sent
- Response includes email address (masked: `j***@example.com`)

### REQ-7: Admin Bypass

**User Story**: As an admin, I want to manually verify users, so that I can help users with email issues.

**Acceptance Criteria**:
- Admin endpoint to set `emailVerified = true`
- Add to existing AdminController
- Analytics event logged for audit

### REQ-8: Configuration

**User Story**: As a developer, I want to configure email verification behavior, so that I can adjust for different environments.

**Acceptance Criteria**:
- `application.email.verification.enabled` - Enable/disable feature (default: true)
- `application.email.verification.expiration-hours` - Token expiration (default: 24)
- When disabled, registration works as before (returns JWT immediately)

## Non-Functional Requirements

### Architecture

- New `EmailVerificationToken` entity with: id, token, userId, expiresAt, createdAt
- New `EmailVerificationTokenRepository`
- Extend `User` entity with `emailVerified` boolean field
- New Flyway migration for schema changes
- Verification logic in `AuthenticationService` or new `EmailVerificationService`

### Security

- Tokens must be cryptographically random (use `SecureRandom`)
- Tokens should be URL-safe (Base64 URL encoding)
- Token length: minimum 32 bytes
- Tokens stored hashed in database (like passwords)
- Rate limiting on resend endpoint to prevent abuse
- Verification endpoint should not leak user existence

### Database Changes

- Add `email_verified` column to users table (BOOLEAN, NOT NULL, DEFAULT FALSE)
- New `email_verification_tokens` table:
  - id (UUID, PK)
  - user_id (UUID, FK to users)
  - token_hash (VARCHAR, indexed)
  - expires_at (TIMESTAMP)
  - created_at (TIMESTAMP)

### API Design

- `POST /auth/register` - Returns message, not token
- `GET /auth/verify-email?token={token}` - Verify email
- `POST /auth/resend-verification` - Request new verification email
- `PUT /admin/users/{id}/verify-email` - Admin manual verification

### Email Template

- Template name: `email_verification`
- Variables: fullName, verificationLink, expirationHours, domain
- Clear call-to-action button
- Expiration warning
