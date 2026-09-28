// Render preview stills: node scripts/stills.mjs 100 467 920
import path from "node:path";
import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import { webpackOverride } from "../webpack-override.mjs";

const frames = process.argv.slice(2).map(Number);
const serveUrl = await bundle({ entryPoint: path.resolve("src/index.ts"), webpackOverride, publicDir: path.resolve("public") });
const composition = await selectComposition({ serveUrl, id: "Promo" });
for (const frame of frames) {
  await renderStill({ composition, serveUrl, frame, output: `out/still-${frame}.png` });
  console.log("rendered", frame);
}
