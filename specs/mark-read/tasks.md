# Mark-Read Implementation Plan

## Phase 1: Database & Entity Layer

**Goal:** Add `readAt` and `saved` fields to NewsItem with proper indexing.

**Verification:** Run migration, verify columns exist via `\d news_items` in psql.

### Tasks

1.1 **Create Flyway migration V8**
- File: `backend/src/main/resources/db/migrations/V8__add_read_saved_to_news_items.sql`
- Add `read_at TIMESTAMP` column (nullable)
- Add `saved BOOLEAN NOT NULL DEFAULT false` column
- Create index `idx_news_items_source_read ON news_items(source_id, read_at)`
- Create index `idx_news_items_source_saved ON news_items(source_id, saved)`

1.2 **Update NewsItem entity**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/model/NewsItem.java`
- Add `readAt` field with `@Column(name = "read_at")`
- Add `saved` field with `@Column(name = "saved", nullable = false)` and default `false`

1.3 **Update NewsItemDTO**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/dto/NewsItemDTO.java`
- Add `LocalDateTime readAt`
- Add `Boolean saved`

1.4 **Update NewsItemService.mapToDTO**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`
- Map `readAt` and `saved` fields in builder

---

## Phase 2: Toggle Endpoints

**Goal:** Expose PATCH endpoints to toggle read and saved status.

**Verification:**
```bash
# Toggle read
curl -X PATCH http://localhost:3000/api/news/{id}/read -H "Authorization: Bearer $TOKEN"
# Toggle saved
curl -X PATCH http://localhost:3000/api/news/{id}/saved -H "Authorization: Bearer $TOKEN"
```

### Tasks

2.1 **Add repository method for ownership check**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/repository/NewsItemRepository.java`
- Add `Optional<NewsItem> findByIdAndSourceUserId(UUID id, UUID userId)`

2.2 **Add toggleRead service method**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`
- Method: `toggleRead(UUID userId, UUID newsItemId)`
- Get item with ownership check, toggle `readAt` (null ↔ now), save, return DTO
- Throw 404 if not found, 403 if not owner

2.3 **Add toggleSaved service method**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`
- Method: `toggleSaved(UUID userId, UUID newsItemId)`
- Get item with ownership check, toggle `saved` boolean, save, return DTO

2.4 **Add controller endpoints**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/controller/NewsController.java`
- `PATCH /{id}/read` → calls `toggleRead`
- `PATCH /{id}/saved` → calls `toggleSaved`

2.5 **Add integration tests**
- File: `backend/src/test/java/be/transcode/daybrief/server/core/controller/NewsItemIT.java`
- Test: toggle read on unread item → sets readAt
- Test: toggle read on read item → clears readAt
- Test: toggle saved on unsaved item → sets saved=true
- Test: toggle saved on saved item → sets saved=false
- Test: toggle on non-existent item → 404
- Test: toggle on other user's item → 403

---

## Phase 3: Unread Count on Sources

**Goal:** Include `unreadCount` in SourceDTO.

**Verification:** GET /api/sources returns sources with `unreadCount` field.

### Tasks

3.1 **Add unread count repository method**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/repository/NewsItemRepository.java`
- Add `long countBySourceIdAndReadAtIsNull(UUID sourceId)`

3.2 **Update SourceDTO**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/dto/SourceDTO.java`
- Add `Long unreadCount`

3.3 **Update SourceService.mapToDTO**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`
- Change signature to `mapToDTO(Source source, Long itemCount, Long unreadCount)`
- Update all callers: `getSource`, `listSources`, `createSource`, `updateSource`, `refreshSource`
- Compute unreadCount via `newsItemRepository.countBySourceIdAndReadAtIsNull()`

3.4 **Update SourceIT tests**
- File: `backend/src/test/java/be/transcode/daybrief/server/core/controller/SourceIT.java`
- Verify `unreadCount` is present in responses
- Test: source with all unread items → unreadCount = itemCount
- Test: source with some read items → unreadCount < itemCount

---

## Phase 4: Filter Support

**Goal:** Filter news items by read status and saved flag.

**Verification:**
```bash
curl "http://localhost:3000/api/news?sourceId={id}&readStatus=UNREAD" -H "Authorization: Bearer $TOKEN"
curl "http://localhost:3000/api/news?sourceId={id}&saved=true" -H "Authorization: Bearer $TOKEN"
```

### Tasks

4.1 **Add filter repository method**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/repository/NewsItemRepository.java`
- Add query method with optional `readStatus` (UNREAD/READ) and `saved` params
- Use dynamic query or add multiple method overloads

4.2 **Update NewsItemService.searchNewsItems**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`
- Add `String readStatus` and `Boolean saved` parameters
- Apply filters in query

4.3 **Update NewsController**
- File: `backend/src/main/java/be/transcode/daybrief/server/core/controller/NewsController.java`
- Add `@RequestParam(required = false) String readStatus`
- Add `@RequestParam(required = false) Boolean saved`
- Pass to service

4.4 **Add filter integration tests**
- File: `backend/src/test/java/be/transcode/daybrief/server/core/controller/NewsItemIT.java`
- Test: filter by UNREAD returns only items with readAt=null
- Test: filter by READ returns only items with readAt≠null
- Test: filter by saved=true returns only saved items
- Test: combine filters

---

## Phase 5: Frontend Types & API

**Goal:** Update frontend types and API client.

**Verification:** TypeScript compiles without errors.

### Tasks

5.1 **Update NewsItemDTO type**
- File: `frontend/src/types/index.ts`
- Add `readAt: string | null`
- Add `saved: boolean`

5.2 **Update SourceDTO type**
- File: `frontend/src/types/index.ts`
- Add `unreadCount: number`

5.3 **Add ReadFilter type**
- File: `frontend/src/types/index.ts`
- Add `type ReadFilter = 'ALL' | 'UNREAD' | 'READ'`

5.4 **Add toggle methods to API client**
- File: `frontend/src/lib/api.ts`
- Add `toggleRead(itemId: string): Promise<NewsItemDTO>`
- Add `toggleSaved(itemId: string): Promise<NewsItemDTO>`

5.5 **Update getNewsItems with filters**
- File: `frontend/src/lib/api.ts`
- Add optional `readFilter` and `saved` parameters
- Append to query string when provided

---

## Phase 6: Visual Indicators

**Goal:** Show read/unread and saved status visually in item lists.

**Verification:** Browse sources page, see unread dot on new items, bookmark on saved items.

### Tasks

6.1 **Update NewsItemRow component**
- File: `frontend/src/components/sources/NewsItemRow.tsx`
- Add blue dot before title when `!item.readAt`
- Add `font-semibold` to title when unread, `text-muted-foreground` when read
- Add filled Bookmark icon when `item.saved`

6.2 **Update SourceCard component**
- File: `frontend/src/components/sources/SourceCard.tsx`
- Add Badge with `source.unreadCount` when > 0
- Position next to item count

---

## Phase 7: Detail Panel & Auto-Mark

**Goal:** Auto-mark items as read after 3s, add toggle buttons.

**Verification:** Open item detail, wait 3s, item becomes read. Click toggle buttons.

### Tasks

7.1 **Add onItemUpdated prop to ItemDetailPanel**
- File: `frontend/src/components/shared/ItemDetailPanel.tsx`
- Add `onItemUpdated?: (item: NewsItemDTO) => void` prop

7.2 **Add 3-second auto-mark timer**
- File: `frontend/src/components/shared/ItemDetailPanel.tsx`
- useEffect: when open && item && !item.readAt, set 3s timeout
- On timeout: call `apiClient.toggleRead(item.id).then(onItemUpdated)`
- Cleanup: clear timeout on close or unmount

7.3 **Add toggle buttons in header**
- File: `frontend/src/components/shared/ItemDetailPanel.tsx`
- Read toggle: Mail/MailOpen icon based on readAt
- Saved toggle: Bookmark outline/filled based on saved
- onClick: call toggle API, then onItemUpdated

7.4 **Wire up SourceItemsList**
- File: `frontend/src/components/sources/SourceItemsList.tsx`
- Pass `onItemUpdated` to ItemDetailPanel
- Update local items state when item is updated

---

## Phase 8: Filters UI

**Goal:** Add filter controls to source detail page.

**Verification:** Click filter buttons, list updates to show filtered items.

### Tasks

8.1 **Add filter state to SourceItemsList**
- File: `frontend/src/components/sources/SourceItemsList.tsx`
- Add `readFilter` state: `useState<ReadFilter>('ALL')`
- Add `showSavedOnly` state: `useState(false)`

8.2 **Add filter UI**
- File: `frontend/src/components/sources/SourceItemsList.tsx`
- Button group: All / Unread / Read
- Separate Bookmark toggle button for saved filter
- Use Button variant to indicate active filter

8.3 **Update fetch to use filters**
- File: `frontend/src/components/sources/SourceItemsList.tsx`
- Pass `readFilter` and `showSavedOnly` to `apiClient.getNewsItems()`
- Add filters to useEffect/useCallback dependencies

---

## Phase 9: Keyboard Shortcuts

**Goal:** Add `r` and `s` shortcuts for toggle read/saved.

**Verification:** Select item with j/k, press r to toggle read, s to toggle saved.

### Tasks

9.1 **Extend keyboard navigation hook**
- File: `frontend/src/hooks/use-keyboard-navigation.ts`
- Add `onToggleRead?: () => void` to options interface
- Add `onToggleSaved?: () => void` to options interface
- Add case 'r': call onToggleRead
- Add case 's': call onToggleSaved

9.2 **Update KeyboardShortcutsHelp**
- File: `frontend/src/components/shared/KeyboardShortcutsHelp.tsx` (or similar)
- Add `r` — Toggle read/unread
- Add `s` — Toggle saved

9.3 **Wire up in SourceItemsList**
- File: `frontend/src/components/sources/SourceItemsList.tsx`
- Create handlers for toggle read/saved on selected item
- Pass to useKeyboardNavigation hook
