# Source Items View - Requirements

## Introduction

This feature adds an items list to the Source Detail page. Users can view all news items ingested from a source, browse them with pagination, and inspect individual item content in a slide-in panel.

## Alignment with Product Vision

Per `specs/product.md`, the Archive & Search (Memory Layer) enables users to:
- Browse all ingested content
- Filter by source, date range, relevance
- Deep-dive into interesting items

This feature directly supports the "Browse Archive" use case by providing source-scoped item browsing. It also enhances Source Management by giving users visibility into what content each source provides.

## Requirements

### R1: Display Items List on Source Detail Page

**User Story:** As a user, I want to see all items from a source when viewing the source details, so that I can understand what content is being ingested.

**Acceptance Criteria:**
- Source details remain at the top of the page (existing behavior)
- Items list appears below source details
- Items are displayed in a scrollable list/table
- Each item shows: title, published date, author (if available)
- Items are sorted by published date (newest first)
- Empty state shown when source has no items

### R2: Paginated Items List

**User Story:** As a user, I want to navigate through all items from a source, so that I can browse historical content without loading everything at once.

**Acceptance Criteria:**
- Default page size: 20 items
- Pagination controls at bottom of list
- Display current page info (e.g., "Showing 1-20 of 156 items")
- Loading state while fetching next page

### R3: Item Detail Slide-in Panel

**User Story:** As a user, I want to view an item's full content when I click on it, so that I can read the article without leaving the source page.

**Acceptance Criteria:**
- Clicking an item row opens a right-side slide-in panel
- Panel displays: title, source name, author, published date, link to original, full content or summary
- Panel can be closed via close button or clicking outside
- Panel width: ~40-50% of viewport on desktop
- Only one item panel can be open at a time

### R4: External Link Access

**User Story:** As a user, I want to open the original article in a new tab, so that I can read it in its original context.

**Acceptance Criteria:**
- External link icon/button visible in item row
- External link in panel header
- Opens in new tab (`target="_blank"`)

### R5: Keyboard Navigation

**User Story:** As a power user, I want to navigate and interact with items using keyboard shortcuts, so that I can browse content efficiently without using the mouse.

**Acceptance Criteria:**

| Key | Context | Action |
|-----|---------|--------|
| `j` | List | Move selection down to next item |
| `k` | List | Move selection up to previous item |
| `j` | Panel open | Close current, open next item |
| `k` | Panel open | Close current, open previous item |
| `Enter` or `o` | Item selected | Open item in panel |
| `x` or `Escape` | Panel open | Close panel |
| `e` | Item selected or panel open | Open external link in new tab |
| `?` | Any | Toggle keyboard shortcuts help overlay |
| `r` | List (no panel) | Refresh source feed |
| `/` | List (no panel) | Focus search/filter input (if present) |

**Additional behaviors:**
- Visual indicator on currently selected item (highlight row)
- Selection wraps: `j` on last item stays on last, `k` on first stays on first
- `j`/`k` with panel open auto-scrolls list to keep selected item visible
- Shortcuts disabled when focus is in an input field
- Shortcuts help overlay shows all available keys

## Non-Functional Requirements

### Architecture
- Reuse existing `/api/news` endpoint with `sourceId` filter
- Add `NewsItemDTO` type to frontend
- Add API client method `getNewsItems(sourceId, params)`
- Create reusable `ItemDetailPanel` component for future use in Archive view

### Performance
- Paginated loading (20 items per page)
- No full list preloading
- Lazy load item content only when panel opens (if content not already in list response)

### Usability
- Responsive: Panel becomes full-width sheet on mobile
- Loading skeletons for list and panel content
- Keyboard shortcuts discoverable via `?` help overlay

### Security
- Backend already enforces ownership via `sourceId` -> user relationship
- No additional security requirements
