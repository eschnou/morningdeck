# Private Beta Feature - Design Document

## 1. Overview

Database-backed invite code system replacing the current static configuration list. Enables usage tracking, expiration, and user-to-code relationship for future referral analytics.

**Key changes:**
- New `InviteCode` entity and `invite_codes` table
- `InviteCodeService` for validation and usage tracking
- User entity extended with `inviteCode` foreign key
- Frontend reads `?code=` URL parameter for pre-population

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Registration Flow                            │
└─────────────────────────────────────────────────────────────────────┘

  Frontend (Register.tsx)
       │
       │ POST /auth/register { inviteCode: "ABC123", ... }
       ▼
  AuthController
       │
       ▼
  AuthenticationService.register()
       │
       ├── [if closedBeta] ──► InviteCodeService.validateAndUse(code)
       │                              │
       │                              ├── findByCode()
       │                              ├── validate (enabled, expired, maxUses)
       │                              └── incrementUseCount() [atomic]
       │                              │
       │                              ▼
       │                         InviteCode entity
       │
       ▼
  UserService.createUser(..., inviteCode)
       │
       └── User entity (with inviteCode FK)
```

## 3. Components and Interfaces

### 3.1 Backend Components

#### InviteCode Entity
**Location:** `be.transcode.morningdeck.server.core.model.InviteCode`

```java
@Entity
@Table(name = "invite_codes")
public class InviteCode {
    UUID id;                    // PK, auto-generated
    String code;                // unique, indexed, stored uppercase
    String description;         // nullable, admin notes
    Integer maxUses;            // nullable = unlimited
    int useCount;               // default 0
    boolean enabled;            // default true
    Instant expiresAt;          // nullable = never expires
    Instant createdAt;          // auto-set @PrePersist
}
```

#### InviteCodeRepository
**Location:** `be.transcode.morningdeck.server.core.repository.InviteCodeRepository`

```java
public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {
    Optional<InviteCode> findByCode(String code);

    @Modifying
    @Query("UPDATE InviteCode i SET i.useCount = i.useCount + 1 WHERE i.id = :id")
    int incrementUseCount(@Param("id") UUID id);
}
```

#### InviteCodeService
**Location:** `be.transcode.morningdeck.server.core.service.InviteCodeService`

**Public methods:**
- `InviteCode validateAndUse(String code)` - Validates code, increments usage atomically, returns entity
- `boolean isClosedBeta()` - Delegates to AppConfig

**Validation logic:**
1. Normalize code to uppercase
2. Lookup by code (throw BadRequestException if not found)
3. Check `enabled == true`
4. Check `expiresAt == null || expiresAt > now`
5. Check `maxUses == null || useCount < maxUses`
6. Increment `useCount` atomically
7. Return entity for user association

**Error handling:** All validation failures return same generic message: "Invalid or expired invite code"

#### User Entity Update
**Location:** `be.transcode.morningdeck.server.core.model.User`

Add field:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "invite_code_id")
private InviteCode inviteCode;
```

#### UserService Update
**Location:** `be.transcode.morningdeck.server.core.service.UserService`

Update `createUser()` signature to accept optional `InviteCode`:
```java
public UserProfileDTO createUser(String username, String name, String email,
    String password, Language language, boolean sendWelcomeEmail, InviteCode inviteCode)
```

Set `user.setInviteCode(inviteCode)` before persisting.

#### AuthenticationService Update
**Location:** `be.transcode.morningdeck.server.core.service.AuthenticationService`

Replace current invite code validation:
```java
// Before (current)
if (appConfig.isClosedBeta()) {
    if (!appConfig.getInviteCodes().contains(request.getInviteCode())) {
        throw new BadRequestException("Invalid invite code");
    }
}

// After (new)
InviteCode inviteCode = null;
if (appConfig.isClosedBeta()) {
    inviteCode = inviteCodeService.validateAndUse(request.getInviteCode());
}
// Pass inviteCode to userService.createUser()
```

### 3.2 Frontend Components

#### Register.tsx Updates

1. **Read URL parameter on mount:**
```tsx
import { useSearchParams } from 'react-router-dom';

const [searchParams] = useSearchParams();
const initialCode = searchParams.get('code') || '';

const [formData, setFormData] = useState({
    // ...existing fields
    inviteCode: initialCode,
});
```

2. **No changes to form submission** - already sends `inviteCode` field

### 3.3 Configuration

**Backend (`application.properties`):**
- Keep existing `application.closed-beta=true/false`
- Remove `application.invite-codes` after migration

**Frontend (`.env`):**
- Keep existing `VITE_REQUIRE_INVITE_CODE=true/false`

## 4. Data Models

### 4.1 New Entity: InviteCode

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK, auto-generated | |
| code | String | unique, not null, max 50 | Stored uppercase |
| description | String | nullable, max 255 | Admin notes |
| max_uses | Integer | nullable | null = unlimited |
| use_count | int | not null, default 0 | |
| enabled | boolean | not null, default true | |
| expires_at | Instant | nullable | null = never |
| created_at | Instant | not null, auto-set | |

### 4.2 Updated Entity: User

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| invite_code_id | UUID | FK to invite_codes, nullable | null for users registered before feature or when closed beta disabled |

### 4.3 Database Migration

**File:** `V16__Add_invite_codes.sql`

```sql
-- Create invite_codes table
CREATE TABLE invite_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    max_uses INTEGER,
    use_count INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_invite_codes_code UNIQUE (code)
);

CREATE INDEX idx_invite_codes_code ON invite_codes(code);
CREATE INDEX idx_invite_codes_enabled ON invite_codes(enabled);

-- Add invite_code_id to users
ALTER TABLE users ADD COLUMN invite_code_id UUID REFERENCES invite_codes(id);
```

## 5. Error Handling

| Scenario | HTTP Status | Message |
|----------|-------------|---------|
| Code not found | 400 | "Invalid or expired invite code" |
| Code disabled | 400 | "Invalid or expired invite code" |
| Code expired | 400 | "Invalid or expired invite code" |
| Code max uses reached | 400 | "Invalid or expired invite code" |
| Missing code (when required) | 400 | "Invite code is required" |

**Rationale:** Single generic message prevents enumeration attacks (cannot determine if code exists vs. exhausted).

## 6. Testing Strategy

### 6.1 Unit Tests

**InviteCodeServiceTest:**
- `validateAndUse_validCode_incrementsCount()`
- `validateAndUse_invalidCode_throwsBadRequest()`
- `validateAndUse_disabledCode_throwsBadRequest()`
- `validateAndUse_expiredCode_throwsBadRequest()`
- `validateAndUse_maxUsesReached_throwsBadRequest()`
- `validateAndUse_unlimitedUses_succeeds()`
- `validateAndUse_caseInsensitive_succeeds()`

### 6.2 Integration Tests

**InviteCodeIT:**
- `register_withValidInviteCode_succeeds()`
- `register_withInvalidInviteCode_returns400()`
- `register_withExpiredInviteCode_returns400()`
- `register_codeUsageIncremented_afterRegistration()`
- `register_userLinkedToInviteCode_afterRegistration()`
- `register_closedBetaDisabled_noCodeRequired()`

### 6.3 Test Data

Test profile should include test invite codes:
```java
@BeforeEach
void setup() {
    inviteCodeRepository.save(InviteCode.builder()
        .code("TESTCODE")
        .enabled(true)
        .build());
}
```

## 7. Performance Considerations

### 7.1 Database
- Index on `code` column for O(1) lookups
- Index on `enabled` for admin queries
- Atomic increment via `UPDATE ... SET use_count = use_count + 1` prevents race conditions

### 7.2 Concurrency
- `incrementUseCount()` uses atomic SQL update
- No optimistic locking needed - increment is idempotent for tracking purposes
- Edge case: if two users register simultaneously with a code at max_uses-1, both may succeed (acceptable for simplicity)

### 7.3 Caching
- No caching needed - invite code validation is infrequent (only at registration)

## 8. Security Considerations

### 8.1 Code Format
- Codes stored uppercase, lookups normalized
- Recommend 8+ alphanumeric characters (e.g., `BETA2026A`)
- Admin should generate codes externally or via future admin API

### 8.2 Enumeration Prevention
- Single generic error message for all validation failures
- No timing differences between code-not-found and code-exhausted paths

### 8.3 Abuse Prevention
- Existing rate limiting on registration endpoint applies
- `max_uses` per code limits blast radius of leaked codes
- `expires_at` limits time window for code abuse
- `enabled` flag allows immediate revocation

## 9. Monitoring and Observability

### 9.1 Logging
- Log successful registrations with invite code (already exists)
- Log validation failures at WARN level (already exists via BadRequestException)

### 9.2 Metrics (Future)
- Count registrations per invite code
- Track code exhaustion events

### 9.3 Admin Visibility (Future)
- Admin API to list codes with usage stats
- Dashboard showing registration funnel by invite code

## 10. File Changes Summary

### New Files
| File | Purpose |
|------|---------|
| `InviteCode.java` | Entity |
| `InviteCodeRepository.java` | Repository |
| `InviteCodeService.java` | Service |
| `V16__Add_invite_codes.sql` | Migration |
| `InviteCodeServiceTest.java` | Unit tests |
| `InviteCodeIT.java` | Integration tests |

### Modified Files
| File | Changes |
|------|---------|
| `User.java` | Add `inviteCode` field |
| `UserService.java` | Accept `InviteCode` in `createUser()` |
| `AuthenticationService.java` | Use `InviteCodeService` for validation |
| `AppConfig.java` | Remove `inviteCodes` list (after migration) |
| `Register.tsx` | Read `?code=` URL param |
| `application.properties` | Remove `application.invite-codes` |
| `application-local.properties` | Remove `application.invite-codes` |
