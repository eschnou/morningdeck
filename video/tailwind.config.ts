import path from "path";
import type { Config } from "tailwindcss";
import appConfig from "../frontend/tailwind.config";

export default {
  ...appConfig,
  content: [
    path.resolve(__dirname, "../frontend/src/**/*.{ts,tsx}"),
    path.resolve(__dirname, "src/**/*.{ts,tsx}"),
  ],
} satisfies Config;
