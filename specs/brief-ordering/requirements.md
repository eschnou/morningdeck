# Brief Ordering Requirements

## Introduction

Enable users to reorder briefs in the sidebar via drag-and-drop. Currently, briefs appear in undefined database order with no user control over their arrangement. This feature lets users organize briefs according to personal preference and workflow.

## Alignment with Product Vision

From `product.md`, Morning Deck serves knowledge professionals who need to stay informed across multiple domains. These users typically have multiple active briefs (e.g., "AI News", "Competitor Watch", "Industry Trends"). The ability to reorder briefs supports:

- **Personalization**: Users prioritize what they see first each morning
- **Workflow optimization**: Most-used briefs accessible at the top
- **Information architecture**: Users organize briefs logically (e.g., by topic, frequency, or importance)

## Requirements

### R1: Drag-and-Drop Reordering

**User Story**: As a user, I want to drag briefs in the sidebar to reorder them, so that I can arrange them according to my preferences.

**Acceptance Criteria**:
- User can drag any brief item in the sidebar
- Visual feedback shows the dragged item and drop target position
- Brief list reorders on drop
- Order persists across page refreshes and sessions
- Works in both expanded and collapsed sidebar states
- Reordering is disabled during loading states

### R2: Position Persistence

**User Story**: As a user, I want my brief ordering to be saved, so that my arrangement persists across sessions.

**Acceptance Criteria**:
- New position is saved to backend immediately on drop
- If save fails, revert to previous order and show error toast
- Order syncs correctly if user has multiple browser tabs open

### R3: New Brief Positioning

**User Story**: As a user, I want newly created briefs to appear at a predictable location, so that I know where to find them.

**Acceptance Criteria**:
- New briefs appear at the end of the list
- Existing briefs retain their positions

### R4: Touch Device Support

**User Story**: As a mobile user, I want to reorder briefs via touch gestures, so that I can organize on any device.

**Acceptance Criteria**:
- Long-press initiates drag on touch devices
- Touch drag behaves same as mouse drag
- Scroll still works normally when not dragging

## Non-Functional Requirements

### Architecture

- Backend: Add `position` integer column to `day_briefs` table
- Backend: New endpoint `POST /daybriefs/reorder` accepting list of brief IDs in order
- Frontend: Use `@dnd-kit` library (consistent with modern React patterns, accessible, performant)
- Emit `BRIEFS_CHANGED` event after successful reorder

### Performance

- Drag preview renders at 60fps
- Backend reorder operation completes in <500ms
- Optimistic UI update (reorder instantly, revert on failure)

### Reliability

- Handle concurrent modifications (e.g., brief deleted while being dragged)
- Graceful degradation if JavaScript fails (static list still usable)

### Usability

- Clear drag handle affordance (cursor change, optional handle icon)
- Accessible via keyboard (arrow keys to move, Enter to confirm)
- Animation on reorder for visual continuity
