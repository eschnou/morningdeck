# Email Verification - Implementation Task

## Task Overview

Implement email verification for new user registrations. Users receive a verification link after registration and must click it before they can login. Includes token management, verification endpoints, and admin bypass functionality.

Reference: `specs/requirements/email-verification.md`

## Application Context

### Current Registration Flow
- `AuthController.register()` → `AuthenticationService.register()`
- Creates user with `enabled = true`
- Sends welcome email immediately
- Returns JWT token immediately (user can login right away)

### User Entity
- Location: `core/model/User.java`
- Has `enabled` field (for admin disable)
- Missing: `emailVerified` field

### Authentication Flow
- `UserService.loadUserByUsername()` checks `enabled` field
- Throws `DisabledException` if disabled
- Returns authorities based on role

### Email Infrastructure
- `EmailService` with Freemarker templates
- `LogsEmailSender` for local development
- Templates in `src/main/resources/templates/email/`

### Existing Patterns
- Secure random: Use `java.security.SecureRandom`
- Hashing: Use existing `PasswordEncoder` for token hashing
- Config: Use `@Value` or `@ConfigurationProperties`

## Architecture Design Guidelines

### Token Security
1. Generate 32 bytes using `SecureRandom`
2. Encode as URL-safe Base64 (44 characters)
3. Hash token before storing (use SHA-256, not bcrypt - need exact match)
4. Store hash in database, send raw token in email

### Token Hashing
```java
// Use SHA-256 for token hashing (need exact match, not bcrypt)
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
String tokenHash = Base64.getEncoder().encodeToString(hash);
```

### Registration Response DTO
```java
@Data @Builder
public class RegisterResponse {
    private String message;
    private String email; // masked: j***@example.com
}
```

### Configuration Properties
```java
@ConfigurationProperties(prefix = "application.email.verification")
public class EmailVerificationProperties {
    private boolean enabled = true;
    private int expirationHours = 24;
}
```

## Implementation Plan

### Phase 1: Database Schema

**Files to create:**
- `src/main/resources/db/migrations/V3__Add_email_verification.sql`

**Migration content:**
```sql
-- Add emailVerified to users
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Update existing users to verified (backward compatibility)
UPDATE users SET email_verified = TRUE;

-- Create verification tokens table
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_email_verification_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens(expires_at);
```

---

### Phase 2: Domain Model Updates

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/model/EmailVerificationToken.java`

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/model/User.java`

**User entity changes:**
```java
@Builder.Default
@Column(name = "email_verified", nullable = false)
private boolean emailVerified = false;
```

**EmailVerificationToken entity:**
```java
@Entity
@Table(name = "email_verification_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
```

---

### Phase 3: Configuration

**Files to create:**
- `src/main/java/be/transcode/barebone/server/config/EmailVerificationProperties.java`

**Files to modify:**
- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`

**Properties class:**
```java
@Configuration
@ConfigurationProperties(prefix = "application.email.verification")
@Data
public class EmailVerificationProperties {
    private boolean enabled = true;
    private int expirationHours = 24;
}
```

**application.properties additions:**
```properties
# Email verification
application.email.verification.enabled=true
application.email.verification.expiration-hours=24
```

**application-local.properties:**
```properties
# Disable email verification for local development
application.email.verification.enabled=false
```

---

### Phase 4: Repository

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/repository/EmailVerificationTokenRepository.java`

```java
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    int countByUserAndCreatedAtAfter(User user, LocalDateTime dateTime);
}
```

---

### Phase 5: DTOs

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/dto/RegisterResponse.java`
- `src/main/java/be/transcode/barebone/server/core/dto/ResendVerificationRequest.java`
- `src/main/java/be/transcode/barebone/server/core/dto/VerificationResponse.java`

**RegisterResponse:**
```java
@Data @Builder
public class RegisterResponse {
    private String message;
    private String email;
}
```

**ResendVerificationRequest:**
```java
@Data
public class ResendVerificationRequest {
    @NotBlank
    @Email
    private String email;
}
```

**VerificationResponse:**
```java
@Data @Builder
public class VerificationResponse {
    private boolean success;
    private String message;
}
```

---

### Phase 6: Email Verification Service

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/service/EmailVerificationService.java`

**Methods:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailVerificationProperties properties;
    private final AppConfig appConfig;

    // Generate token and send verification email
    @Transactional
    public void createAndSendVerification(User user);

    // Verify token and activate user
    @Transactional
    public VerificationResponse verifyEmail(String token);

    // Resend verification email (with rate limiting)
    @Transactional
    public void resendVerification(String email);

    // Admin manual verification
    @Transactional
    public void adminVerifyEmail(UUID adminId, UUID userId);

    // Check if verification is enabled
    public boolean isVerificationEnabled();

    // Generate secure random token
    private String generateToken();

    // Hash token for storage
    private String hashToken(String token);

    // Mask email for display (j***@example.com)
    private String maskEmail(String email);

    // Cleanup expired tokens (scheduled)
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredTokens();
}
```

**Token generation:**
```java
private String generateToken() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}

private String hashToken(String token) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
```

**Rate limiting for resend:**
```java
// Check rate limit: max 3 per hour
int recentCount = tokenRepository.countByUserAndCreatedAtAfter(
    user, LocalDateTime.now().minusHours(1));
if (recentCount >= 3) {
    throw new BadRequestException("Too many verification requests. Try again later.");
}
```

---

### Phase 7: Email Template

**Files to create:**
- `src/main/resources/templates/email/email_verification.ftl`

**Template variables:**
- `fullName`
- `verificationLink`
- `expirationHours`
- `domain`

**EmailService additions:**
```java
public void sendVerificationEmail(String to, String fullName, String verificationLink,
                                   int expirationHours, String domain);
```

---

### Phase 8: Authentication Service Updates

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/service/AuthenticationService.java`
- `src/main/java/be/transcode/barebone/server/core/service/UserService.java`

**AuthenticationService.register() changes:**
- If verification enabled:
  - Create user with `emailVerified = false`
  - Don't send welcome email yet
  - Create verification token and send verification email
  - Return `RegisterResponse` with message and masked email
- If verification disabled:
  - Keep current behavior (return JWT)

**UserService.loadUserByUsername() changes:**
```java
if (!user.isEnabled()) {
    throw new DisabledException("User account is disabled");
}
if (!user.isEmailVerified()) {
    throw new DisabledException("Email not verified");
}
```

---

### Phase 9: Auth Controller Updates

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/controller/AuthController.java`

**New/modified endpoints:**
```java
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
    // Returns AuthResponse or RegisterResponse based on verification config
}

@GetMapping("/verify-email")
public ResponseEntity<VerificationResponse> verifyEmail(@RequestParam String token) {
    return ResponseEntity.ok(emailVerificationService.verifyEmail(token));
}

@PostMapping("/resend-verification")
public ResponseEntity<Void> resendVerification(
        @RequestBody @Valid ResendVerificationRequest request) {
    emailVerificationService.resendVerification(request.getEmail());
    return ResponseEntity.ok().build();
}
```

---

### Phase 10: Admin Endpoint

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/controller/AdminController.java`
- `src/main/java/be/transcode/barebone/server/core/service/AdminService.java`

**New endpoint:**
```java
@PutMapping("/users/{id}/verify-email")
public ResponseEntity<Void> verifyUserEmail(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID id) {
    UUID adminId = getAdminId(userDetails);
    emailVerificationService.adminVerifyEmail(adminId, id);
    return ResponseEntity.ok().build();
}
```

---

### Phase 11: Update Admin User Detail DTO

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/dto/AdminUserDetailDTO.java`
- `src/main/java/be/transcode/barebone/server/core/dto/AdminUserListItemDTO.java`
- `src/main/java/be/transcode/barebone/server/core/service/AdminService.java`

Add `emailVerified` field to admin DTOs and mappings.

---

### Phase 12: Exception Handling

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/exception/GlobalExceptionHandler.java`

Consider adding specific exception for unverified email if needed for distinct error messages.

---

### Phase 13: Integration Tests

**Files to create:**
- `src/test/java/be/transcode/barebone/server/core/controller/EmailVerificationIT.java`

**Test scenarios:**
1. Registration sends verification email (when enabled)
2. Registration returns JWT (when disabled)
3. Verify email with valid token
4. Verify email with expired token
5. Verify email with invalid token
6. Resend verification email
7. Rate limiting on resend
8. Unverified user cannot login
9. Verified user can login
10. Admin can manually verify user
11. Welcome email sent after verification

---

### Phase 14: Update Existing Tests

**Files to modify:**
- Existing auth tests need to handle verification flow
- Either disable verification in test profile or verify users in setup

**Test configuration:**
```properties
# application-test.properties
application.email.verification.enabled=false
```

---

## Implementation Order

Execute phases sequentially:
1. Phase 1 (Database) - Required for all subsequent phases
2. Phase 2 (Domain Model) - Required for repository and service
3. Phase 3 (Configuration) - Required for conditional logic
4. Phase 4 (Repository) - Required for service
5. Phase 5 (DTOs) - Required for controller and service
6. Phase 6 (Service) - Core verification logic
7. Phase 7 (Email Template) - Required for sending emails
8. Phase 8 (Auth Service) - Update registration flow
9. Phase 9 (Auth Controller) - New endpoints
10. Phase 10 (Admin) - Admin bypass functionality
11. Phase 11 (Admin DTOs) - Add emailVerified to admin views
12. Phase 12 (Exceptions) - Polish error handling
13. Phase 13 (Tests) - New integration tests
14. Phase 14 (Update tests) - Ensure existing tests pass
