# Brief Ordering Implementation Tasks

## Phase 1: Backend - Database and Entity Layer

**Goal**: Add position column to database and entity, with migration backfilling existing data.

### Tasks

1. **Create database migration `V21__add_brief_position.sql`**
   - Add nullable `position` column to `day_briefs` table
   - Backfill positions using `ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at)`
   - Set column to NOT NULL after backfill
   - Create index `idx_day_briefs_user_position` on `(user_id, position)`

2. **Update `DayBrief` entity**
   - Add `position` field with `@Column(nullable = false)`
   - No default value needed (handled by migration and service)

3. **Update `DayBriefDTO`**
   - Add `position` field (Integer)

4. **Update `DayBriefService.mapToDTO()`**
   - Include position in DTO mapping

**Verification**: Run `mvn test` - existing tests should pass. Verify migration applies cleanly with `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"`.

---

## Phase 2: Backend - Repository and Service Updates

**Goal**: Briefs returned ordered by position; new briefs get position at end.

### Tasks

1. **Add repository methods to `DayBriefRepository`**
   - `Page<DayBrief> findByUserIdOrderByPositionAsc(UUID userId, Pageable pageable)`
   - `Page<DayBrief> findByUserIdAndStatusOrderByPositionAsc(UUID userId, DayBriefStatus status, Pageable pageable)`
   - `@Query("SELECT COALESCE(MAX(d.position), -1) FROM DayBrief d WHERE d.userId = :userId") Integer findMaxPositionByUserId(@Param("userId") UUID userId)`
   - `List<DayBrief> findByUserId(UUID userId)` (for reorder operation)

2. **Update `DayBriefService.listDayBriefs()`**
   - Use `findByUserIdOrderByPositionAsc` instead of `findByUserId`
   - Use `findByUserIdAndStatusOrderByPositionAsc` instead of `findByUserIdAndStatus`

3. **Update `DayBriefService.createDayBrief()`**
   - Get max position: `findMaxPositionByUserId(userId)`
   - Set `position = maxPosition + 1` on new brief

4. **Add unit tests in `DayBriefServiceTest`**
   - `createDayBrief_setsPositionAtEnd`
   - `listDayBriefs_orderedByPosition`

**Verification**: Run `mvn test`. Create 3 briefs via API, verify they return in creation order (positions 0, 1, 2).

---

## Phase 3: Backend - Reorder Endpoint

**Goal**: POST `/daybriefs/reorder` endpoint updates positions.

### Tasks

1. **Create `ReorderBriefsRequest` DTO**
   - Field: `List<UUID> briefIds` with `@NotNull` and `@Size(min = 1)`
   - Location: `be.transcode.morningdeck.server.core.dto`

2. **Add `DayBriefService.reorderBriefs()` method**
   ```java
   @Transactional
   public void reorderBriefs(UUID userId, List<UUID> briefIds) {
       List<DayBrief> briefs = dayBriefRepository.findByUserId(userId);
       if (briefs.size() != briefIds.size()) {
           throw new IllegalArgumentException("Request must contain all briefs");
       }
       Map<UUID, DayBrief> briefMap = briefs.stream()
           .collect(Collectors.toMap(DayBrief::getId, Function.identity()));
       for (int i = 0; i < briefIds.size(); i++) {
           DayBrief brief = briefMap.get(briefIds.get(i));
           if (brief == null) {
               throw new IllegalArgumentException("Invalid brief ID: " + briefIds.get(i));
           }
           brief.setPosition(i);
       }
       dayBriefRepository.saveAll(briefs);
       log.info("Reordered {} briefs for user {}", briefIds.size(), userId);
   }
   ```

3. **Add endpoint in `DayBriefController`**
   ```java
   @PostMapping("/reorder")
   public ResponseEntity<Void> reorderBriefs(
       @AuthenticationPrincipal User user,
       @Valid @RequestBody ReorderBriefsRequest request
   ) {
       dayBriefService.reorderBriefs(user.getId(), request.getBriefIds());
       return ResponseEntity.noContent().build();
   }
   ```

4. **Add unit tests in `DayBriefServiceTest`**
   - `reorderBriefs_validIds_updatesPositions`
   - `reorderBriefs_missingBriefId_throwsException`
   - `reorderBriefs_invalidBriefId_throwsException`

5. **Add integration tests in `BriefingCoreIT`**
   - `reorderBriefs_returnsNoContent`
   - `reorderBriefs_persistsNewOrder`
   - `reorderBriefs_otherUsersBrief_returns400`

**Verification**: Run `mvn test`. Test via curl:
```bash
curl -X POST http://localhost:3000/daybriefs/reorder \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"briefIds": ["uuid3", "uuid1", "uuid2"]}'
```

---

## Phase 4: Frontend - Dependencies and Types

**Goal**: Install @dnd-kit and update TypeScript types.

### Tasks

1. **Install dependencies**
   ```bash
   cd frontend && npm install @dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities
   ```

2. **Update `src/types/index.ts`**
   - Add `position: number` to `DayBriefDTO` interface

3. **Add `reorderBriefs` method to `src/lib/api.ts`**
   ```typescript
   async reorderBriefs(briefIds: string[]): Promise<void> {
     return this.request('/daybriefs/reorder', {
       method: 'POST',
       body: JSON.stringify({ briefIds }),
     });
   }
   ```

**Verification**: Run `npm run build` - should compile without errors.

---

## Phase 5: Frontend - Drag and Drop UI

**Goal**: Implement sortable briefs list with optimistic updates.

### Tasks

1. **Create `SortableBriefItem.tsx` component**
   - Location: `src/components/layout/SortableBriefItem.tsx`
   - Use `useSortable` hook from `@dnd-kit/sortable`
   - Apply transform/transition styles
   - Reduce opacity when dragging
   - Forward ref to `SidebarMenuItem`

2. **Update `SidebarBriefsList.tsx`**
   - Import DnD components: `DndContext`, `SortableContext`, `closestCenter`, sensors
   - Configure sensors: `PointerSensor` (distance: 8), `KeyboardSensor`
   - Wrap `SidebarMenu` with `DndContext` and `SortableContext`
   - Use `verticalListSortingStrategy`
   - Implement `handleDragEnd`:
     - Optimistic update with `arrayMove`
     - Call `apiClient.reorderBriefs()`
     - Revert on error with toast notification
   - Disable drag during loading state
   - Replace inline brief rendering with `SortableBriefItem`

3. **Update `handleCreateSuccess` in `SidebarBriefsList.tsx`**
   - Append new brief to end of list (not prepend)
   - Change `[brief, ...prev]` to `[...prev, brief]`

**Verification**:
1. Run `npm run dev`
2. Create 3 briefs
3. Drag to reorder
4. Refresh page - order should persist
5. Disconnect network, try to reorder - should revert with error toast

---

## Phase 6: Polish and Edge Cases

**Goal**: Handle edge cases and improve UX.

### Tasks

1. **Touch device support**
   - Add `TouchSensor` to sensors array
   - Configure with `activationConstraint: { delay: 250, tolerance: 5 }`

2. **Keyboard accessibility**
   - Verify arrow keys work for reordering (provided by `KeyboardSensor` + `sortableKeyboardCoordinates`)

3. **Visual feedback improvements**
   - Add `cursor: grab` on hover, `cursor: grabbing` when dragging
   - Optional: Add subtle drag handle icon (GripVertical from lucide-react)

4. **Handle concurrent modifications**
   - If reorder fails with 400 (brief count mismatch), emit `BRIEFS_CHANGED` to refresh list

**Verification**:
1. Test on mobile device or Chrome DevTools mobile emulation
2. Test keyboard navigation with Tab + Enter + Arrow keys
3. Delete a brief in another tab, try reordering - should refresh and show updated list
