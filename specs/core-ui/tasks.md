# Core UI Implementation Tasks

## Phase 1: Foundation - Types and API Client

**Goal**: Establish type definitions and API client methods for sources, briefs, and reports.

**Verification**: Run TypeScript compiler with no errors. API client methods match backend endpoints.

### Tasks

#### 1.1 Create frontend types file
- Create `frontend/src/types/index.ts`
- Define enums: `SourceType`, `SourceStatus`, `BriefingFrequency`, `DayBriefStatus`, `ReportStatus`
- Define DTOs: `SourceDTO`, `DayBriefDTO`, `ReportItemDTO`, `DailyReportDTO`
- Define request types: `CreateSourceRequest`, `UpdateSourceRequest`, `CreateDayBriefRequest`, `UpdateDayBriefRequest`
- Export all types

#### 1.2 Extend API client with Source methods
- Add to `frontend/src/lib/api.ts`:
  - `getSources(page, size, status?, search?): Promise<PagedResponse<SourceDTO>>`
  - `getSource(id): Promise<SourceDTO>`
  - `createSource(data): Promise<SourceDTO>`
  - `updateSource(id, data): Promise<SourceDTO>`
  - `deleteSource(id): Promise<void>`
- Import types from `@/types`

#### 1.3 Extend API client with DayBrief methods
- Add to `frontend/src/lib/api.ts`:
  - `getDayBriefs(page, size, status?): Promise<PagedResponse<DayBriefDTO>>`
  - `getDayBrief(id): Promise<DayBriefDTO>`
  - `createDayBrief(data): Promise<DayBriefDTO>`
  - `updateDayBrief(id, data): Promise<DayBriefDTO>`
  - `deleteDayBrief(id): Promise<void>`
  - `executeDayBrief(id): Promise<void>`

#### 1.4 Extend API client with Report methods
- Add to `frontend/src/lib/api.ts`:
  - `getDayBriefReports(briefId, page, size): Promise<PagedResponse<DailyReportDTO>>`
  - `getDayBriefReport(briefId, reportId): Promise<DailyReportDTO>`
  - `getLatestReports(): Promise<DailyReportDTO[]>` (for home page)

---

## Phase 2: Layout Shell

**Goal**: Implement sidebar-based layout that wraps all authenticated routes.

**Verification**: Navigate between authenticated pages; sidebar visible, collapse/expand works, state persists across page refresh.

### Tasks

#### 2.1 Create AppLayout component
- Create `frontend/src/components/layout/AppLayout.tsx`
- Wrap content in `SidebarProvider` with `defaultOpen={true}`
- Include `AppSidebar` component
- Include `SidebarInset` with `<Outlet />` for nested routes
- Use existing shadcn/ui sidebar components

#### 2.2 Create AppSidebar component
- Create `frontend/src/components/layout/AppSidebar.tsx`
- `SidebarHeader`: App logo/name (use "daybrief.ai" text, collapsible)
- `SidebarContent` with `SidebarMenu`:
  - Home (`/home`, Newspaper icon)
  - Sources (`/sources`, Rss icon)
  - Briefs (`/briefs`, FileText icon)
- `SidebarFooter`:
  - Settings (`/settings`, Settings icon)
  - User dropdown (avatar, name, logout)
- Use `useLocation()` to highlight active item
- Use `useAuth()` for user info and logout
- Include `SidebarRail` for collapse interaction

#### 2.3 Create PageHeader component
- Create `frontend/src/components/layout/PageHeader.tsx`
- Props: `title?: string`, `children?: ReactNode` (for action buttons)
- Include `SidebarTrigger` for mobile/collapse toggle
- Include `Separator`
- Include `Breadcrumb` component (use route path segments)
- Flexible spacer
- Children slot for page-specific actions

#### 2.4 Update App.tsx routing structure
- Wrap protected routes in `AppLayout` using nested route structure
- Add route for `/home` (HomePage placeholder)
- Add route for `/sources` (SourcesPage placeholder)
- Add route for `/sources/:id` (SourceDetailPage placeholder)
- Add route for `/briefs` (BriefsPage placeholder)
- Add route for `/briefs/:id` (BriefDetailPage placeholder)
- Add route for `/briefs/:id/reports` (BriefReportsPage placeholder)
- Add route for `/briefs/:id/reports/:reportId` (ReportDetailPage placeholder)
- Add redirect from `/dashboard` to `/home`
- Keep `/settings` and `/admin` in nested layout

#### 2.5 Create placeholder pages
- Create `frontend/src/pages/HomePage.tsx` (simple "Home" heading)
- Create `frontend/src/pages/SourcesPage.tsx` (simple "Sources" heading)
- Create `frontend/src/pages/SourceDetailPage.tsx` (simple "Source Detail" heading)
- Create `frontend/src/pages/BriefsPage.tsx` (simple "Briefs" heading)
- Create `frontend/src/pages/BriefDetailPage.tsx` (simple "Brief Detail" heading)
- Create `frontend/src/pages/BriefReportsPage.tsx` (simple "Reports" heading)
- Create `frontend/src/pages/ReportDetailPage.tsx` (simple "Report Detail" heading)

#### 2.6 Update Settings page layout
- Remove `Navbar` from Settings page
- Use `PageHeader` component with title "Settings"
- Keep existing content structure

---

## Phase 3: Shared Components

**Goal**: Build reusable components used across Sources/Briefs/Reports pages.

**Verification**: Components render correctly in isolation with sample props.

### Tasks

#### 3.1 Create StatusBadge component
- Create `frontend/src/components/shared/StatusBadge.tsx`
- Props: `status: SourceStatus | DayBriefStatus | ReportStatus`, `size?: 'sm' | 'default'`
- Map statuses to badge variants:
  - ACTIVE/GENERATED → default (green tint)
  - PAUSED/PENDING → secondary (gray)
  - ERROR → destructive (red)
  - DELETED → outline (muted)
- Use existing `Badge` component from shadcn/ui

#### 3.2 Create EmptyState component
- Create `frontend/src/components/shared/EmptyState.tsx`
- Props: `icon: LucideIcon`, `title: string`, `description: string`, `action?: { label: string; onClick: () => void }`
- Centered layout with icon, title, description, optional action button
- Use muted colors for icon

#### 3.3 Create ConfirmDialog component
- Create `frontend/src/components/shared/ConfirmDialog.tsx`
- Props: `open: boolean`, `onOpenChange: (open: boolean) => void`, `title: string`, `description: string`, `confirmLabel?: string`, `variant?: 'default' | 'destructive'`, `onConfirm: () => void`, `isLoading?: boolean`
- Use AlertDialog from shadcn/ui
- Show loading spinner on confirm button when `isLoading`

#### 3.4 Create PaginationControls component
- Create `frontend/src/components/shared/PaginationControls.tsx`
- Props: `currentPage: number`, `totalPages: number`, `onPageChange: (page: number) => void`, `disabled?: boolean`
- Previous/Next buttons with page indicator
- Disable buttons at boundaries
- Match existing Admin page pagination style

---

## Phase 4: Sources Feature

**Goal**: Implement full Sources CRUD functionality.

**Verification**: User can list, add, view, edit, and delete sources. Filters and search work. Pagination works.

### Tasks

#### 4.1 Create SourceCard component
- Create `frontend/src/components/sources/SourceCard.tsx`
- Props: `source: SourceDTO`, `onClick?: () => void`
- Display: name, URL (truncated with tooltip), type badge, StatusBadge, item count, last fetched time (relative format)
- Use Card component
- Hover effect for clickability

#### 4.2 Create AddSourceDialog component
- Create `frontend/src/components/sources/AddSourceDialog.tsx`
- Props: `open: boolean`, `onOpenChange: (open: boolean) => void`, `onSuccess?: (source: SourceDTO) => void`
- Step 1: URL input field, optional name, tags input
- URL validation with Zod schema
- On submit: call `apiClient.createSource()`
- Show loading state, handle errors with toast
- On success: toast notification, call onSuccess, close dialog

#### 4.3 Implement SourcesPage
- Update `frontend/src/pages/SourcesPage.tsx`
- PageHeader with title "Sources" and "Add Source" button
- Filter bar: status dropdown (All/Active/Paused/Error), search input
- Use `apiClient.getSources()` for data fetching
- Display SourceCard components in grid layout
- PaginationControls at bottom
- EmptyState when no sources (CTA opens AddSourceDialog)
- Loading state with skeleton cards

#### 4.4 Create SourceForm component
- Create `frontend/src/components/sources/SourceForm.tsx`
- Props: `source?: SourceDTO` (for edit mode), `onSubmit: (data) => void`, `onCancel: () => void`, `isLoading?: boolean`
- Fields: name (Input), tags (multi-select/input), status (Select: Active/Paused)
- Zod validation
- Submit and Cancel buttons

#### 4.5 Implement SourceDetailPage
- Update `frontend/src/pages/SourceDetailPage.tsx`
- Get `id` from URL params
- Fetch source with `apiClient.getSource(id)`
- PageHeader with back button, title (source name)
- View mode: display all source fields in Card
- Edit button toggles to edit mode with SourceForm
- Delete button opens ConfirmDialog
- On delete: call `apiClient.deleteSource()`, navigate to /sources

---

## Phase 5: Briefs Feature

**Goal**: Implement full Briefs CRUD functionality.

**Verification**: User can list, create, view, edit, delete briefs, and execute them manually.

### Tasks

#### 5.1 Create BriefCard component
- Create `frontend/src/components/briefs/BriefCard.tsx`
- Props: `brief: DayBriefDTO`, `onClick?: () => void`
- Display: title, description (truncated), frequency badge, schedule time, source count, StatusBadge, last executed time
- Use Card component
- Hover effect

#### 5.2 Create CreateBriefDialog component
- Create `frontend/src/components/briefs/CreateBriefDialog.tsx`
- Props: `open: boolean`, `onOpenChange: (open: boolean) => void`, `onSuccess?: (brief: DayBriefDTO) => void`
- Form sections:
  - Basic: title (required), description (optional)
  - Content: briefing criteria (required textarea), source selection (required, multi-select)
  - Schedule: frequency (Select), time (time input), timezone (Select)
- Fetch user's sources for selection with `apiClient.getSources(0, 100, 'ACTIVE')`
- Zod validation for all fields
- On submit: call `apiClient.createDayBrief()`
- Toast and close on success

#### 5.3 Implement BriefsPage
- Update `frontend/src/pages/BriefsPage.tsx`
- PageHeader with title "Briefs" and "Create Brief" button
- Filter bar: status dropdown (All/Active/Paused)
- Use `apiClient.getDayBriefs()` for data fetching
- Display BriefCard components in grid
- PaginationControls
- EmptyState when no briefs (CTA opens CreateBriefDialog)
- Loading skeleton

#### 5.4 Create BriefForm component
- Create `frontend/src/components/briefs/BriefForm.tsx`
- Props: `brief?: DayBriefDTO`, `sources: SourceDTO[]`, `onSubmit: (data) => void`, `onCancel: () => void`, `isLoading?: boolean`
- All editable fields from CreateBriefDialog
- Source multi-select with checkboxes
- Zod validation

#### 5.5 Implement BriefDetailPage
- Update `frontend/src/pages/BriefDetailPage.tsx`
- Get `id` from URL params
- Fetch brief and user's sources
- PageHeader with back button, title
- View mode: display all fields in Card
- Edit button toggles BriefForm
- "Execute Now" button: call `apiClient.executeDayBrief()`, toast on success
- "View Reports" link to `/briefs/:id/reports`
- Delete with ConfirmDialog

---

## Phase 6: Reports Feature

**Goal**: Implement report viewing for briefs.

**Verification**: User can view list of reports for a brief and read individual report content.

### Tasks

#### 6.1 Create ReportItemCard component
- Create `frontend/src/components/reports/ReportItemCard.tsx`
- Props: `item: ReportItemDTO`, `position: number`
- Display: position number, headline (clickable external link), source name, score badge, summary text
- Use Card component
- External link icon for article URL

#### 6.2 Implement BriefReportsPage
- Update `frontend/src/pages/BriefReportsPage.tsx`
- Get `id` from URL params
- Fetch brief info and reports with `apiClient.getDayBriefReports()`
- PageHeader with back link to `/briefs/:id`, title "Reports for {brief.title}"
- Report list: each row shows generation date, StatusBadge, item count
- Click navigates to `/briefs/:id/reports/:reportId`
- PaginationControls
- EmptyState when no reports

#### 6.3 Implement ReportDetailPage
- Update `frontend/src/pages/ReportDetailPage.tsx`
- Get `id` and `reportId` from URL params
- Fetch report with `apiClient.getDayBriefReport()`
- PageHeader with back link, title (brief title + report date)
- Report header: generation timestamp, status
- Map `report.items` to ReportItemCard components
- Loading state

---

## Phase 7: Home Page

**Goal**: Implement the home page showing latest briefing reports.

**Verification**: User sees their latest report on login. Can switch between briefs. Empty states display correctly.

### Tasks

#### 7.1 Implement HomePage
- Update `frontend/src/pages/HomePage.tsx`
- Fetch user's briefs with `apiClient.getDayBriefs()`
- If no briefs: EmptyState with CTA to create brief
- If briefs exist: dropdown to select brief (default: first active)
- Fetch latest report for selected brief
- If no report: message "No report generated yet. Execute your brief to generate one."
- Display report using ReportItemCard components
- Show brief title, generation timestamp as header

#### 7.2 Update Index page redirect
- When user is authenticated on `/`, redirect to `/home` instead of `/dashboard`
- Update Index.tsx to check auth status and redirect

---

## Phase 8: Cleanup and Polish

**Goal**: Remove deprecated components, ensure consistency, handle edge cases.

**Verification**: No references to old Dashboard/Navbar in authenticated routes. All pages handle loading/error states.

### Tasks

#### 8.1 Remove deprecated Dashboard page
- Delete `frontend/src/pages/Dashboard.tsx`
- Remove import from App.tsx

#### 8.2 Remove Navbar from authenticated routes
- Navbar still used by public Index page
- Ensure no authenticated pages import Navbar
- Navbar can remain for Index page or be simplified

#### 8.3 Add loading states to all pages
- SourcesPage: skeleton cards during load
- BriefsPage: skeleton cards during load
- Detail pages: skeleton content during load
- Use Skeleton component from shadcn/ui

#### 8.4 Add error states to all pages
- Display error message if API fetch fails
- Retry button to refetch
- Toast notification for mutation errors

#### 8.5 Responsive testing
- Test sidebar collapse on mobile viewport
- Test Sheet behavior for mobile sidebar
- Ensure all forms are usable on mobile

---

## Phase 9: Testing (Optional)

**Goal**: Add automated tests for critical components and flows.

**Verification**: All tests pass.

### Tasks

#### 9.1 Set up testing framework
- Install Vitest, @testing-library/react, @testing-library/user-event
- Configure vitest.config.ts
- Create test setup file with providers

#### 9.2 Write component tests
- StatusBadge: renders correct variant for each status
- EmptyState: renders title, description, action button
- SourceCard: displays source info correctly
- BriefCard: displays brief info correctly

#### 9.3 Write page tests
- SourcesPage: renders source list, handles empty state
- BriefsPage: renders brief list, handles empty state
- HomePage: renders latest report or empty state

---

## Summary

| Phase | Description | Key Deliverables |
|-------|-------------|------------------|
| 1 | Foundation | Types, API client methods |
| 2 | Layout Shell | AppLayout, AppSidebar, routing |
| 3 | Shared Components | StatusBadge, EmptyState, ConfirmDialog, Pagination |
| 4 | Sources Feature | SourcesPage, SourceDetailPage, AddSourceDialog |
| 5 | Briefs Feature | BriefsPage, BriefDetailPage, CreateBriefDialog |
| 6 | Reports Feature | BriefReportsPage, ReportDetailPage |
| 7 | Home Page | HomePage with latest report display |
| 8 | Cleanup | Remove Dashboard, polish loading/error states |
| 9 | Testing | Vitest setup, component and page tests |
