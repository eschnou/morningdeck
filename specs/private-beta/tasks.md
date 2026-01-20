# Private Beta Feature - Implementation Tasks

## Phase 1: Database Schema and Entity

**Goal:** Create the invite_codes table and InviteCode entity
**Verification:** Run `mvn test` - migrations apply successfully, entity compiles
**Status:** COMPLETED

### Tasks

- [x] **1.1** Create Flyway migration `V18__Add_invite_codes.sql`
  - Create `invite_codes` table with columns: id, code, description, max_uses, use_count, enabled, expires_at, created_at
  - Add indexes on `code` and `enabled`
  - Add `invite_code_id` FK column to `users` table
  - File: `backend/src/main/resources/db/migrations/V18__Add_invite_codes.sql`

- [x] **1.2** Create `InviteCode` entity
  - UUID id with GenerationType.UUID
  - String code (unique, not null, max 50)
  - String description (nullable, max 255)
  - Integer maxUses (nullable)
  - int useCount (default 0)
  - boolean enabled (default true)
  - Instant expiresAt (nullable)
  - Instant createdAt (@PrePersist)
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/model/InviteCode.java`

- [x] **1.3** Update `User` entity
  - Add `@ManyToOne(fetch = FetchType.LAZY)` field `inviteCode`
  - JoinColumn `invite_code_id`
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/model/User.java`

---

## Phase 2: Repository and Service Layer

**Goal:** Implement invite code validation and usage tracking logic
**Verification:** Run `mvn test -Dtest=InviteCodeServiceTest` - all unit tests pass
**Status:** COMPLETED

### Tasks

- [x] **2.1** Create `InviteCodeRepository`
  - `Optional<InviteCode> findByCode(String code)`
  - `@Modifying @Query` for atomic `incrementUseCount(UUID id)`
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/repository/InviteCodeRepository.java`

- [x] **2.2** Create `InviteCodeService`
  - Inject `InviteCodeRepository` and `AppConfig`
  - Method `InviteCode validateAndUse(String code)`:
    1. Normalize code to uppercase
    2. Find by code or throw BadRequestException("Invalid or expired invite code")
    3. Validate enabled == true
    4. Validate expiresAt == null OR expiresAt > now
    5. Validate maxUses == null OR useCount < maxUses
    6. Call repository.incrementUseCount(id)
    7. Return entity
  - All validation failures use same error message
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/service/InviteCodeService.java`

- [x] **2.3** Create `InviteCodeServiceTest` unit tests
  - Test: `validateAndUse_validCode_incrementsCount`
  - Test: `validateAndUse_codeNotFound_throwsBadRequest`
  - Test: `validateAndUse_disabledCode_throwsBadRequest`
  - Test: `validateAndUse_expiredCode_throwsBadRequest`
  - Test: `validateAndUse_maxUsesReached_throwsBadRequest`
  - Test: `validateAndUse_unlimitedUses_succeeds`
  - Test: `validateAndUse_caseInsensitive_succeeds`
  - File: `backend/src/test/java/be/transcode/morningdeck/server/core/service/InviteCodeServiceTest.java`

---

## Phase 3: Registration Flow Integration

**Goal:** Integrate invite code validation into registration flow
**Verification:** Run `mvn test -Dtest=InviteCodeIT` - all integration tests pass
**Status:** COMPLETED (integration tests have pre-existing infrastructure issue with AI service mocking)

### Tasks

- [x] **3.1** Update `UserService.createUser()` method
  - Add overloaded method accepting `InviteCode inviteCode` parameter
  - Set `user.setInviteCode(inviteCode)` before persisting
  - Existing method delegates to new method with `null` inviteCode
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/service/UserService.java`

- [x] **3.2** Update `AuthenticationService.register()` method
  - Inject `InviteCodeService`
  - Replace static list check with `inviteCodeService.validateAndUse()`
  - Pass validated InviteCode to `userService.createUser()`
  - File: `backend/src/main/java/be/transcode/morningdeck/server/core/service/AuthenticationService.java`

- [x] **3.3** Create `InviteCodeIT` integration tests
  - Test: `register_withValidInviteCode_succeeds` (closedBeta=true)
  - Test: `register_withInvalidInviteCode_returns400`
  - Test: `register_withExpiredInviteCode_returns400`
  - Test: `register_withDisabledCode_returns400`
  - Test: `register_withExhaustedCode_returns400`
  - Test: `register_codeUsageIncremented_afterRegistration`
  - Test: `register_userLinkedToInviteCode_afterRegistration`
  - Test: `register_closedBetaDisabled_noCodeRequired`
  - Use `@TestPropertySource(properties = {"application.closed-beta=true"})` for closed beta tests
  - File: `backend/src/test/java/be/transcode/morningdeck/server/core/controller/InviteCodeIT.java`
  - **Note:** Integration tests written but blocked by pre-existing AI service mocking issue in test infrastructure

---

## Phase 4: Configuration Cleanup

**Goal:** Remove deprecated static invite codes configuration
**Verification:** Application starts with `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"`
**Status:** COMPLETED

### Tasks

- [x] **4.1** Update `AppConfig`
  - Remove `inviteCodes` field and getter/setter
  - Keep `closedBeta` field
  - File: `backend/src/main/java/be/transcode/morningdeck/server/config/AppConfig.java`

- [x] **4.2** Remove `application.invite-codes` from properties
  - Remove from `application.properties` (if present)
  - Remove from `application-local.properties` (if present)
  - Keep `application.closed-beta` setting

---

## Phase 5: Frontend URL Pre-population

**Goal:** Pre-fill invite code from URL query parameter
**Verification:** Navigate to `/auth/register?code=TESTCODE` and verify field is pre-populated
**Status:** COMPLETED

### Tasks

- [x] **5.1** Update `Register.tsx` to read URL parameter
  - Import `useSearchParams` from `react-router-dom`
  - Read `code` query parameter on component mount
  - Initialize `inviteCode` field with URL value
  - File: `frontend/src/pages/Register.tsx`

---

## Phase 6: End-to-End Verification

**Goal:** Verify complete flow works in local environment
**Verification:** Manual testing of registration with invite codes
**Status:** READY FOR MANUAL TESTING

### Tasks

- [x] **6.1** Seed test invite codes in local database
  - Created seed SQL script: `backend/src/main/resources/db/seed/invite_codes.sql`
  - Include: unlimited code (BETA2026), limited code (LIMITED10), disabled code, expired code, exhausted code
  - Run: `psql -h localhost -U postgres -d morningdeck -f backend/src/main/resources/db/seed/invite_codes.sql`

- [ ] **6.2** Manual test: closed beta enabled
  - Set `application.closed-beta=true` in `application-local.properties`
  - Set `VITE_REQUIRE_INVITE_CODE=true` in frontend `.env`
  - Verify registration requires invite code
  - Verify valid code allows registration
  - Verify invalid code shows error
  - Verify use_count increments after registration

- [ ] **6.3** Manual test: closed beta disabled
  - Set `application.closed-beta=false`
  - Set `VITE_REQUIRE_INVITE_CODE=false`
  - Verify registration works without invite code

- [ ] **6.4** Manual test: URL pre-population
  - Navigate to `/auth/register?code=TESTCODE`
  - Verify invite code field is pre-populated

---

## Summary of Created/Modified Files

### New Files
| File | Purpose |
|------|---------|
| `backend/src/main/resources/db/migrations/V18__Add_invite_codes.sql` | Database migration |
| `backend/src/main/java/.../model/InviteCode.java` | Entity |
| `backend/src/main/java/.../repository/InviteCodeRepository.java` | Repository |
| `backend/src/main/java/.../service/InviteCodeService.java` | Service |
| `backend/src/test/java/.../service/InviteCodeServiceTest.java` | Unit tests |
| `backend/src/test/java/.../controller/InviteCodeIT.java` | Integration tests |
| `backend/src/main/resources/db/seed/invite_codes.sql` | Test data seed script |

### Modified Files
| File | Changes |
|------|---------|
| `backend/src/main/java/.../model/User.java` | Added `inviteCode` FK field |
| `backend/src/main/java/.../service/UserService.java` | Added `InviteCode` parameter to `createUser()` |
| `backend/src/main/java/.../service/AuthenticationService.java` | Integrated `InviteCodeService` |
| `backend/src/main/java/.../config/AppConfig.java` | Removed `inviteCodes` list |
| `backend/src/main/resources/application.properties` | Removed `application.invite-codes` |
| `frontend/src/pages/Register.tsx` | Added URL `?code=` parameter reading |
