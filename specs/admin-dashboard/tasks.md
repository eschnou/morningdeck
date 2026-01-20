# Admin Feature - Implementation Task

## Task Overview

Implement role-based admin functionality for user management. Adds ADMIN role, user enable/disable, and admin API endpoints for managing users, credentials, subscriptions, and credits.

Reference: `specs/requirements/admin.md`

## Application Context

### Current Authentication Flow
- `JwtAuthenticationFilter` extracts JWT, calls `UserDetailsService.loadUserByUsername()`
- `UserService.loadUserByUsername()` returns Spring Security `User` with **empty authorities list**
- No role system exists - all authenticated users have identical permissions
- `SecurityConfig` uses `anyRequest().authenticated()` - no role-based restrictions

### User Entity
- Location: `core/model/User.java`
- Fields: id, username, name, email, password, avatarUrl, language, createdAt, subscription
- Missing: `role`, `enabled` fields

### Existing Patterns
- **Controllers**: `@RestController`, `@RequiredArgsConstructor`, return `ResponseEntity<DTO>`
- **Services**: `@Service`, `@Transactional`, inject repositories via constructor
- **DTOs**: Lombok `@Data`, `@Builder`, separate request/response DTOs
- **Tests**: `@SpringBootTest`, `@Transactional`, `@Import(TestConfig.class)`, use `mockMvc`
- **Exceptions**: Custom exceptions handled by `GlobalExceptionHandler`
- **Analytics**: `AnalyticsService.logEvent(eventKey, userId, properties)`
- **Email**: `EmailService` with template-based emails

### Database
- Flyway migrations in `src/main/resources/db/migrations/`
- Current: `V1__Database_init.sql`
- Enums stored as STRING (project convention)

## Architecture Design Guidelines

### Security
1. Use `@EnableMethodSecurity` in SecurityConfig
2. Add `@PreAuthorize("hasRole('ADMIN')")` on AdminController class level
3. Return authorities from `loadUserByUsername()` based on user role
4. Check `enabled` flag in authentication flow - reject disabled users

### Role Enum
```java
public enum Role {
    USER,
    ADMIN
}
```

### DTOs for Admin Operations
- `AdminUserListDTO` - paginated list response
- `AdminUserDetailDTO` - full user details with subscription
- `AdminUpdateEnabledDTO` - { enabled: boolean }
- `AdminResetPasswordDTO` - { newPassword: string }
- `AdminUpdateEmailDTO` - { email: string }
- `AdminUpdateSubscriptionDTO` - { plan: SubscriptionPlan }
- `AdminAdjustCreditsDTO` - { amount: int, mode: "SET" | "ADD" }

### Audit Trail
Log all admin actions with:
- Event name (e.g., `ADMIN_RESET_PASSWORD`)
- Admin user ID (actor)
- Target user ID
- Relevant details (e.g., new plan name, credits adjustment)

## Implementation Plan

### Phase 1: Database Schema

**Files to create:**
- `src/main/resources/db/migrations/V2__Add_user_role_and_enabled.sql`

**Migration content:**
```sql
ALTER TABLE users ADD COLUMN role VARCHAR(255) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
```

---

### Phase 2: Domain Model Updates

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/model/User.java`

**Changes:**
1. Create `Role` enum in `core/model/Role.java`:
   ```java
   public enum Role {
       USER,
       ADMIN
   }
   ```

2. Add fields to User entity:
   ```java
   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   @Builder.Default
   private Role role = Role.USER;

   @Column(nullable = false)
   @Builder.Default
   private boolean enabled = true;
   ```

---

### Phase 3: Security Configuration

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/config/SecurityConfig.java`
- `src/main/java/be/transcode/barebone/server/core/service/UserService.java`
- `src/main/java/be/transcode/barebone/server/filter/JwtAuthenticationFilter.java`

**SecurityConfig changes:**
1. Add `@EnableMethodSecurity` annotation
2. Admin paths covered by `anyRequest().authenticated()`

**UserService.loadUserByUsername() changes:**
```java
@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByUsername(normalizeUsername(username))
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    if (!user.isEnabled()) {
        throw new DisabledException("User account is disabled");
    }

    return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
    );
}
```

**JwtAuthenticationFilter changes:**
- Add handling for `DisabledException` to return appropriate error response

---

### Phase 4: Admin DTOs

**Files to create in `src/main/java/be/transcode/barebone/server/core/dto/`:**

1. `AdminUserListItemDTO.java`:
   ```java
   @Data @Builder
   public class AdminUserListItemDTO {
       private String id;
       private String username;
       private String email;
       private String name;
       private Role role;
       private boolean enabled;
       private LocalDateTime createdAt;
       private String subscriptionPlan;
       private Integer creditsBalance;
   }
   ```

2. `AdminUserDetailDTO.java`:
   ```java
   @Data @Builder
   public class AdminUserDetailDTO {
       private String id;
       private String username;
       private String email;
       private String name;
       private String avatarUrl;
       private Language language;
       private Role role;
       private boolean enabled;
       private LocalDateTime createdAt;
       private SubscriptionStatusDTO subscription;
   }
   ```

3. `AdminUpdateEnabledDTO.java`:
   ```java
   @Data
   public class AdminUpdateEnabledDTO {
       @NotNull
       private Boolean enabled;
   }
   ```

4. `AdminResetPasswordDTO.java`:
   ```java
   @Data
   public class AdminResetPasswordDTO {
       @NotBlank
       @Size(min = 8)
       private String newPassword;
   }
   ```

5. `AdminUpdateEmailDTO.java`:
   ```java
   @Data
   public class AdminUpdateEmailDTO {
       @NotBlank
       @Email
       private String email;
   }
   ```

6. `AdminUpdateSubscriptionDTO.java`:
   ```java
   @Data
   public class AdminUpdateSubscriptionDTO {
       @NotNull
       private Subscription.SubscriptionPlan plan;
   }
   ```

7. `AdminAdjustCreditsDTO.java`:
   ```java
   @Data
   public class AdminAdjustCreditsDTO {
       @NotNull
       private Integer amount;

       @NotNull
       private CreditAdjustmentMode mode;

       public enum CreditAdjustmentMode {
           SET,  // Set absolute value
           ADD   // Add/subtract delta
       }
   }
   ```

---

### Phase 5: Repository Updates

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/repository/UserRepository.java`

**Add methods:**
```java
Page<User> findAll(Pageable pageable);

Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
    String username, String email, Pageable pageable);

Page<User> findByEnabled(boolean enabled, Pageable pageable);

Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndEnabled(
    String username, String email, boolean enabled, Pageable pageable);
```

---

### Phase 6: Admin Service

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/service/AdminService.java`

**Methods:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsService analyticsService;
    private final EmailService emailService;
    private final AppConfig appConfig;

    // List users with pagination, search, filter
    public Page<AdminUserListItemDTO> listUsers(
        String search, Boolean enabled, Pageable pageable);

    // Get user details
    public AdminUserDetailDTO getUserDetail(UUID userId);

    // Update enabled status
    @Transactional
    public void updateUserEnabled(UUID adminId, UUID userId, boolean enabled);

    // Reset password
    @Transactional
    public void resetPassword(UUID adminId, UUID userId, String newPassword);

    // Update email
    @Transactional
    public void updateEmail(UUID adminId, UUID userId, String newEmail);

    // Update subscription plan
    @Transactional
    public void updateSubscription(UUID adminId, UUID userId, SubscriptionPlan plan);

    // Adjust credits
    @Transactional
    public void adjustCredits(UUID adminId, UUID userId, int amount, CreditAdjustmentMode mode);
}
```

**Implementation notes:**
- Each method logs analytics event with admin ID and target user ID
- `updateUserEnabled` must check admin != target user
- `resetPassword` sends notification email to user
- `updateEmail` sends notification to both old and new addresses
- `adjustCredits` validates non-negative result

---

### Phase 7: Admin Controller

**Files to create:**
- `src/main/java/be/transcode/barebone/server/core/controller/AdminController.java`

```java
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserListItemDTO>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        // Build Pageable and call service
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailDTO> getUserDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Void> updateEnabled(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateEnabledDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.updateUserEnabled(adminId, id, dto.getEnabled());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.resetPassword(adminId, id, dto.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/email")
    public ResponseEntity<Void> updateEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateEmailDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.updateEmail(adminId, id, dto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/subscription")
    public ResponseEntity<Void> updateSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateSubscriptionDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.updateSubscription(adminId, id, dto.getPlan());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/credits")
    public ResponseEntity<Void> adjustCredits(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminAdjustCreditsDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.adjustCredits(adminId, id, dto.getAmount(), dto.getMode());
        return ResponseEntity.ok().build();
    }

    private UUID getAdminId(UserDetails userDetails) {
        return userService.getInternalUserByUsername(userDetails.getUsername()).getId();
    }
}
```

---

### Phase 8: Email Templates

**Files to create in `src/main/resources/templates/email/`:**
- `password_reset_by_admin.ftl`
- `email_changed_by_admin.ftl`

**EmailService additions:**
```java
public void sendPasswordResetByAdminEmail(String to, String fullName, String domain);
public void sendEmailChangedByAdminEmail(String to, String fullName, String newEmail, String domain);
```

---

### Phase 9: Exception Handling

**Files to modify:**
- `src/main/java/be/transcode/barebone/server/core/exception/GlobalExceptionHandler.java`

**Add handler for Spring Security's AccessDeniedException:**
```java
@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
public ResponseEntity<ApiError> handleSpringAccessDenied(
        org.springframework.security.access.AccessDeniedException ex,
        HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied", request);
}

@ExceptionHandler(DisabledException.class)
public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.FORBIDDEN, "Account is disabled", request);
}
```

---

### Phase 10: Integration Tests

**Files to create:**
- `src/test/java/be/transcode/barebone/server/core/controller/AdminIT.java`

**Test scenarios:**
1. Admin can list users with pagination
2. Admin can filter users by search term
3. Admin can filter users by enabled status
4. Admin can view user details
5. Admin can disable/enable user
6. Admin cannot disable themselves
7. Admin can reset user password
8. Admin can change user email
9. Admin can update user subscription
10. Admin can adjust user credits (SET mode)
11. Admin can adjust user credits (ADD mode)
12. Credits cannot go negative
13. Non-admin user gets 403 on admin endpoints
14. Disabled user cannot authenticate

**Test setup:**
- Create admin user by setting role directly in repository
- Create regular test users for operations

---

### Phase 11: Update Existing Tests

**Files to modify:**
- Existing tests may need updates to account for new `role` and `enabled` fields
- Ensure test users have `enabled = true` by default (should be automatic via entity default)

---

## Implementation Order

Execute phases sequentially:
1. Phase 1 (Database) - Required for all subsequent phases
2. Phase 2 (Domain Model) - Required for security and service
3. Phase 3 (Security) - Required for controller authorization
4. Phase 4 (DTOs) - Required for service and controller
5. Phase 5 (Repository) - Required for service
6. Phase 6 (Service) - Required for controller
7. Phase 7 (Controller) - Main API implementation
8. Phase 8 (Email) - Optional but recommended for user notification
9. Phase 9 (Exceptions) - Polish error handling
10. Phase 10 (Tests) - Verify all functionality
11. Phase 11 (Update tests) - Ensure existing tests pass
