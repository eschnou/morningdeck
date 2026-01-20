# Source Items View - Design Document

## Overview

Extend the Source Detail page to display a paginated list of news items below the source details card. Clicking an item opens a slide-in panel showing full content. Vim-style keyboard navigation enables efficient browsing.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SourceDetailPage                                 │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    Source Details Card                           │    │
│  │                    (existing component)                          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     SourceItemsList                              │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │ NewsItemRow (selected)          [date] [author] [→]     │    │    │
│  │  │ NewsItemRow                     [date] [author] [→]     │    │    │
│  │  │ NewsItemRow                     [date] [author] [→]     │    │    │
│  │  │ ...                                                      │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │              PaginationControls (existing)               │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌──────────────────────────┐                                           │
│  │   ItemDetailPanel        │  ← Sheet (right side, 50% width)          │
│  │   (shadcn Sheet)         │                                           │
│  │                          │                                           │
│  │   - Title + external link│                                           │
│  │   - Author, date         │                                           │
│  │   - Content/Summary      │                                           │
│  └──────────────────────────┘                                           │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │              KeyboardShortcutsHelp (dialog, triggered by ?)      │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### New Components

#### 1. `SourceItemsList` (`/components/sources/SourceItemsList.tsx`)

Container for the items list with keyboard navigation logic.

```tsx
interface SourceItemsListProps {
  sourceId: string;
  onRefresh?: () => void;  // Callback for 'r' key
}
```

**Responsibilities:**
- Fetch paginated news items via `apiClient.getNewsItems(sourceId, page)`
- Manage selection state (`selectedIndex`)
- Manage panel open state (`selectedItem`)
- Handle keyboard events via `useKeyboardNavigation` hook
- Render `NewsItemRow` for each item
- Render `PaginationControls` (existing)
- Render `EmptyState` when no items (existing)
- Render `ItemDetailPanel` when item selected

#### 2. `NewsItemRow` (`/components/sources/NewsItemRow.tsx`)

Single row in the items list.

```tsx
interface NewsItemRowProps {
  item: NewsItemDTO;
  isSelected: boolean;
  onClick: () => void;
  onExternalLink: () => void;
}
```

**Display:**
- Title (truncated with ellipsis)
- Published date (relative: "2 hours ago")
- Author (if available)
- External link icon button
- Selection highlight via `data-[state=selected]:bg-muted`

#### 3. `ItemDetailPanel` (`/components/shared/ItemDetailPanel.tsx`)

Slide-in panel using shadcn Sheet. Placed in `/shared` for reuse in Archive view.

```tsx
interface ItemDetailPanelProps {
  item: NewsItemDTO | null;
  open: boolean;
  onClose: () => void;
  onNext?: () => void;      // For j key in panel
  onPrevious?: () => void;  // For k key in panel
  onExternalLink?: () => void;
}
```

**Structure:**
```
┌────────────────────────────────────┐
│ [Title]                       [X]  │  ← SheetHeader + external link
│ Source • Author • Jan 9, 2026      │  ← Metadata row
├────────────────────────────────────┤
│                                    │
│  Content or Summary                │  ← Scrollable content area
│  (rendered as prose)               │
│                                    │
└────────────────────────────────────┘
```

**Sheet customization:**
- Width: `sm:max-w-[50vw]` (override default `sm:max-w-sm`)
- Full height: `h-full overflow-y-auto`

#### 4. `KeyboardShortcutsHelp` (`/components/shared/KeyboardShortcutsHelp.tsx`)

Dialog overlay showing available shortcuts.

```tsx
interface KeyboardShortcutsHelpProps {
  open: boolean;
  onClose: () => void;
  shortcuts: KeyboardShortcut[];
}

interface KeyboardShortcut {
  key: string;
  description: string;
}
```

### New Hook

#### `useKeyboardNavigation` (`/hooks/use-keyboard-navigation.ts`)

Encapsulates keyboard event handling logic.

```tsx
interface UseKeyboardNavigationOptions {
  itemCount: number;
  selectedIndex: number;
  onSelect: (index: number) => void;
  onOpen: () => void;
  onClose: () => void;
  onExternalLink: () => void;
  onRefresh: () => void;
  onToggleHelp: () => void;
  isPanelOpen: boolean;
  disabled?: boolean;
}

function useKeyboardNavigation(options: UseKeyboardNavigationOptions): void
```

**Implementation:**
- Attach `keydown` listener to `document`
- Check `document.activeElement` to disable when in input/textarea
- Handle key mappings per requirements (j, k, Enter, o, x, Escape, e, ?, r)
- Auto-scroll selected item into view via `scrollIntoView({ block: 'nearest' })`

### API Client Addition

Add to `/lib/api.ts`:

```tsx
async getNewsItems(
  sourceId: string,
  page = 0,
  size = 20
): Promise<PagedResponse<NewsItemDTO>> {
  const params = new URLSearchParams({
    sourceId,
    page: page.toString(),
    size: size.toString(),
  });
  return this.request(`/news?${params.toString()}`);
}
```

### Modified Components

#### `SourceDetailPage` (`/pages/SourceDetailPage.tsx`)

Changes:
- Remove `max-w-3xl` constraint to allow wider layout
- Add `SourceItemsList` below existing source card
- Pass `handleRefresh` to `SourceItemsList` for 'r' key binding

## Backend (No Changes Required)

The existing `/api/news` endpoint fully supports this feature:

**Endpoint:** `GET /api/news?sourceId={uuid}&page={n}&size={n}`

**Controller** (`NewsController.java:28-49`):
- Accepts optional `sourceId` query param
- Default pagination: 20 items, sorted by `publishedAt`

**Service** (`NewsItemService.java:42-86`):
- Validates user owns the requested source
- Delegates to repository with source filter
- Maps `NewsItem` → `NewsItemDTO` including `content`, `summary`, `tags`

**Repository** (`NewsItemRepository.java:69-77`):
- `findBySourceIdInAndFilters()` returns items ordered by `publishedAt DESC`

**Response:** Spring `Page<NewsItemDTO>` with standard pagination metadata.

## Data Models

### Frontend Types

Add to `/types/index.ts`:

```tsx
// News item status
export type NewsItemStatus = 'PENDING' | 'SUMMARIZED' | 'PROCESSED' | 'ERROR';

// News item tags (matches backend NewsItemTags)
export interface NewsItemTags {
  topics?: string[];
  entities?: string[];
  sentiment?: string;
}

// News item DTO
export interface NewsItemDTO {
  id: string;
  title: string;
  link: string;
  author: string | null;
  publishedAt: string | null;
  content: string | null;
  summary: string | null;
  tags: NewsItemTags | null;
  sourceId: string;
  sourceName: string;
  createdAt: string;
}
```

### Backend

No changes required. Existing `GET /api/news?sourceId={id}` endpoint provides all needed data.

## Error Handling

| Scenario | Handling |
|----------|----------|
| Failed to load items | Toast error, show empty state with retry button |
| Failed to load page | Toast error, keep current page displayed |
| Item not found (404) | Close panel, toast error, refresh list |
| Network error | Toast with "Check your connection" message |

Error toast pattern (existing):
```tsx
toast({
  title: 'Failed to load items',
  description: error.message || 'An error occurred',
  variant: 'destructive',
});
```

## Testing Strategy

### Unit Tests

| Component | Tests |
|-----------|-------|
| `NewsItemRow` | Renders title, date, author; fires onClick; selection styling |
| `ItemDetailPanel` | Opens/closes; displays content; external link works |
| `KeyboardShortcutsHelp` | Renders shortcuts; closes on Escape |
| `useKeyboardNavigation` | j/k navigation; Enter opens; disabled in inputs |

### Integration Tests

| Test | Description |
|------|-------------|
| Items list loads | Navigate to source detail, verify items appear |
| Pagination works | Click next/previous, verify new items load |
| Panel opens on click | Click item, verify panel opens with content |
| Keyboard navigation | Press j/k, verify selection moves |
| External link | Press e or click icon, verify new tab opens |

### E2E Tests

- Full flow: Login → Sources → Source Detail → Browse items → Open panel → Navigate with keyboard

## Performance Considerations

| Concern | Mitigation |
|---------|------------|
| Large item lists | Pagination (20 items/page), no preloading |
| Content size | Content truncated in list, full content only in panel |
| Re-renders | Memoize `NewsItemRow` with `React.memo` |
| Keyboard events | Single listener on document, not per-row |
| Panel animations | CSS-only animations via Radix/Tailwind |

## Security Considerations

| Concern | Mitigation |
|---------|------------|
| XSS in content | React auto-escapes; use `dangerouslySetInnerHTML` only if needed with DOMPurify |
| Ownership bypass | Backend validates `sourceId` belongs to authenticated user |
| External links | Always use `rel="noopener noreferrer"` |

Note: Content from RSS feeds may contain HTML. If rendering HTML content:
```tsx
import DOMPurify from 'dompurify';

<div
  className="prose prose-sm"
  dangerouslySetInnerHTML={{
    __html: DOMPurify.sanitize(item.content || '')
  }}
/>
```

## Monitoring and Observability

| Metric | Implementation |
|--------|----------------|
| Items list load time | Browser Performance API / existing analytics |
| Keyboard shortcut usage | Optional: track via analytics provider |
| Error rates | Existing error handling logs to console |

No new backend metrics required - existing API metrics cover the `/news` endpoint.

## File Summary

### New Files

| Path | Type |
|------|------|
| `frontend/src/components/sources/SourceItemsList.tsx` | Component |
| `frontend/src/components/sources/NewsItemRow.tsx` | Component |
| `frontend/src/components/shared/ItemDetailPanel.tsx` | Component |
| `frontend/src/components/shared/KeyboardShortcutsHelp.tsx` | Component |
| `frontend/src/hooks/use-keyboard-navigation.ts` | Hook |

### Modified Files

| Path | Changes |
|------|---------|
| `frontend/src/pages/SourceDetailPage.tsx` | Add SourceItemsList, remove width constraint |
| `frontend/src/lib/api.ts` | Add `getNewsItems()` method |
| `frontend/src/types/index.ts` | Add `NewsItemDTO`, `NewsItemStatus`, `NewsItemTags` |
