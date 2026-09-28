// The real app shell — AppSidebar + BriefDetailPage with its live feed — in a browser window.
import React, { useEffect, useRef, useState } from "react";
import { AbsoluteFill, continueRender, delayRender, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/contexts/AuthContext";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/AppSidebar";
import BriefDetailPage from "@/pages/BriefDetailPage";
import { AppSurface } from "../fx/AppSurface";
import { Background } from "../fx/Background";
import { BorderBeam } from "../fx/BorderBeam";
import { Headline } from "../fx/Headline";
import { BRIEF_ID } from "../mocks/fixtures";

const WIN_W = 1280;
const WIN_H = 860;

// The app references /logo.png from the site root; point it at Remotion's static folder.
const useFixAppAssets = (ref: React.RefObject<HTMLDivElement>) => {
  const [handle] = useState(() => delayRender("app assets"));
  useEffect(() => {
    const timer = setTimeout(() => {
      ref.current?.querySelectorAll<HTMLImageElement>('img[src="/logo.png"]').forEach((img) => {
        img.src = staticFile("logo.png");
      });
      continueRender(handle);
    }, 150);
    return () => clearTimeout(timer);
  });
};

const App: React.FC = () => (
  <MemoryRouter initialEntries={[`/briefs/${BRIEF_ID}`]}>
    <AuthProvider>
      <SidebarProvider defaultOpen>
        <AppSidebar />
        <SidebarInset>
          <Routes>
            <Route path="/briefs/:id" element={<BriefDetailPage />} />
          </Routes>
        </SidebarInset>
      </SidebarProvider>
    </AuthProvider>
  </MemoryRouter>
);

export const AppScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const ref = useRef<HTMLDivElement>(null);
  useFixAppAssets(ref);

  const enter = spring({ frame: frame - 4, fps, config: { damping: 20, stiffness: 60 } });
  const push = interpolate(frame, [40, 165], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const baseScale = 0.8;
  const scale = baseScale * (0.85 + enter * 0.15) * (1 + push * 0.05);
  const x = (1080 - WIN_W * scale) / 2;
  const y = 300 + (1 - enter) * 200;

  return (
    <AbsoluteFill>
      <Background />
      <Headline text="Calm, not *chaos.*" sub="Read, filter, search and save — all in one feed." size={84} top={64} />
      <AbsoluteFill style={{ perspective: 1800 }}>
        <AppSurface
          x={x}
          y={y}
          width={WIN_W}
          height={WIN_H}
          scale={scale}
          style={{
            transform: `scale(${scale}) rotateX(${(1 - enter) * 28}deg) rotateZ(${(1 - enter) * -6}deg)`,
            opacity: enter,
          }}
        >
          <div className="relative rounded-2xl overflow-hidden border shadow-2xl bg-background" style={{ height: WIN_H }}>
            <div className="bg-secondary/80 border-b px-4 py-3 flex items-center gap-2">
              <div className="flex gap-1.5">
                <div className="w-3 h-3 rounded-full bg-red-400" />
                <div className="w-3 h-3 rounded-full bg-yellow-400" />
                <div className="w-3 h-3 rounded-full bg-green-400" />
              </div>
              <div className="flex-1 mx-24">
                <div className="bg-background rounded-lg px-3 py-1.5 text-xs text-muted-foreground text-center border">
                  app.morningdeck.com/briefs/ai-dev-tools
                </div>
              </div>
            </div>
            <div ref={ref} className="app-window relative" style={{ height: WIN_H - 45, transform: "translateZ(0)", overflow: "hidden" }}>
              <App />
            </div>
          </div>
          <div style={{ position: "absolute", inset: 0, borderRadius: 16 }}>
            <BorderBeam borderWidth={3} duration={100} radius={16} />
          </div>
        </AppSurface>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
