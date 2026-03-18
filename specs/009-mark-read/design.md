# Mark-Read Feature Design

## Overview

Add `readAt` (nullable timestamp) and `saved` (boolean) fields to `NewsItem` entity. Expose via DTOs and new endpoints. Frontend auto-marks items as read after 3 seconds in detail panel, with manual toggles and keyboard shortcuts.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Frontend                                   │
├─────────────────────────────────────────────────────────────────────┤
│  SourceItemsList                 ItemDetailPanel                     │
│  ├─ NewsItemRow (visual)         ├─ 3s timer → auto-mark read       │
│  ├─ unread badge + bold          ├─ Toggle buttons (read/saved)     │
│  └─ saved bookmark icon          └─ Keyboard: r, s                  │
│                                                                      │
│  SourceCard                      Filters                             │
│  └─ unreadCount badge            └─ All / Unread / Read / Saved     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           Backend                                    │
├─────────────────────────────────────────────────────────────────────┤
│  NewsController                                                      │
│  ├─ PATCH /news/{id}/read   (toggle)                                │
│  └─ PATCH /news/{id}/saved  (toggle)                                │
│                                                                      │
│  NewsItemService                 NewsItemRepository                  │
│  ├─ toggleRead()                 ├─ countBySourceIdAndReadAtIsNull() │
│  ├─ toggleSaved()                └─ findBySourceIdAndFilters()       │
│  └─ getNewsItems() + filters                                         │
│                                                                      │
│  SourceService                                                       │
│  └─ include unreadCount in SourceDTO                                 │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         PostgreSQL                                   │
├─────────────────────────────────────────────────────────────────────┤
│  news_items                                                          │
│  ├─ read_at TIMESTAMP (nullable, null = unread)                     │
│  ├─ saved BOOLEAN (default false)                                   │
│  ├─ INDEX idx_news_items_source_read (source_id, read_at)           │
│  └─ INDEX idx_news_items_source_saved (source_id, saved)            │
└─────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Backend

#### NewsItem Entity Changes
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/model/NewsItem.java`

```java
@Column(name = "read_at")
private LocalDateTime readAt;

@Column(name = "saved", nullable = false)
private Boolean saved = false;
```

#### NewsItemDTO Changes
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/dto/NewsItemDTO.java`

```java
private LocalDateTime readAt;
private Boolean saved;
```

#### SourceDTO Changes
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/dto/SourceDTO.java`

```java
private Long unreadCount;
```

#### NewsItemRepository New Methods
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/repository/NewsItemRepository.java`

```java
long countBySourceIdAndReadAtIsNull(UUID sourceId);

@Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
       "AND (:readStatus IS NULL OR " +
       "(:readStatus = 'UNREAD' AND n.readAt IS NULL) OR " +
       "(:readStatus = 'READ' AND n.readAt IS NOT NULL)) " +
       "AND (:saved IS NULL OR n.saved = :saved) " +
       "ORDER BY n.publishedAt DESC")
Page<NewsItem> findBySourceIdsAndFilters(
    @Param("sourceIds") List<UUID> sourceIds,
    @Param("readStatus") String readStatus,
    @Param("saved") Boolean saved,
    Pageable pageable);
```

#### NewsItemService New Methods
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`

```java
@Transactional
public NewsItemDTO toggleRead(UUID userId, UUID newsItemId) {
    NewsItem item = getItemWithOwnershipCheck(userId, newsItemId);
    item.setReadAt(item.getReadAt() == null ? LocalDateTime.now() : null);
    return mapToDTO(newsItemRepository.save(item));
}

@Transactional
public NewsItemDTO toggleSaved(UUID userId, UUID newsItemId) {
    NewsItem item = getItemWithOwnershipCheck(userId, newsItemId);
    item.setSaved(!item.getSaved());
    return mapToDTO(newsItemRepository.save(item));
}
```

#### NewsController New Endpoints
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/controller/NewsController.java`

```java
@PatchMapping("/{id}/read")
public ResponseEntity<NewsItemDTO> toggleRead(
    @AuthenticationPrincipal UserDetails userDetails,
    @PathVariable UUID id) {
    User user = userService.getInternalUserByUsername(userDetails.getUsername());
    return ResponseEntity.ok(newsItemService.toggleRead(user.getId(), id));
}

@PatchMapping("/{id}/saved")
public ResponseEntity<NewsItemDTO> toggleSaved(
    @AuthenticationPrincipal UserDetails userDetails,
    @PathVariable UUID id) {
    User user = userService.getInternalUserByUsername(userDetails.getUsername());
    return ResponseEntity.ok(newsItemService.toggleSaved(user.getId(), id));
}
```

#### SourceService Changes
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`

Update `mapToDTO` to include unread count:
```java
private SourceDTO mapToDTO(Source source, Long itemCount, Long unreadCount) {
    return SourceDTO.builder()
        // ... existing fields
        .itemCount(itemCount)
        .unreadCount(unreadCount)
        .build();
}
```

Update all callers of mapToDTO to compute unreadCount via:
```java
long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(source.getId());
```

### Frontend

#### Types Changes
**File:** `frontend/src/types/index.ts`

```typescript
interface NewsItemDTO {
  // ... existing fields
  readAt: string | null;
  saved: boolean;
}

interface SourceDTO {
  // ... existing fields
  unreadCount: number;
}

type ReadFilter = 'ALL' | 'UNREAD' | 'READ';
```

#### API Client Changes
**File:** `frontend/src/lib/api.ts`

```typescript
async toggleRead(itemId: string): Promise<NewsItemDTO> {
  return this.request(`/news/${itemId}/read`, { method: 'PATCH' });
}

async toggleSaved(itemId: string): Promise<NewsItemDTO> {
  return this.request(`/news/${itemId}/saved`, { method: 'PATCH' });
}

async getNewsItems(
  sourceId: string,
  page = 0,
  size = 20,
  readFilter?: ReadFilter,
  saved?: boolean
): Promise<PagedResponse<NewsItemDTO>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (sourceId) params.append('sourceId', sourceId);
  if (readFilter && readFilter !== 'ALL') params.append('readStatus', readFilter);
  if (saved) params.append('saved', 'true');
  return this.request(`/news?${params}`);
}
```

#### NewsItemRow Changes
**File:** `frontend/src/components/sources/NewsItemRow.tsx`

Add visual indicators:
- Unread: blue dot before title + font-semibold on title
- Saved: filled bookmark icon on right side
- Read: regular weight title, muted text color

```tsx
<div className="flex items-center gap-2">
  {!item.readAt && <div className="w-2 h-2 rounded-full bg-blue-500" />}
  <span className={item.readAt ? 'text-muted-foreground' : 'font-semibold'}>
    {item.title}
  </span>
</div>
{item.saved && <Bookmark className="h-4 w-4 fill-current" />}
```

#### ItemDetailPanel Changes
**File:** `frontend/src/components/shared/ItemDetailPanel.tsx`

Add 3-second auto-mark timer:
```tsx
useEffect(() => {
  if (!open || !item || item.readAt) return;

  const timer = setTimeout(() => {
    apiClient.toggleRead(item.id).then(onItemUpdated);
  }, 3000);

  return () => clearTimeout(timer);
}, [open, item?.id, item?.readAt]);
```

Add toggle buttons in header:
```tsx
<Button
  variant="ghost"
  size="icon"
  onClick={() => apiClient.toggleRead(item.id).then(onItemUpdated)}
>
  {item.readAt ? <MailOpen /> : <Mail />}
</Button>
<Button
  variant="ghost"
  size="icon"
  onClick={() => apiClient.toggleSaved(item.id).then(onItemUpdated)}
>
  {item.saved ? <Bookmark className="fill-current" /> : <Bookmark />}
</Button>
```

Props change: add `onItemUpdated: (item: NewsItemDTO) => void`

#### SourceCard Changes
**File:** `frontend/src/components/sources/SourceCard.tsx`

Add unread badge next to item count:
```tsx
{source.unreadCount > 0 && (
  <Badge variant="default" className="ml-2">{source.unreadCount}</Badge>
)}
```

#### SourceItemsList Changes
**File:** `frontend/src/components/sources/SourceItemsList.tsx`

Add filter state and controls:
```tsx
const [readFilter, setReadFilter] = useState<ReadFilter>('ALL');
const [showSavedOnly, setShowSavedOnly] = useState(false);

// Pass to API call
const response = await apiClient.getNewsItems(
  sourceId, page, 20, readFilter, showSavedOnly || undefined
);
```

Add filter UI (tabs or button group):
```tsx
<div className="flex gap-2">
  <Button variant={readFilter === 'ALL' ? 'default' : 'outline'} onClick={() => setReadFilter('ALL')}>All</Button>
  <Button variant={readFilter === 'UNREAD' ? 'default' : 'outline'} onClick={() => setReadFilter('UNREAD')}>Unread</Button>
  <Button variant={readFilter === 'READ' ? 'default' : 'outline'} onClick={() => setReadFilter('READ')}>Read</Button>
  <Button variant={showSavedOnly ? 'default' : 'outline'} onClick={() => setShowSavedOnly(!showSavedOnly)}>
    <Bookmark className={showSavedOnly ? 'fill-current' : ''} />
  </Button>
</div>
```

#### Keyboard Navigation Changes
**File:** `frontend/src/hooks/use-keyboard-navigation.ts`

Add new options:
```typescript
interface UseKeyboardNavigationOptions {
  // ... existing
  onToggleRead?: () => void;
  onToggleSaved?: () => void;
}
```

Add cases in switch:
```typescript
case 'r':
  event.preventDefault();
  options.onToggleRead?.();
  break;
case 's':
  event.preventDefault();
  options.onToggleSaved?.();
  break;
```

Update KeyboardShortcutsHelp to include new shortcuts.

## Data Models

### Database Migration
**File:** `backend/src/main/resources/db/migration/V{next}__add_read_saved_to_news_items.sql`

```sql
ALTER TABLE news_items ADD COLUMN read_at TIMESTAMP;
ALTER TABLE news_items ADD COLUMN saved BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_news_items_source_read ON news_items(source_id, read_at);
CREATE INDEX idx_news_items_source_saved ON news_items(source_id, saved);
```

## Error Handling

| Scenario | Backend | Frontend |
|----------|---------|----------|
| Item not found | 404 Not Found | Toast error, no state change |
| Not owner | 403 Forbidden | Toast error, no state change |
| Network error | N/A | Toast error, retry option |
| Timer cleanup | N/A | Clear timeout on unmount/close |

## Testing Strategy

### Backend
- Unit tests for NewsItemService: toggleRead, toggleSaved
- Integration tests for new endpoints (NewsControllerIntegrationTest)
- Repository tests for filter queries

### Frontend
- Component tests for NewsItemRow visual states
- Hook tests for keyboard shortcuts
- Integration test for 3-second timer behavior

## Performance Considerations

- Unread count uses indexed query (`source_id, read_at`) — O(log n)
- Filter queries use same index — no table scan
- Auto-mark timer is client-side only — no polling
- Single API call per toggle action

## Security Considerations

- Ownership check on all endpoints via `getItemWithOwnershipCheck()`
- No bulk operations that could bypass ownership
- Read/saved status only visible to item owner

## Monitoring and Observability

No additional monitoring needed. Existing request logging covers new endpoints.
