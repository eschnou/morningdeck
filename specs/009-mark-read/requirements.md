# Mark-Read Feature Requirements

## 1. Introduction

Track user read/unread status on news items to surface new content and reduce cognitive load. Additionally, allow users to save items for later deep reading.

## 2. Alignment with Product Vision

From `product.md`:
- **Information overload** — Read tracking helps users focus on unseen content
- **Daily Consumption Flow** — "Mark as Read/Save" is explicitly mentioned in User Flow 2
- **Archive Search** — "Show me high-scoring articles I haven't read yet" is a stated use case
- **Saved Items** — Information Architecture includes "Saved Items" under Archive section

## 3. Functional Requirements

### 3.1 Auto-Mark Item as Read

**User Story:** As a user, I want items to be automatically marked as read when I view them, so that I don't waste time re-scanning content I've already seen.

**Acceptance Criteria:**
- Items are automatically marked as read after 3 seconds of viewing the detail panel
- If user closes detail panel before 3 seconds, item remains unread
- Read status persists across sessions

### 3.2 Manual Read State Toggle

**User Story:** As a user, I want to manually toggle read/unread status, so that I can control my reading queue.

**Acceptance Criteria:**
- Toggle button in detail panel to mark read/unread
- Read items can be marked as unread to revisit later

### 3.3 Save Item for Later

**User Story:** As a user, I want to save items for later reading, so that I can bookmark important stories to revisit.

**Acceptance Criteria:**
- Toggle button to save/unsave an item (bookmark icon)
- Saved status is independent of read status
- Saved items accessible via "Saved" filter

### 3.4 Visual Distinction

**User Story:** As a user, I want to visually distinguish unread from read items and see saved items clearly.

**Acceptance Criteria:**
- Unread items: bold title + unread dot indicator
- Read items: regular weight title, muted styling
- Saved items: bookmark icon (outline when not saved, filled when saved)
- Applies in: News item list, Source detail page, Daily report items

### 3.5 Source-Level Unread Count

**User Story:** As a user, I want to see unread counts on each source, so that I can prioritize sources with new content.

**Acceptance Criteria:**
- Source cards display unread item count badge
- Badge hidden when count is zero

### 3.6 Filter by Read/Saved Status

**User Story:** As a user, I want to filter items by read or saved status.

**Acceptance Criteria:**
- Read filter: "All", "Unread", "Read"
- Saved filter: "Saved" (separate toggle)
- Available on: Source detail page, News search/archive

### 3.7 Keyboard Shortcuts

**User Story:** As a power user, I want keyboard shortcuts to quickly manage read and saved status.

**Acceptance Criteria:**
- `r` — Toggle read/unread on focused item
- `s` — Toggle saved on focused item
- Works in list view and detail panel

## 4. Non-Functional Requirements

### 4.1 Architecture

- Add fields to `NewsItem` entity:
  - `readAt`: nullable timestamp (null = unread)
  - `saved`: boolean (default false)
- Index on `(source_id, read_at)` for unread queries
- Index on `(source_id, saved)` for saved queries

### 4.2 Performance

- Unread counts must not degrade list rendering
- Counts computed efficiently via indexed queries
