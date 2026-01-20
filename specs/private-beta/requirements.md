# Private Beta Feature - Requirements

## 1. Introduction

Private beta mode enables controlled access to Morning Deck during early launch phases. When enabled, users must provide a valid invite code during registration. The system tracks invite code usage to prevent abuse and records which code each user used for future referral analytics.

## 2. Alignment with Product Vision

Per `product.md`, Morning Deck targets knowledge professionals and content creators. Private beta supports:
- **Controlled growth** during early product validation
- **Quality user acquisition** through invitation-based access
- **Referral foundation** by tracking code-to-user relationships for future organic growth (target: >30% of signups from referrals)

## 3. Functional Requirements

### 3.1 Private Beta Mode Toggle

**User Story:** As an operator, I want to enable/disable private beta mode via configuration, so that I can control access without code changes.

**Acceptance Criteria:**
- Backend: `application.closed-beta=true/false` in `application.properties` controls private beta mode
- Frontend: `VITE_REQUIRE_INVITE_CODE=true/false` in `.env` controls invite code field visibility
- When disabled, registration works without invite code (current behavior)
- When enabled, invite code is mandatory for registration

### 3.2 Invite Code Entity

**User Story:** As an operator, I want invite codes stored in the database with usage tracking, so that I can manage and monitor code distribution.

**Acceptance Criteria:**
- Database table `invite_code` with fields:
  - `id` (UUID, primary key)
  - `code` (String, unique, indexed) - the invite code string
  - `description` (String, nullable) - admin note about code purpose/recipient
  - `max_uses` (Integer, nullable) - maximum allowed uses (null = unlimited)
  - `use_count` (Integer, default 0) - current usage count
  - `enabled` (Boolean, default true) - code can be used
  - `expires_at` (Instant, nullable) - expiration timestamp (null = never expires)
  - `created_at` (Instant) - creation timestamp
- Codes are case-insensitive (normalize to uppercase on storage and lookup)

### 3.3 Invite Code Validation

**User Story:** As a prospective user, I want to register with a valid invite code, so that I can access the private beta.

**Acceptance Criteria:**
- When private beta is enabled, registration requires `inviteCode` field
- Validation checks:
  1. Code exists in database
  2. Code is enabled
  3. Code has not expired (if `expires_at` is set)
  4. Code has not exceeded `max_uses` (if set)
- On validation failure, return HTTP 400 with message "Invalid or expired invite code"
- On validation success, increment `use_count` atomically

### 3.4 User-Invite Code Tracking

**User Story:** As an operator, I want to know which invite code each user registered with, so that I can analyze acquisition channels and enable future referral features.

**Acceptance Criteria:**
- Add `invite_code_id` (UUID, nullable, foreign key) to `users` table
- On successful registration with invite code, store reference to the invite code used
- User profile API does not expose invite code (internal tracking only)

### 3.5 URL Pre-population

**User Story:** As a prospective user, I want my invite code pre-filled when I click a shared link, so that registration is seamless.

**Acceptance Criteria:**
- Registration page reads `?code=XXXX` query parameter from URL
- If present, pre-populate invite code field
- Field remains editable (user can correct if needed)

## 4. Non-Functional Requirements

### 4.1 Architecture

- Follow existing service layer patterns (InviteCodeService)
- Use JPA repository for database access (InviteCodeRepository)
- Integrate validation into existing AuthenticationService.register() flow
- Maintain backward compatibility: when private beta is disabled, no invite code logic executes

### 4.2 Security

- Do not leak whether a code exists vs. is exhausted (generic error message)

### 4.3 Reliability

- Use database transactions for atomic use_count increment
- Handle concurrent registration attempts safely (optimistic locking or atomic updates)

### 4.4 Usability

- Clear error messaging when invite code is invalid
- Frontend shows invite code field only when `VITE_REQUIRE_INVITE_CODE=true`
- Pre-populated codes from URL improve conversion

## 5. Migration from Current Implementation

The current implementation uses a static list of codes in `application.properties`:
```properties
application.closed-beta=true
application.invite-codes=CODE1,CODE2,CODE3
```

Migration path:
1. Create `invite_code` table via Flyway migration
2. Update AuthenticationService to use InviteCodeService instead of list lookup
3. Remove `application.invite-codes` property after migration
4. Keep `application.closed-beta` property for mode toggle

## 6. Data Model Summary

```
┌─────────────────────┐         ┌─────────────────────┐
│     invite_code     │         │        users        │
├─────────────────────┤         ├─────────────────────┤
│ id (PK)             │◄────────│ invite_code_id (FK) │
│ code (unique)       │         │ ...                 │
│ description         │         └─────────────────────┘
│ max_uses            │
│ use_count           │
│ enabled             │
│ expires_at          │
│ created_at          │
└─────────────────────┘
```
