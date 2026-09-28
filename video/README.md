# Morning Deck — promo video

A 37s, 1080×1080 LinkedIn promo built with [Remotion](https://remotion.dev).

The scenes render the **real frontend components** straight from `../frontend/src`
(`NewsItemRow`, `SourceCard`, `ItemDetailPanel`, `ReportNewsItemRow`, and the full
`AppSidebar` + `BriefDetailPage`). Two modules are swapped at bundle time
(`webpack-override.mjs`):

- `@/lib/api` → `src/mocks/api.ts`: returns fixture data from `src/mocks/fixtures.ts`.
- `@/components/ui/sheet` → `src/mocks/sheet.tsx`: renders the sheet inline, because the Radix
  version portals to `<body>` and animates with CSS keyframes, which Remotion can't capture.

Effects in `src/fx/` are frame-driven ports of Magic UI components listed on 21st.dev
(Animated Beam, Border Beam, Number Ticker, Shiny Text, Aurora Text, Particles, Retro Grid).

## Commands

Requires `frontend/node_modules` (run `npm install` in `frontend/` first).

```bash
npm install
npm run studio   # live preview / scrub timeline
npm run render   # out/morningdeck-promo.mp4
npm run still    # out/poster.png (thumbnail)
node scripts/stills.mjs 100 467 920   # quick preview stills of given frames
```

Edit copy in `src/scenes/*`, demo data in `src/mocks/fixtures.ts`, timing in `src/Promo.tsx`.
