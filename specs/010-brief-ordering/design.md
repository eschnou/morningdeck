# Brief Ordering Design

## Overview

Add drag-and-drop reordering for briefs in the sidebar. Users drag briefs to reorder, changes persist to backend immediately with optimistic UI updates.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Frontend                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ SidebarBriefsList                                            │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │   │
│  │  │ DndContext  │─▶│SortableCtx  │─▶│ SortableBriefItem   │  │   │
│  │  │ (sensors,   │  │ (items,     │  │ (useSortable hook)  │  │   │
│  │  │  collision) │  │  strategy)  │  │                     │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼ onDragEnd                            │
│                    ┌─────────────────────┐                          │
│                    │ apiClient.reorder() │                          │
│                    └─────────────────────┘                          │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼ POST /daybriefs/reorder
┌─────────────────────────────────────────────────────────────────────┐
│                         Backend                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │
│  │ DayBriefController│─▶│ DayBriefService │─▶│ DayBriefRepository │ │
│  │ reorderBriefs() │  │ reorderBriefs() │  │ saveAll()          │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Frontend

#### New Dependencies

```json
{
  "@dnd-kit/core": "^6.1.0",
  "@dnd-kit/sortable": "^8.0.0",
  "@dnd-kit/utilities": "^3.2.2"
}
```

#### SidebarBriefsList.tsx (Modified)

Wrap brief items with DnD context and sortable components.

```tsx
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable';

// In component:
const sensors = useSensors(
  useSensor(PointerSensor, {
    activationConstraint: { distance: 8 },
  }),
  useSensor(KeyboardSensor, {
    coordinateGetter: sortableKeyboardCoordinates,
  })
);

const handleDragEnd = async (event: DragEndEvent) => {
  const { active, over } = event;
  if (!over || active.id === over.id) return;

  const oldIndex = briefs.findIndex(b => b.id === active.id);
  const newIndex = briefs.findIndex(b => b.id === over.id);

  // Optimistic update
  const newOrder = arrayMove(briefs, oldIndex, newIndex);
  setBriefs(newOrder);

  try {
    await apiClient.reorderBriefs(newOrder.map(b => b.id));
  } catch (error) {
    // Revert on failure
    setBriefs(briefs);
    toast({ title: 'Failed to save order', variant: 'destructive' });
  }
};
```

#### SortableBriefItem.tsx (New Component)

Extracted brief item with sortable behavior.

```tsx
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface SortableBriefItemProps {
  brief: DayBriefDTO;
  isActive: boolean;
  onClick: () => void;
}

export function SortableBriefItem({ brief, isActive, onClick }: SortableBriefItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: brief.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <SidebarMenuItem ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <SidebarMenuButton isActive={isActive} onClick={onClick} tooltip={brief.title}>
        <FileText className="size-4" />
        <span>{brief.title}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}
```

#### apiClient (Modified)

Add reorder method to `src/lib/api.ts`.

```tsx
async reorderBriefs(briefIds: string[]): Promise<void> {
  return this.request('/daybriefs/reorder', {
    method: 'POST',
    body: JSON.stringify({ briefIds }),
  });
}
```

#### Types (Modified)

Add position to DayBriefDTO in `src/types/index.ts`.

```tsx
export interface DayBriefDTO {
  // ... existing fields
  position: number;
}
```

### Backend

#### Database Migration

New file: `V21__add_brief_position.sql`

```sql
-- Add position column for user-defined ordering
ALTER TABLE day_briefs ADD COLUMN position INTEGER;

-- Initialize positions based on creation order (oldest first = lowest position)
WITH ranked AS (
  SELECT id, user_id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at) - 1 AS pos
  FROM day_briefs
)
UPDATE day_briefs SET position = ranked.pos
FROM ranked WHERE day_briefs.id = ranked.id;

-- Make position non-nullable after backfill
ALTER TABLE day_briefs ALTER COLUMN position SET NOT NULL;

-- Index for efficient ordering queries
CREATE INDEX idx_day_briefs_user_position ON day_briefs(user_id, position);
```

#### DayBrief Entity (Modified)

Add position field to `DayBrief.java`.

```java
@Column(nullable = false)
private Integer position;
```

#### DayBriefDTO (Modified)

Add position field.

```java
private Integer position;
```

#### ReorderBriefsRequest (New DTO)

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReorderBriefsRequest {
    @NotNull
    @Size(min = 1)
    private List<UUID> briefIds;
}
```

#### DayBriefRepository (Modified)

Add query methods.

```java
// Replace existing methods with ordered versions
Page<DayBrief> findByUserIdAndStatusOrderByPositionAsc(UUID userId, DayBriefStatus status, Pageable pageable);

Page<DayBrief> findByUserIdOrderByPositionAsc(UUID userId, Pageable pageable);

// Get max position for new briefs
@Query("SELECT COALESCE(MAX(d.position), -1) FROM DayBrief d WHERE d.userId = :userId")
Integer findMaxPositionByUserId(@Param("userId") UUID userId);

// Fetch all briefs for reorder (existing method, returns List instead of Page)
List<DayBrief> findByUserId(UUID userId);
```

#### DayBriefService (Modified)

Add reorder method and update create/list methods.

```java
@Transactional
public void reorderBriefs(UUID userId, List<UUID> briefIds) {
    List<DayBrief> briefs = dayBriefRepository.findByUserId(userId);

    // Verify request contains all user's briefs
    if (briefs.size() != briefIds.size()) {
        throw new IllegalArgumentException("Request must contain all briefs");
    }

    // Map for quick lookup
    Map<UUID, DayBrief> briefMap = briefs.stream()
        .collect(Collectors.toMap(DayBrief::getId, Function.identity()));

    // Verify all IDs exist and update positions
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

// Update createDayBrief to set position at end
@Transactional
public DayBriefDTO createDayBrief(...) {
    Integer maxPosition = dayBriefRepository.findMaxPositionByUserId(userId);

    DayBrief dayBrief = DayBrief.builder()
        // ... existing fields
        .position(maxPosition + 1)
        .build();
    // ...
}

// Update listDayBriefs to use ordered queries
@Transactional(readOnly = true)
public Page<DayBriefDTO> listDayBriefs(UUID userId, DayBriefStatus status, Pageable pageable) {
    Page<DayBrief> dayBriefs;
    if (status != null) {
        dayBriefs = dayBriefRepository.findByUserIdAndStatusOrderByPositionAsc(userId, status, pageable);
    } else {
        dayBriefs = dayBriefRepository.findByUserIdOrderByPositionAsc(userId, pageable);
    }
    return dayBriefs.map(this::mapToDTO);
}
```

#### DayBriefController (Modified)

Add reorder endpoint.

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

## Data Models

### DayBrief Entity Changes

| Field | Type | Change |
|-------|------|--------|
| position | Integer | NEW - User-defined order (0-based) |

### DayBriefDTO Changes

| Field | Type | Change |
|-------|------|--------|
| position | Integer | NEW - Included in list responses |

### New Request DTO

**ReorderBriefsRequest**
| Field | Type | Validation |
|-------|------|------------|
| briefIds | List<UUID> | @NotNull, @Size(min=1) |

## Error Handling

| Scenario | Frontend | Backend |
|----------|----------|---------|
| Network failure | Revert to previous order, show error toast | N/A |
| Invalid brief ID | Show error toast | 400 Bad Request |
| Brief deleted during drag | Refresh list via BRIEFS_CHANGED event | 400 Bad Request |
| Concurrent modification | Last write wins (acceptable for personal ordering) | N/A |

## Testing Strategy

### Backend Tests

**Unit Tests (DayBriefServiceTest)**
- `reorderBriefs_validIds_updatesPositions`
- `reorderBriefs_invalidId_throwsException`
- `reorderBriefs_differentUser_throwsException`
- `createDayBrief_setsPositionAtEnd`

**Integration Tests (DayBriefIT)**
- `reorderBriefs_returnsNoContent`
- `listBriefs_orderedByPosition`
- `createBrief_appearsAtEnd`

### Frontend Tests

**Component Tests (SidebarBriefsList.test.tsx)**
- Renders briefs in position order
- Drag and drop updates local state
- API failure reverts order
- Loading state disables drag

## Performance Considerations

- **Batch update**: Single `saveAll()` call for all position changes
- **Indexed query**: `idx_day_briefs_user_position` enables fast ordered retrieval
- **Optimistic UI**: Instant visual feedback, no loading state during reorder
- **Minimal payload**: Reorder request only sends brief IDs, not full objects

## Security Considerations

- **Ownership validation**: Service verifies all brief IDs belong to requesting user
- **No position gaps**: Positions are consecutive integers, no manipulation allowed
- **Rate limiting**: Standard API rate limits apply

## Monitoring and Observability

- **Logging**: Existing `log.info` pattern for reorder operations
- **Metrics**: No additional metrics required (standard request latency applies)
- **Error tracking**: Failed reorders logged with user context
