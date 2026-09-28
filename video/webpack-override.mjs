// Shared by remotion.config.ts (CLI/Studio) and scripts/*.mjs (Node API).
import path from "node:path";
import { enableTailwind } from "@remotion/tailwind";

// Commands run from video/.
const root = process.cwd();

export const webpackOverride = (current) => {
  const withTailwind = enableTailwind(current);
  return {
    ...withTailwind,
    resolve: {
      ...withTailwind.resolve,
      // Frontend-only packages (router, radix, lucide…) resolve from the app's own install.
      modules: ["node_modules", path.resolve(root, "../frontend/node_modules")],
      alias: {
        ...(withTailwind.resolve?.alias ?? {}),
        // Swap the network layer for fixture data; everything else is the real app code.
        "@/lib/api$": path.resolve(root, "src/mocks/api.ts"),
        // Radix Sheet portals to <body>; render it inline so it lives inside the video frame.
        "@/components/ui/sheet$": path.resolve(root, "src/mocks/sheet.tsx"),
        "@": path.resolve(root, "../frontend/src"),
        // Remotion already pins "react"; pin react-dom too so frontend code shares one instance.
        "react-dom$": path.resolve(root, "node_modules/react-dom"),
      },
    },
  };
};
