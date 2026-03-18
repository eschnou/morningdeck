# Core UI Design Document

## Overview

Implement a sidebar-based navigation shell for daybrief.ai with three main sections (Home, Sources, Briefs) and associated CRUD screens. The design leverages the existing shadcn/ui Sidebar component and follows established patterns from the Settings and Admin pages.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        SidebarProvider                          │
├────────────────┬────────────────────────────────────────────────┤
│                │                                                │
│    Sidebar     │              SidebarInset (main)               │
│    (240px)     │                                                │
│                │    ┌──────────────────────────────────────┐    │
│  ┌──────────┐  │    │            Header                    │    │
│  │  Header  │  │    │  (SidebarTrigger + Breadcrumb)       │    │
│  │ (Logo)   │  │    └──────────────────────────────────────┘    │
│  └──────────┘  │    ┌──────────────────────────────────────┐    │
│  ┌──────────┐  │    │                                      │    │
│  │  Content │  │    │         Page Content                 │    │
│  │  - Home  │  │    │    (Routes render here)              │    │
│  │  - Src   │  │    │                                      │    │
│  │  - Brief │  │    │                                      │    │
│  └──────────┘  │    └──────────────────────────────────────┘    │
│  ┌──────────┐  │                                                │
│  │  Footer  │  │                                                │
│  │(Settings)│  │                                                │
│  └──────────┘  │                                                │
└────────────────┴────────────────────────────────────────────────┘
```

---

## Architecture

### Layout Structure

Replace the current `Navbar` + page content pattern with a `SidebarProvider` wrapping a `Sidebar` + `SidebarInset` layout.

```
App.tsx
├── SidebarProvider (wraps all authenticated routes)
│   ├── AppSidebar (new component)
│   │   ├── SidebarHeader (logo/brand)
│   │   ├── SidebarContent
│   │   │   └── SidebarMenu (Home, Sources, Briefs)
│   │   └── SidebarFooter (Settings, User dropdown)
│   └── SidebarInset
│       ├── PageHeader (trigger, breadcrumb)
│       └── <Outlet /> (React Router nested routes)
└── Routes outside SidebarProvider (login, register, public pages)
```

### Routing Structure

```tsx
// New route hierarchy in App.tsx
<Routes>
  {/* Public routes */}
  <Route path="/" element={<Index />} />
  <Route path="/auth/login" element={<Login />} />
  <Route path="/auth/register" element={<Register />} />

  {/* Protected routes with sidebar layout */}
  <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
    <Route index element={<Navigate to="/home" replace />} />
    <Route path="/home" element={<HomePage />} />
    <Route path="/sources" element={<SourcesPage />} />
    <Route path="/sources/:id" element={<SourceDetailPage />} />
    <Route path="/briefs" element={<BriefsPage />} />
    <Route path="/briefs/:id" element={<BriefDetailPage />} />
    <Route path="/briefs/:id/reports" element={<BriefReportsPage />} />
    <Route path="/briefs/:id/reports/:reportId" element={<ReportDetailPage />} />
    <Route path="/settings" element={<SettingsPage />} />
    <Route path="/admin" element={<ProtectedRoute requireAdmin><AdminPage /></ProtectedRoute>} />
  </Route>

  <Route path="*" element={<NotFound />} />
</Routes>
```

---

## Components and Interfaces

### New Components

#### 1. `AppLayout.tsx`
Main layout wrapper for authenticated pages.

```tsx
interface AppLayoutProps {}

// Structure:
// - SidebarProvider with defaultOpen={true}
// - AppSidebar component
// - SidebarInset containing:
//   - PageHeader (sticky, contains SidebarTrigger)
//   - main content area with <Outlet />
```

#### 2. `AppSidebar.tsx`
Navigation sidebar component.

```tsx
interface NavItem {
  title: string;
  url: string;
  icon: LucideIcon;
  badge?: number | string;
}

// Props: none (uses useAuth and useLocation internally)

// Structure:
// - SidebarHeader: Logo + app name (collapsible to icon only)
// - SidebarContent > SidebarGroup > SidebarMenu:
//   - Home (Newspaper icon)
//   - Sources (Rss icon)
//   - Briefs (FileText icon)
// - SidebarFooter:
//   - Settings link (Settings icon)
//   - User dropdown (avatar, logout)
// - SidebarRail (for resize interaction)
```

#### 3. `PageHeader.tsx`
Consistent header for all pages inside layout.

```tsx
interface PageHeaderProps {
  title?: string;
  children?: React.ReactNode; // For action buttons
}

// Structure:
// - SidebarTrigger (hamburger icon)
// - Separator
// - Breadcrumb (auto-generated from route)
// - Spacer
// - {children} slot for page actions
```

#### 4. `HomePage.tsx`
Today's brief display.

```tsx
// State:
// - briefs: DayBriefDTO[] (user's briefs)
// - selectedBriefId: UUID | null
// - latestReport: DailyReportDTO | null

// Structure:
// - Brief selector dropdown (if multiple briefs)
// - Report display card:
//   - Header: brief title, generated timestamp
//   - Report items list (ReportItemCard component)
// - Empty states for: no briefs, no reports
```

#### 5. `SourcesPage.tsx`
Source list with filtering.

```tsx
// State:
// - sources: SourceDTO[]
// - statusFilter: SourceStatus | 'ALL'
// - searchQuery: string
// - pagination: { page, totalPages }

// Structure:
// - Header: title, Add Source button
// - Filter bar: status dropdown, search input
// - Source cards grid/list (SourceCard component)
// - Pagination controls
// - Empty state with CTA
```

#### 6. `SourceDetailPage.tsx`
Source view/edit with recent items.

```tsx
// URL param: id (source UUID)
// State:
// - source: SourceDTO | null
// - isEditing: boolean
// - recentItems: NewsItemDTO[] (last 10)

// Structure:
// - Back link to /sources
// - Source info card (view mode)
// - Edit form (edit mode)
// - Recent news items section
// - Delete confirmation dialog
```

#### 7. `AddSourceDialog.tsx`
Modal for creating new source.

```tsx
interface AddSourceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (source: SourceDTO) => void;
}

// State:
// - url: string
// - name: string (optional, populated from validation)
// - tags: string[]
// - isValidating: boolean
// - validationResult: { title, sampleItems } | null
// - errors: Record<string, string>

// Steps:
// 1. Enter URL -> Validate button
// 2. Show preview (title, sample items) -> Confirm button
// 3. Create source -> Success toast, close dialog
```

#### 8. `BriefsPage.tsx`
Brief list with filtering.

```tsx
// State:
// - briefs: DayBriefDTO[]
// - statusFilter: DayBriefStatus | 'ALL'
// - pagination: { page, totalPages }

// Structure:
// - Header: title, Create Brief button
// - Filter bar: status dropdown
// - Brief cards grid (BriefCard component)
// - Pagination controls
// - Empty state with CTA
```

#### 9. `BriefDetailPage.tsx`
Brief view/edit.

```tsx
// URL param: id (brief UUID)
// State:
// - brief: DayBriefDTO | null
// - sources: SourceDTO[] (for source selection)
// - isEditing: boolean

// Structure:
// - Back link to /briefs
// - Brief info card (view mode)
// - Edit form (edit mode)
// - Execute Now button
// - View Reports link
// - Delete confirmation dialog
```

#### 10. `CreateBriefDialog.tsx`
Modal for creating new brief.

```tsx
interface CreateBriefDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (brief: DayBriefDTO) => void;
}

// State:
// - formData: { title, description, briefing, sourceIds, frequency, scheduleTime, timezone }
// - availableSources: SourceDTO[]
// - errors: Record<string, string>

// Structure:
// - Single form with sections or stepper
// - Source multi-select with checkboxes
// - Time picker for schedule
// - Timezone select
```

#### 11. `BriefReportsPage.tsx`
Report history for a brief.

```tsx
// URL params: id (brief UUID)
// State:
// - reports: DailyReportDTO[]
// - pagination: { page, totalPages }

// Structure:
// - Back link to /briefs/:id
// - Report list (date, status, item count)
// - Click to navigate to /briefs/:id/reports/:reportId
```

#### 12. `ReportDetailPage.tsx`
Full report view.

```tsx
// URL params: id (brief UUID), reportId (report UUID)
// State:
// - report: DailyReportDTO | null

// Structure:
// - Back link to /briefs/:id/reports
// - Report header (brief title, generated date)
// - Report items list (same as HomePage)
```

### Reusable Sub-Components

#### `SourceCard.tsx`
```tsx
interface SourceCardProps {
  source: SourceDTO;
  onClick?: () => void;
}
// Displays: name, URL (truncated), type badge, status badge, item count, last fetched
```

#### `BriefCard.tsx`
```tsx
interface BriefCardProps {
  brief: DayBriefDTO;
  onClick?: () => void;
}
// Displays: title, description (truncated), frequency badge, schedule time, source count, status badge
```

#### `ReportItemCard.tsx`
```tsx
interface ReportItemCardProps {
  item: ReportItemDTO;
}
// Displays: position, headline, source name, score, summary, external link
```

#### `StatusBadge.tsx`
```tsx
interface StatusBadgeProps {
  status: SourceStatus | DayBriefStatus | ReportStatus;
}
// Maps status to appropriate badge variant (default, secondary, destructive, outline)
```

#### `EmptyState.tsx`
```tsx
interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: { label: string; onClick: () => void };
}
```

---

## Data Models

### Frontend Types (new file: `src/types/index.ts`)

```tsx
// Enums
export type SourceType = 'RSS';
export type SourceStatus = 'ACTIVE' | 'PAUSED' | 'ERROR' | 'DELETED';
export type BriefingFrequency = 'DAILY' | 'WEEKLY';
export type DayBriefStatus = 'ACTIVE' | 'PAUSED' | 'DELETED';
export type ReportStatus = 'PENDING' | 'GENERATED' | 'ERROR';

// DTOs
export interface SourceDTO {
  id: string;
  url: string;
  name: string;
  type: SourceType;
  status: SourceStatus;
  tags: string[];
  lastFetchedAt: string | null;
  lastError: string | null;
  itemCount: number;
  createdAt: string;
}

export interface DayBriefDTO {
  id: string;
  title: string;
  description: string | null;
  briefing: string;
  sourceIds: string[];
  frequency: BriefingFrequency;
  scheduleTime: string; // HH:mm:ss
  timezone: string;
  status: DayBriefStatus;
  lastExecutedAt: string | null;
  sourceCount: number;
  createdAt: string;
}

export interface ReportItemDTO {
  id: string;
  newsItemId: string;
  title: string;
  link: string;
  sourceName: string;
  summary: string | null;
  score: number;
  position: number;
}

export interface DailyReportDTO {
  id: string;
  dayBriefId: string;
  dayBriefTitle: string;
  dayBriefDescription: string | null;
  generatedAt: string;
  status: ReportStatus;
  items: ReportItemDTO[];
  itemCount: number;
}

// Request types
export interface CreateSourceRequest {
  url: string;
  name?: string;
  type?: SourceType;
  tags?: string[];
}

export interface UpdateSourceRequest {
  name?: string;
  tags?: string[];
  status?: 'ACTIVE' | 'PAUSED';
}

export interface CreateDayBriefRequest {
  title: string;
  description?: string;
  briefing: string;
  sourceIds: string[];
  frequency: BriefingFrequency;
  scheduleTime: string;
  timezone?: string;
}

export interface UpdateDayBriefRequest {
  title?: string;
  description?: string;
  briefing?: string;
  sourceIds?: string[];
  frequency?: BriefingFrequency;
  scheduleTime?: string;
  timezone?: string;
  status?: 'ACTIVE' | 'PAUSED';
}
```

### API Client Extensions (`src/lib/api.ts`)

```tsx
// Add to ApiClient class:

// Sources
async getSources(page = 0, size = 20, status?: SourceStatus): Promise<PagedResponse<SourceDTO>>
async getSource(id: string): Promise<SourceDTO>
async createSource(data: CreateSourceRequest): Promise<SourceDTO>
async updateSource(id: string, data: UpdateSourceRequest): Promise<SourceDTO>
async deleteSource(id: string): Promise<void>
async validateSource(url: string): Promise<{ title: string; sampleItems: string[] }>

// Day Briefs
async getDayBriefs(page = 0, size = 20, status?: DayBriefStatus): Promise<PagedResponse<DayBriefDTO>>
async getDayBrief(id: string): Promise<DayBriefDTO>
async createDayBrief(data: CreateDayBriefRequest): Promise<DayBriefDTO>
async updateDayBrief(id: string, data: UpdateDayBriefRequest): Promise<DayBriefDTO>
async deleteDayBrief(id: string): Promise<void>
async executeDayBrief(id: string): Promise<void>

// Reports
async getDayBriefReports(briefId: string, page = 0, size = 20): Promise<PagedResponse<DailyReportDTO>>
async getDayBriefReport(briefId: string, reportId: string): Promise<DailyReportDTO>

// Latest report for home
async getLatestReport(briefId?: string): Promise<DailyReportDTO | null>
```

---

## Error Handling

### API Errors
- All API calls wrapped in try/catch
- Display toast notification with error message
- Show inline error states for failed data fetches
- Retry button for recoverable errors

### Form Validation
- Zod schemas for all forms (consistent with existing patterns)
- Inline error messages below fields
- Disable submit button when form invalid or loading

### Loading States
- Skeleton components for list items during initial load
- Loader2 spinner for button loading states
- Full-page loader for initial auth check

### Empty States
- Distinct empty states for:
  - No sources: "Add your first source to get started"
  - No briefs: "Create your first brief to receive daily summaries"
  - No reports: "No reports generated yet. Execute your brief to generate one."
  - Search no results: "No sources match your search"

---

## Testing Strategy

### Unit Tests
- Component rendering tests for all new components
- Form validation logic tests
- Status badge mapping tests

### Integration Tests
- API client method tests with MSW mocking
- Full page flow tests (list -> detail -> edit)
- Dialog open/close/submit flows

### E2E Tests (Playwright)
- Source CRUD flow: list -> add -> view -> edit -> delete
- Brief CRUD flow: list -> create -> view -> edit -> execute -> delete
- Navigation between sections
- Sidebar collapse/expand persistence
- Mobile responsive behavior

---

## Performance Considerations

### Data Fetching
- React Query for all API calls with caching
- Stale time: 30 seconds for lists, 5 minutes for details
- Background refetch on window focus
- Pagination with 20 items per page

### Bundle Size
- Lazy load page components with React.lazy()
- Sidebar component already installed, no new dependencies

### Rendering
- Memoize list item components with React.memo
- Virtualize long lists if > 100 items (future optimization)
- Debounce search input (300ms)

---

## Security Considerations

### Authentication
- All new routes wrapped in ProtectedRoute
- Admin routes require `requireAdmin` prop
- Token refresh handled by existing AuthContext

### Authorization
- Backend enforces user ownership on all source/brief operations
- Frontend assumes backend validation, shows generic error on 403

### Input Validation
- Sanitize URL input before sending to API
- Validate all form inputs client-side with Zod
- Backend performs final validation

---

## Monitoring and Observability

### Error Tracking
- Console.error for API failures (existing pattern)
- Toast notifications for user-visible errors

### Analytics (future)
- Track page views for each section
- Track create/edit/delete actions
- Track brief execution frequency

### Performance Monitoring (future)
- Page load times
- API response times
- Bundle size tracking

---

## File Structure

```
frontend/src/
├── components/
│   ├── layout/
│   │   ├── AppLayout.tsx
│   │   ├── AppSidebar.tsx
│   │   └── PageHeader.tsx
│   ├── sources/
│   │   ├── SourceCard.tsx
│   │   ├── AddSourceDialog.tsx
│   │   └── SourceForm.tsx
│   ├── briefs/
│   │   ├── BriefCard.tsx
│   │   ├── CreateBriefDialog.tsx
│   │   └── BriefForm.tsx
│   ├── reports/
│   │   └── ReportItemCard.tsx
│   └── shared/
│       ├── StatusBadge.tsx
│       ├── EmptyState.tsx
│       └── ConfirmDialog.tsx
├── pages/
│   ├── HomePage.tsx
│   ├── SourcesPage.tsx
│   ├── SourceDetailPage.tsx
│   ├── BriefsPage.tsx
│   ├── BriefDetailPage.tsx
│   ├── BriefReportsPage.tsx
│   └── ReportDetailPage.tsx
├── types/
│   └── index.ts
└── lib/
    └── api.ts (extended)
```

---

## Migration Notes

### Breaking Changes
- `/dashboard` route removed, replaced by `/home`
- `Navbar` component deprecated in favor of `AppSidebar`
- Settings page moved from standalone to nested route

### Backward Compatibility
- Add redirect from `/dashboard` to `/home`
- Keep Navbar temporarily for public pages (Index)

### Rollout
1. Implement AppLayout and routing changes
2. Add Sources pages
3. Add Briefs pages
4. Add Home page
5. Remove old Dashboard/Navbar
6. Update Settings page layout
