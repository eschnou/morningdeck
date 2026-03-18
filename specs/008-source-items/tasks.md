# Source Items View - Implementation Tasks

## Phase 1: Foundation (Types & API)

**Goal:** Establish frontend types and API client method for fetching news items.

**Verification:** Call `apiClient.getNewsItems(sourceId)` from browser console and confirm paginated response.

### Task 1.1: Add NewsItemDTO types

**File:** `frontend/src/types/index.ts`

Add:
```tsx
export type NewsItemStatus = 'PENDING' | 'SUMMARIZED' | 'PROCESSED' | 'ERROR';

export interface NewsItemTags {
  topics?: string[];
  entities?: string[];
  sentiment?: string;
}

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

### Task 1.2: Add getNewsItems API method

**File:** `frontend/src/lib/api.ts`

Add import for `NewsItemDTO` and method:
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

---

## Phase 2: Basic Items List

**Goal:** Display paginated news items list on Source Detail page.

**Verification:** Navigate to source detail page, see items list with pagination controls. Click pagination to load more items.

### Task 2.1: Create NewsItemRow component

**File:** `frontend/src/components/sources/NewsItemRow.tsx`

Props:
- `item: NewsItemDTO`
- `isSelected: boolean`
- `onClick: () => void`
- `onExternalLink: (e: React.MouseEvent) => void`

Display:
- Title (truncated, `line-clamp-1`)
- Published date via `formatDistanceToNow` from date-fns
- Author (if present)
- External link icon button (ExternalLink from lucide-react)
- Selection state: `data-[state=selected]:bg-muted` + `hover:bg-muted/50`
- Use `React.memo` for performance

### Task 2.2: Create SourceItemsList component

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Props:
- `sourceId: string`
- `onRefresh?: () => void`

State:
- `items: NewsItemDTO[]`
- `isLoading: boolean`
- `currentPage: number`
- `totalPages: number`
- `totalElements: number`
- `selectedIndex: number` (default: -1, no selection)
- `selectedItem: NewsItemDTO | null` (for panel)

Features:
- Fetch items on mount and page change via `apiClient.getNewsItems`
- Show loading skeleton while fetching
- Show `EmptyState` (icon: Newspaper) when no items
- Render `NewsItemRow` for each item
- Render `PaginationControls` at bottom
- Show "Showing X-Y of Z items" count

### Task 2.3: Integrate SourceItemsList into SourceDetailPage

**File:** `frontend/src/pages/SourceDetailPage.tsx`

Changes:
- Import `SourceItemsList`
- Remove `max-w-3xl` from main container (use `max-w-5xl` or remove entirely)
- Add `<SourceItemsList sourceId={id!} onRefresh={fetchSource} />` below source card
- Wrap in Card with "Items" title

---

## Phase 3: Item Detail Panel

**Goal:** Click an item to open a slide-in panel showing full content.

**Verification:** Click any item row, panel slides in from right with title, metadata, and content. Click outside or X to close.

### Task 3.1: Create ItemDetailPanel component

**File:** `frontend/src/components/shared/ItemDetailPanel.tsx`

Props:
- `item: NewsItemDTO | null`
- `open: boolean`
- `onClose: () => void`
- `onNext?: () => void`
- `onPrevious?: () => void`
- `onExternalLink?: () => void`

Implementation:
- Use shadcn `Sheet` with `side="right"`
- Custom width class: `sm:max-w-[50vw]` (override default)
- SheetHeader with:
  - Title + external link button
  - Metadata: sourceName, author (if present), publishedAt (formatted)
- Scrollable content area (`overflow-y-auto flex-1`)
- Render `item.content` or `item.summary` as fallback
- For HTML content: use DOMPurify (install if needed: `npm install dompurify @types/dompurify`)
- Apply Tailwind prose classes for typography

### Task 3.2: Wire panel to SourceItemsList

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Changes:
- Import `ItemDetailPanel`
- Add state: `isPanelOpen: boolean`
- On row click: set `selectedItem` and `isPanelOpen = true`
- On panel close: set `isPanelOpen = false`
- Render `<ItemDetailPanel>` at component bottom

---

## Phase 4: Keyboard Navigation

**Goal:** Navigate items with j/k keys, open/close panel with Enter/x/Escape.

**Verification:** Press j/k to move selection. Press Enter to open panel. Press x or Escape to close. Press e to open external link.

### Task 4.1: Create useKeyboardNavigation hook

**File:** `frontend/src/hooks/use-keyboard-navigation.ts`

Interface:
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
```

Implementation:
- `useEffect` with `keydown` listener on `document`
- Check `document.activeElement?.tagName` - skip if INPUT, TEXTAREA, SELECT
- Key handlers:
  - `j`: if panel open, call onClose then onSelect(index+1), else just onSelect(index+1)
  - `k`: same logic but index-1
  - `Enter`, `o`: call onOpen
  - `x`, `Escape`: call onClose
  - `e`: call onExternalLink
  - `?`: call onToggleHelp
  - `r`: if not panel open, call onRefresh
- Clamp index: Math.max(0, Math.min(index, itemCount - 1))
- Cleanup listener on unmount

### Task 4.2: Create KeyboardShortcutsHelp component

**File:** `frontend/src/components/shared/KeyboardShortcutsHelp.tsx`

Props:
- `open: boolean`
- `onClose: () => void`

Implementation:
- Use shadcn `Dialog`
- Title: "Keyboard Shortcuts"
- Grid layout with key + description
- Shortcuts list:
  - `j` / `k` - Navigate down / up
  - `Enter` or `o` - Open item
  - `x` or `Esc` - Close panel
  - `e` - Open external link
  - `r` - Refresh source
  - `?` - Toggle this help
- Close on Escape (built into Dialog)

### Task 4.3: Integrate keyboard navigation into SourceItemsList

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Changes:
- Import `useKeyboardNavigation` and `KeyboardShortcutsHelp`
- Add state: `showHelp: boolean`
- Call `useKeyboardNavigation` with:
  - `itemCount: items.length`
  - `selectedIndex`
  - `onSelect`: update selectedIndex, scroll into view
  - `onOpen`: set selectedItem and isPanelOpen
  - `onClose`: set isPanelOpen = false
  - `onExternalLink`: `window.open(items[selectedIndex].link, '_blank')`
  - `onRefresh`: call props.onRefresh
  - `onToggleHelp`: toggle showHelp
  - `isPanelOpen`
  - `disabled: isLoading`
- Add ref to list container for scrollIntoView
- Render `KeyboardShortcutsHelp` dialog
- Add subtle hint at bottom: "Press ? for keyboard shortcuts"

### Task 4.4: Add scroll-into-view behavior

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Implementation:
- Add `itemRefs` using `useRef<(HTMLDivElement | null)[]>([])`
- Pass ref callback to each `NewsItemRow`
- In `onSelect` callback, call `itemRefs.current[index]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })`

---

## Phase 5: Polish & Edge Cases

**Goal:** Handle edge cases, improve UX, ensure responsive design.

**Verification:** Test on mobile (panel is full-width). Test empty state. Test error handling. Verify keyboard hint visibility.

### Task 5.1: Add loading skeletons

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Add loading state:
- While `isLoading && items.length === 0`, show 5 skeleton rows
- Each skeleton: `<Skeleton className="h-16 w-full" />`

### Task 5.2: Handle panel navigation at list boundaries

**File:** `frontend/src/hooks/use-keyboard-navigation.ts`

Edge cases:
- j on last item with panel open: stay on last, don't close/reopen
- k on first item with panel open: stay on first, don't close/reopen
- Panel navigation should smoothly transition between items (close current, open next)

### Task 5.3: Responsive panel width

**File:** `frontend/src/components/shared/ItemDetailPanel.tsx`

Mobile handling:
- Default Sheet behavior on mobile is already full-width
- Ensure content is readable: add padding, appropriate text sizes
- Test: panel should be usable on 375px width

### Task 5.4: Add item count display

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Above the list, show:
```
{totalElements} items • Page {currentPage + 1} of {totalPages}
```

### Task 5.5: Error handling improvements

**File:** `frontend/src/components/sources/SourceItemsList.tsx`

- On fetch error: show toast, display EmptyState with retry action
- Add `fetchItems` as retry action in EmptyState

---

## Phase 6: Final Integration

**Goal:** Complete integration with clean code and final testing.

**Verification:** Full user flow works: navigate to source, browse items, use keyboard, open panel, paginate. No console errors.

### Task 6.1: Export new components

**File:** `frontend/src/components/sources/index.ts` (create if needed)

Export:
- `SourceItemsList`
- `NewsItemRow`

**File:** `frontend/src/components/shared/index.ts` (create if needed)

Export:
- `ItemDetailPanel`
- `KeyboardShortcutsHelp`

### Task 6.2: Code review checklist

Verify:
- [ ] No TypeScript errors (`npm run build`)
- [ ] No ESLint warnings (`npm run lint`)
- [ ] All imports use `@/` alias
- [ ] External links have `rel="noopener noreferrer"`
- [ ] Loading states for all async operations
- [ ] Error toasts for all API failures
- [ ] Keyboard shortcuts work
- [ ] Panel closes on outside click
- [ ] Responsive on mobile

### Task 6.3: Manual testing scenarios

Test cases:
1. Source with 0 items → Empty state shown
2. Source with 5 items → No pagination shown
3. Source with 50 items → Pagination works
4. Click item → Panel opens
5. Press j/k → Selection moves
6. Press Enter on selected → Panel opens
7. Press x or Escape → Panel closes
8. Press e → External link opens
9. Press ? → Help dialog shows
10. Press r → Source refreshes
11. Mobile viewport → Panel is full-width
12. Network error → Toast shown, retry available
