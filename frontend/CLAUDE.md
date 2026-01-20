# Frontend - React + TypeScript

React SPA with TypeScript, Vite, Tailwind CSS, and shadcn/ui. For project-wide context, see root `CLAUDE.md`.

## Commands

```bash
npm run dev      # Start dev server with hot reload (port 5173)
npm run build    # Production build
npm run lint     # Run ESLint
npm run preview  # Preview production build locally
```

## Architecture

### Directory Structure

```
src/
├── components/
│   └── ui/          # shadcn/ui components
├── contexts/        # React contexts (AuthContext, etc.)
├── hooks/           # Custom hooks (use-toast, etc.)
├── lib/             # Utilities (api.ts, utils.ts)
├── pages/           # Page components
└── App.tsx          # Route definitions
```

### Key Patterns

**Path Aliases**
- Use `@/` to import from `src/` (e.g., `@/components/ui/button`)

**Authentication**
- `AuthContext` provides auth state via `useAuth()` hook
- JWT stored in localStorage as `auth_token`
- `ProtectedRoute` wrapper for authenticated routes
- Admin routes use `<ProtectedRoute requireAdmin>`
- Admin role: `ADMIN` in user's `roles` array

**API Client** (`src/lib/api.ts`)
- Singleton `apiClient` handles all backend communication
- Base URL from `VITE_API_BASE_URL` (default: `http://localhost:3000`)
- Automatically attaches Bearer token

**UI Components**
- shadcn/ui components in `src/components/ui/`
- Use `cn()` from `@/lib/utils` for conditional classes
- Toast notifications via `@/hooks/use-toast`

### Route Structure

- `/` - Public index page
- `/auth/login`, `/auth/register` - Authentication
- `/briefs`, `/sources`, `/settings` - Protected (require auth)
- `/admin` - Admin-only (requires `ADMIN` role)

Add routes in `App.tsx` above the catch-all `*` route.

## TypeScript Guidelines

### Types
- Prefer `interface` for object shapes, `type` for unions/intersections
- Export types alongside components that use them
- Use strict mode - avoid `any`, prefer `unknown` for unknown types
- Use discriminated unions for state variants

### Components
```tsx
interface ButtonProps {
  variant?: 'primary' | 'secondary';
  children: React.ReactNode;
  onClick?: () => void;
}

export function Button({ variant = 'primary', children, onClick }: ButtonProps) {
  return (
    <button className={cn('btn', variant)} onClick={onClick}>
      {children}
    </button>
  );
}
```

### State Management
- Use React Context for global state (auth, theme)
- Use local state for component-specific data
- Prefer `useState` + `useReducer` over external libraries

### Error Handling
- Use error boundaries for component errors
- Show toast notifications for API errors
- Type API responses properly

## Styling Guidelines

### Tailwind CSS
- Use Tailwind utility classes directly
- Extract repeated patterns to components, not CSS
- Use `cn()` for conditional class merging

### shadcn/ui
- Customize via `components.json` and Tailwind config
- Extend existing components rather than creating from scratch
- Follow shadcn conventions for consistency
