# Security

Morning Deck uses Spring Security with JWT-based stateless authentication. This document covers authentication, authorization, and security configuration.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           HTTP Request                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         AwsXRayFilter                                    │
│                    (Request tracing - optional)                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           MdcFilter                                      │
│                    (Request ID for logging)                              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    JwtAuthenticationFilter                               │
│            Extract Bearer token → Validate → Set auth context            │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Spring Security Chain                               │
│                    (CORS, CSRF, Authorization)                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Controller                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

## JWT Authentication

### Token Structure

JWT tokens contain:
- **Subject:** Username
- **Issued At:** Token creation time
- **Expiration:** Token expiry time
- **Signature:** HS256 with secret key

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/JwtService.java`

### Token Generation

```java
// JwtService.java:38-46
public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(userDetails.getUsername())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}
```

### Token Validation

1. Extract username from token
2. Load user from database
3. Check token signature
4. Check token not expired
5. Check username matches

### Configuration

```properties
# JWT secret (base64-encoded)
spring.security.jwt.secret=${JWT_SECRET}

# Token expiration (milliseconds)
spring.security.jwt.expiration=86400000  # 24 hours
```

## Authentication Filter

**File:** `backend/src/main/java/be/transcode/morningdeck/server/filter/JwtAuthenticationFilter.java`

### Request Flow

1. Check if path is excluded (login/register)
2. Extract `Authorization: Bearer <token>` header
3. Validate token:
   - `SignatureException` → 401 Invalid signature
   - `MalformedJwtException` → 401 Malformed token
   - `ExpiredJwtException` → 401 Expired token
4. Load user from database
   - `DisabledException` → 403 Account disabled
5. Set Spring Security authentication context
6. Add username to MDC for logging

### Error Responses

| Error | Status | Message |
|-------|--------|---------|
| Invalid signature | 401 | Invalid JWT signature |
| Malformed token | 401 | Malformed JWT token |
| Expired token | 401 | Expired JWT token |
| Account disabled | 403 | Account is disabled |

## Security Configuration

**File:** `backend/src/main/java/be/transcode/morningdeck/server/config/SecurityConfig.java`

### Public Endpoints

```java
// SecurityConfig.java:61-71
.requestMatchers(
    "/auth/**",           // Authentication endpoints
    "/waitlist/**",       // Waitlist signup
    "/error",             // Error page
    "/swagger-ui/**",     // API docs
    "/swagger-ui.html",
    "/api-docs/**",
    "/api-docs.yaml",
    "/",                  // Root
    "/public/**"          // Public endpoints
).permitAll()
```

### Session Management

```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

Sessions are **stateless** - no server-side session storage. Authentication state is carried entirely in the JWT.

## Authorization

### Role-Based Access

Two roles are defined:
- `USER` - Standard user (default)
- `ADMIN` - Admin access

Admin-only endpoints use `@PreAuthorize`:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public ResponseEntity<Page<AdminUserListItem>> getUsers(...) { }
```

### Method Security

Enabled via `@EnableMethodSecurity`:

```java
// Require authenticated user
@PreAuthorize("isAuthenticated()")

// Require ADMIN role
@PreAuthorize("hasRole('ADMIN')")

// Require specific permission
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
```

## CORS Configuration

**File:** `backend/src/main/java/be/transcode/morningdeck/server/config/SecurityConfig.java:84-110`

### Development Mode

When `application.cors.allowed-origins` is empty or `*`:
- All origins allowed via patterns
- Supports local development

### Production Mode

When `application.cors.allowed-origins` is set:
- Only listed origins allowed
- Comma-separated list

```properties
application.cors.allowed-origins=https://morningdeck.com,https://www.morningdeck.com
```

### Allowed Methods

```java
configuration.setAllowedMethods(Arrays.asList(
    "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
));
```

## Password Security

### Encoding

Passwords are encoded with BCrypt:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### Validation

Password validation happens in `AuthenticationService`:
- Spring Security's `DaoAuthenticationProvider` handles credential comparison
- Failed login attempts return 401 Unauthorized

## Frontend Integration

### Token Storage

The frontend stores tokens in `localStorage`:

```typescript
// AuthContext.tsx
localStorage.setItem('auth_token', response.token);
```

### Token Attachment

The API client attaches tokens to requests:

```typescript
// api.ts
const headers: HeadersInit = {
    'Content-Type': 'application/json',
};
if (this.token) {
    headers['Authorization'] = `Bearer ${this.token}`;
}
```

### Token Expiry Handling

When token expires:
- API returns 401
- Frontend clears token and redirects to login

## Additional Filters

### MdcFilter

Adds request tracking information to logging MDC:
- Request ID
- Client IP
- Request URI

### AwsXRayFilter

Optional AWS X-Ray tracing:
- Distributed tracing for AWS deployments
- Correlates requests across services

## Security Headers

Added by `JwtAuthenticationFilter`:

```java
response.setHeader("Cache-Control", "no-store");
response.setHeader("Pragma", "no-cache");
```

Prevents caching of authenticated responses.

## Configuration Summary

```properties
# JWT
spring.security.jwt.secret=${JWT_SECRET}
spring.security.jwt.expiration=86400000

# CORS
application.cors.allowed-origins=https://yourapp.com

# Features
application.closed-beta=false
application.email.verification.enabled=true
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| SecurityConfig | `backend/.../config/SecurityConfig.java` |
| JwtService | `backend/.../core/service/JwtService.java` |
| JwtAuthenticationFilter | `backend/.../filter/JwtAuthenticationFilter.java` |
| MdcFilter | `backend/.../filter/MdcFilter.java` |
| AwsXRayFilter | `backend/.../filter/AwsXRayFilter.java` |
| UserService | `backend/.../core/service/UserService.java` |
| AuthenticationService | `backend/.../core/service/AuthenticationService.java` |
| AuthContext (Frontend) | `frontend/src/contexts/AuthContext.tsx` |
| API Client (Frontend) | `frontend/src/lib/api.ts` |

## Related Documentation

- [Users](../domain/users.md) - User and role management
- [Configuration](../operations/configuration.md) - Security configuration options
