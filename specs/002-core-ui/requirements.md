# Core UI Requirements

## Introduction

This feature introduces a cohesive navigation and screen structure for daybrief.ai, enabling users to manage their news sources and briefings through an intuitive left-panel navigation with dedicated screens for each core domain: Home (Today's Brief), Sources, and Briefs.

## Alignment with Product Vision

Per `specs/product.md`, daybrief.ai aims to be "the central intelligence layer between the internet's information firehose and the individual knowledge worker." The UI must surface three core pillars:
- **Sources** (Ingestion Layer) - Where content comes from
- **Briefs** (Delivery Layer) - Personalized daily briefings
- **Today's Brief** (Home) - The primary daily consumption touchpoint

The information architecture in `product.md` Section 7 outlines: Sources, Today's Brief, Archive, Create, Profile, Settings. This feature focuses on the foundational navigation structure and the Sources/Briefs management screens.

---

## Requirements

### REQ-1: Global Navigation Shell

**User Story**: As a user, I want a persistent navigation panel so that I can quickly access different sections of the application.

**Acceptance Criteria**:
- Left sidebar navigation panel visible on all authenticated pages
- Sidebar width: 240px (collapsible to icon-only 64px on smaller screens)
- Navigation sections displayed vertically with icons and labels
- Active section highlighted with visual indicator
- Sections in order: Home, Sources, Briefs
- Settings and user profile accessible from sidebar footer or header
- Sidebar state (expanded/collapsed) persists across sessions

### REQ-2: Home Screen (Today's Brief)

**User Story**: As a user, I want a home screen showing my latest briefing so that I can quickly consume today's most relevant news.

**Acceptance Criteria**:
- Default landing page after login
- Displays the most recent generated report from user's active briefs
- If no briefs exist, show empty state with CTA to create first brief
- If briefs exist but no reports generated yet, show appropriate message
- Report display includes:
  - Brief title and generation timestamp
  - List of report items with headline, source, score, and summary
  - Click-through to original article (external link)
- Option to switch between different briefs if user has multiple

### REQ-3: Sources List Screen

**User Story**: As a user, I want to view all my news sources in one place so that I can understand what feeds my briefings.

**Acceptance Criteria**:
- Accessible via "Sources" navigation item
- Displays paginated list of user's sources
- Each source card shows: name, URL (truncated), type badge, status badge, item count, last fetched timestamp
- Filter by status (All, Active, Paused, Error)
- Search sources by name or URL
- Empty state with CTA when no sources exist
- "Add Source" button prominently placed

### REQ-4: Source Detail/Edit Screen

**User Story**: As a user, I want to view and edit a source's details so that I can manage my news feeds.

**Acceptance Criteria**:
- Click on source from list opens detail view (modal or dedicated panel)
- Displays all source fields: name, URL, type, status, tags, last fetched, last error (if any), item count
- Edit mode allows changing: name, tags, status (Active/Paused)
- Delete action with confirmation dialog (soft delete)
- Show recent news items fetched from this source (last 5-10)

### REQ-5: Add Source Flow

**User Story**: As a user, I want to add new RSS feeds so that I can expand my news coverage.

**Acceptance Criteria**:
- Accessible via "Add Source" button on Sources screen
- Form fields: URL (required), Name (optional, auto-detected from feed), Tags (optional, multi-select/create)
- URL validation with error feedback
- Preview: show detected feed title and sample items before confirming
- Success toast notification after creation
- Redirect to Sources list with new source highlighted

### REQ-6: Briefs List Screen

**User Story**: As a user, I want to view all my configured briefings so that I can manage my daily news digests.

**Acceptance Criteria**:
- Accessible via "Briefs" navigation item
- Displays paginated list of user's briefs
- Each brief card shows: title, description (truncated), frequency badge, schedule time, source count, status badge, last executed timestamp
- Filter by status (All, Active, Paused)
- Empty state with CTA when no briefs exist
- "Create Brief" button prominently placed

### REQ-7: Brief Detail/Edit Screen

**User Story**: As a user, I want to view and edit a brief's configuration so that I can customize my news delivery.

**Acceptance Criteria**:
- Click on brief from list opens detail view (modal or dedicated panel)
- Displays all brief fields: title, description, briefing criteria, sources (list with names), frequency, schedule time, timezone, status, last executed
- Edit mode allows changing all editable fields
- Source selection via multi-select component showing user's active sources
- Delete action with confirmation dialog (soft delete)
- "Execute Now" button to trigger immediate briefing generation

### REQ-8: Create Brief Flow

**User Story**: As a user, I want to create a new briefing so that I can receive personalized news summaries.

**Acceptance Criteria**:
- Accessible via "Create Brief" button on Briefs screen
- Multi-step form or single form with sections:
  1. Basic info: Title (required), Description (optional)
  2. Content: Briefing criteria/prompt (required), Source selection (required, min 1)
  3. Schedule: Frequency (Daily/Weekly), Time, Timezone
- Source selection shows user's active sources with checkboxes
- Form validation with clear error messages
- Success toast notification after creation
- Redirect to Briefs list with new brief highlighted

### REQ-9: Brief Reports View

**User Story**: As a user, I want to view past reports generated by a brief so that I can access historical briefings.

**Acceptance Criteria**:
- Accessible from Brief detail screen
- Paginated list of generated reports for the brief
- Each report shows: generation date, status, item count
- Click on report shows full report content (same format as Home screen)

---

## Non-Functional Requirements

### Architecture
- Maintain existing patterns: React Router for navigation, React Query for data fetching, shadcn/ui for components
- New routes: `/`, `/sources`, `/sources/:id`, `/briefs`, `/briefs/:id`, `/briefs/:id/reports`
- API integration via existing `apiClient` singleton
- Responsive layout: sidebar collapses on screens < 768px

### Performance
- Source and Brief lists implement pagination (20 items default)
- Use React Query caching to minimize redundant API calls
- Lazy load report items when expanding detail views

### Security
- All screens require authentication (use existing `ProtectedRoute`)
- API calls include auth token (handled by `apiClient`)

### Reliability
- Handle API errors with user-friendly toast notifications
- Show loading states during data fetching
- Retry failed requests with exponential backoff (via React Query defaults)

### Usability
- Consistent visual language with existing Settings and Admin screens
- Keyboard navigation support for lists and forms
- Clear visual feedback for all user actions (hover, active, disabled states)
- Empty states guide users toward next actions
- Form validation provides inline error feedback
