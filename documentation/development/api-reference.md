# API Reference

Morning Deck exposes a REST API for all operations. This document provides an overview of available endpoints.

## Base URL

- **Development:** `http://localhost:3000`
- **Production:** Your configured domain

## Authentication

All endpoints except `/auth/**` require JWT authentication.

### Login Flow

1. Register or login to get a JWT token
2. Include token in all subsequent requests:
   ```
   Authorization: Bearer <token>
   ```

## API Documentation

**Interactive docs available at:**
- Swagger UI: `/swagger-ui.html`
- OpenAPI spec: `/api-docs`
- OpenAPI YAML: `/api-docs.yaml`

## Endpoints Overview

### Authentication (`/auth/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register new user |
| POST | `/auth/login` | Authenticate and get token |
| GET | `/auth/verify-email` | Verify email address |
| POST | `/auth/resend-verification` | Resend verification email |

#### Register

```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "fullName": "John Doe",
  "inviteCode": "BETA123"  // Required if closed-beta enabled
}
```

#### Login

```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "fullName": "John Doe",
    "roles": ["USER"]
  }
}
```

### Users (`/users/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users/me` | Get current user profile |
| PATCH | `/users/me` | Update profile |
| POST | `/users/me/avatar` | Upload avatar |
| DELETE | `/users/me/avatar` | Delete avatar |
| PUT | `/users/me/password` | Change password |
| GET | `/users/{id}` | Get public profile |

### Briefings (`/daybriefs/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/daybriefs` | List user's briefings |
| POST | `/daybriefs` | Create briefing |
| GET | `/daybriefs/{id}` | Get briefing details |
| PUT | `/daybriefs/{id}` | Update briefing |
| DELETE | `/daybriefs/{id}` | Delete briefing |
| POST | `/daybriefs/reorder` | Reorder briefings |
| POST | `/daybriefs/{id}/execute` | Execute briefing manually |
| POST | `/daybriefs/{id}/mark-all-read` | Mark all items read |

#### Create Briefing

```http
POST /daybriefs
Content-Type: application/json
Authorization: Bearer <token>

{
  "title": "Tech News",
  "description": "Daily tech updates",
  "briefing": "Focus on AI and startups",
  "frequency": "DAILY",
  "scheduleTime": "08:00",
  "timezone": "America/New_York",
  "emailDeliveryEnabled": true
}
```

### Briefing Sources (`/daybriefs/{id}/sources/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/daybriefs/{id}/sources` | List sources for briefing |
| POST | `/daybriefs/{id}/sources` | Add source to briefing |

### Briefing Items (`/daybriefs/{id}/items`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/daybriefs/{id}/items` | List/search news items |

**Query parameters:**
- `q` - Search query (uses Meilisearch if enabled)
- `sourceId` - Filter by source
- `readStatus` - Filter: `read`, `unread`
- `saved` - Filter: `true`, `false`
- `minScore` - Minimum relevance score
- `page`, `size` - Pagination

### Briefing Reports (`/daybriefs/{id}/reports/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/daybriefs/{id}/reports` | List reports |
| GET | `/daybriefs/{id}/reports/{reportId}` | Get report details |
| DELETE | `/daybriefs/{id}/reports/{reportId}` | Delete report |

### Sources (`/sources/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/sources` | List all user's sources |
| POST | `/sources` | Create source |
| GET | `/sources/{id}` | Get source details |
| PUT | `/sources/{id}` | Update source |
| DELETE | `/sources/{id}` | Delete source |
| POST | `/sources/{id}/refresh` | Trigger manual refresh |
| POST | `/sources/{id}/mark-all-read` | Mark all items read |

#### Create Source

```http
POST /sources
Content-Type: application/json
Authorization: Bearer <token>

{
  "briefingId": "uuid",
  "name": "TechCrunch",
  "url": "https://techcrunch.com/feed/",
  "type": "RSS",
  "refreshIntervalMinutes": 60,
  "tags": ["tech", "startups"]
}
```

**Source types:**
- `RSS` - RSS/Atom feed
- `WEB` - Web page scraping
- `EMAIL` - Email newsletter
- `REDDIT` - Reddit subreddit

### News Items (`/news/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/news` | Search all news items |
| GET | `/news/{id}` | Get item details |
| PATCH | `/news/{id}/read` | Toggle read status |
| PATCH | `/news/{id}/saved` | Toggle saved status |

**Search parameters:**
- `q` - Search query
- `sourceId` - Filter by source
- `from`, `to` - Date range (ISO 8601)
- `readStatus` - `read`, `unread`
- `saved` - `true`, `false`
- `page`, `size`, `sort` - Pagination

### Subscription (`/subscription/**`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/subscription` | Get current subscription |
| POST | `/subscription/upgrade` | Upgrade plan |

### Admin (`/admin/**`)

**Requires ADMIN role.**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/users` | List all users |
| GET | `/admin/users/{id}` | Get user details |
| PATCH | `/admin/users/{id}` | Update user |
| DELETE | `/admin/users/{id}` | Delete user |
| GET | `/admin/stats` | Get system statistics |
| POST | `/admin/invite-codes` | Generate invite codes |

## Common Patterns

### Pagination

All list endpoints support pagination:

```http
GET /daybriefs?page=0&size=20&sort=createdAt,desc
```

**Response format:**
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 100,
  "totalPages": 5
}
```

### Error Responses

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/daybriefs"
}
```

**Common status codes:**
- `400` - Bad request (validation error)
- `401` - Unauthorized (invalid/missing token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not found
- `409` - Conflict (e.g., duplicate email)
- `500` - Internal server error

### Validation Errors

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "must be a valid email address"
    }
  ]
}
```

## DTOs Reference

### DayBriefDTO

```json
{
  "id": "uuid",
  "title": "Tech News",
  "description": "Daily tech updates",
  "briefing": "Focus on AI",
  "frequency": "DAILY",
  "scheduleDayOfWeek": null,
  "scheduleTime": "08:00",
  "timezone": "America/New_York",
  "status": "ACTIVE",
  "emailDeliveryEnabled": true,
  "lastExecutedAt": "2024-01-15T08:00:00Z",
  "sourceCount": 5,
  "unreadCount": 12,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-15T08:00:00Z"
}
```

### SourceDTO

```json
{
  "id": "uuid",
  "briefingId": "uuid",
  "name": "TechCrunch",
  "url": "https://techcrunch.com/feed/",
  "type": "RSS",
  "status": "ACTIVE",
  "fetchStatus": "IDLE",
  "refreshIntervalMinutes": 60,
  "tags": ["tech", "startups"],
  "extractionPrompt": null,
  "errorMessage": null,
  "lastFetchedAt": "2024-01-15T10:00:00Z",
  "itemCount": 150,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### NewsItemDTO

```json
{
  "id": "uuid",
  "sourceId": "uuid",
  "sourceName": "TechCrunch",
  "title": "OpenAI Announces GPT-5",
  "url": "https://techcrunch.com/...",
  "author": "Sarah Smith",
  "summary": "AI summary of article...",
  "content": "Full article content...",
  "score": 85,
  "scoreReasoning": "Highly relevant to AI focus...",
  "sentiment": "POSITIVE",
  "tags": {
    "topics": ["AI", "Machine Learning"],
    "people": ["Sam Altman"],
    "companies": ["OpenAI"],
    "technologies": ["GPT"]
  },
  "isRead": false,
  "saved": true,
  "publishedAt": "2024-01-15T09:00:00Z",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

## Rate Limiting

No explicit rate limiting is implemented. The system naturally throttles based on:
- Credit consumption
- Queue processing capacity

## Key Files Reference

| Controller | File Path |
|------------|-----------|
| AuthController | `backend/.../controller/AuthController.java` |
| UserController | `backend/.../controller/UserController.java` |
| DayBriefController | `backend/.../controller/DayBriefController.java` |
| SourceController | `backend/.../controller/SourceController.java` |
| NewsController | `backend/.../controller/NewsController.java` |
| SubscriptionController | `backend/.../controller/SubscriptionController.java` |
| AdminController | `backend/.../controller/AdminController.java` |

## Related Documentation

- [Local Setup](./local-setup.md) - Development environment
- [Security](../architecture/security.md) - Authentication details
- [Users](../domain/users.md) - User management
- [Briefings](../domain/briefings.md) - Briefing operations
