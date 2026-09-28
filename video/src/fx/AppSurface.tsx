// Hosts real Morning Deck components inside a scene: light app theme, tooltip context,
// absolute placement and a uniform scale so UI text stays legible on a phone feed.
import React from "react";
import { TooltipProvider } from "@/components/ui/tooltip";
import { inter } from "./fonts";

interface AppSurfaceProps {
  x: number;
  y: number;
  /** Native (unscaled) width of the app UI in CSS px. */
  width: number;
  height?: number;
  scale?: number;
  style?: React.CSSProperties;
  className?: string;
  children: React.ReactNode;
}

export const AppSurface: React.FC<AppSurfaceProps> = ({ x, y, width, height, scale = 1, style, className, children }) => (
  <div
    className={`app-surface bg-transparent text-foreground ${className ?? ""}`}
    style={{
      position: "absolute",
      left: x,
      top: y,
      width,
      height,
      transform: `scale(${scale})`,
      transformOrigin: "top left",
      fontFamily: inter,
      ...style,
    }}
  >
    <TooltipProvider>{children}</TooltipProvider>
  </div>
);
