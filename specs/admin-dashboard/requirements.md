# Admin Feature Requirements

## Introduction

Admin functionality to manage users through a privileged API. Enables administrators to list users, control account status, modify credentials, and manage subscriptions/credits.

## Requirements

### REQ-1: Role-based Access Control

**User Story**: As a system administrator, I want role-based access control, so that admin endpoints are protected from regular users.

**Acceptance Criteria**:
- User entity has a `role` field with enum values: `USER`, `ADMIN`
- Default role for new users is `USER`
- Spring Security grants authorities based on user role
- Admin endpoints require `ROLE_ADMIN` authority
- Existing endpoints remain accessible to all authenticated users

### REQ-2: User Account Status

**User Story**: As an admin, I want to enable/disable user accounts, so that I can control access to the system.

**Acceptance Criteria**:
- User entity has an `enabled` boolean field (default: `true`)
- Disabled users cannot authenticate (login fails with appropriate error)
- Disabled users' existing JWT tokens are rejected
- Admin can toggle enabled status via API
- Admin cannot disable their own account

### REQ-3: List Users

**User Story**: As an admin, I want to list all users with pagination, so that I can browse and search the user base.

**Acceptance Criteria**:
- GET endpoint returns paginated list of users
- Response includes: id, username, email, name, role, enabled, createdAt, subscription plan, credits balance
- Supports pagination parameters (page, size)
- Supports sorting by: username, email, createdAt
- Supports search/filter by: username, email, enabled status

### REQ-4: Admin Password Reset

**User Story**: As an admin, I want to reset a user's password, so that I can help users who are locked out.

**Acceptance Criteria**:
- PUT endpoint accepts userId and new password
- Password is encoded using existing PasswordEncoder
- No verification of current password required (admin privilege)
- User receives email notification of password change
- Analytics event logged for audit trail

### REQ-5: Admin Email Change

**User Story**: As an admin, I want to change a user's email address, so that I can correct errors or update records.

**Acceptance Criteria**:
- PUT endpoint accepts userId and new email
- Email uniqueness validated before update
- User receives notification at both old and new email addresses
- Analytics event logged for audit trail

### REQ-6: Subscription Management

**User Story**: As an admin, I want to change a user's subscription plan, so that I can manually upgrade/downgrade accounts.

**Acceptance Criteria**:
- PUT endpoint accepts userId and new subscription plan
- Valid plans: FREE, PRO, BUSINESS
- Credits balance updated to new plan's monthly allocation
- Monthly credits updated to new plan value
- Analytics event logged for audit trail

### REQ-7: Credits Management

**User Story**: As an admin, I want to adjust a user's credits balance, so that I can grant bonus credits or correct errors.

**Acceptance Criteria**:
- PUT endpoint accepts userId and credits adjustment (add/set)
- Supports both setting absolute value and adding/subtracting delta
- Credits balance cannot go negative
- Analytics event logged with adjustment details

### REQ-8: View User Details

**User Story**: As an admin, I want to view complete user details, so that I can investigate issues.

**Acceptance Criteria**:
- GET endpoint returns full user profile including:
  - Basic info: id, username, email, name, avatarUrl, language, createdAt
  - Account status: role, enabled
  - Subscription: plan, creditsBalance, monthlyCredits, autoRenew, nextRenewalDate

## Non-Functional Requirements

### Architecture

- New `AdminController` under `/admin/**`
- New `AdminService` for admin-specific operations
- Reuse existing `UserRepository` with additional query methods
- Role enum stored as STRING in database (per project convention)

### Security

- All admin endpoints require `ROLE_ADMIN` authority
- Use `@PreAuthorize("hasRole('ADMIN')")` on controller or method level
- Admin actions logged with actor ID for audit trail
- Sensitive operations (password reset, email change) logged at WARN level

### Database Changes

- Add `role` column to users table (VARCHAR, NOT NULL, DEFAULT 'USER')
- Add `enabled` column to users table (BOOLEAN, NOT NULL, DEFAULT TRUE)
- Create Flyway migration `V2__Add_user_role_and_enabled.sql`

### API Design

- Base path: `/admin` (reverse proxy adds `/api/v1` prefix in production)
- Endpoints:
  - `GET /users` - List users (paginated)
  - `GET /users/{id}` - Get user details
  - `PUT /users/{id}/enabled` - Toggle enabled status
  - `PUT /users/{id}/password` - Reset password
  - `PUT /users/{id}/email` - Change email
  - `PUT /users/{id}/subscription` - Update subscription plan
  - `PUT /users/{id}/credits` - Adjust credits balance

### Audit & Analytics

- All admin operations tracked via AnalyticsService
- Event names: `ADMIN_DISABLE_USER`, `ADMIN_ENABLE_USER`, `ADMIN_RESET_PASSWORD`, `ADMIN_CHANGE_EMAIL`, `ADMIN_CHANGE_SUBSCRIPTION`, `ADMIN_ADJUST_CREDITS`
- Events include: adminUserId, targetUserId, action details
