// Scene backdrop: aurora glows + Magic UI-style retro grid + drifting particles, all frame-driven.
import React from "react";
import { AbsoluteFill, random, useCurrentFrame, useVideoConfig } from "remotion";
import { COLORS } from "./theme";

const Particles: React.FC<{ count?: number; seed?: string }> = ({ count = 60, seed = "p" }) => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  return (
    <AbsoluteFill>
      {Array.from({ length: count }).map((_, i) => {
        const x = random(`${seed}x${i}`) * width;
        const y0 = random(`${seed}y${i}`) * height;
        const speed = 0.2 + random(`${seed}s${i}`) * 0.6;
        const size = 1 + random(`${seed}r${i}`) * 2.2;
        const y = (((y0 - frame * speed) % height) + height) % height;
        const twinkle = 0.25 + 0.5 * Math.abs(Math.sin(frame / 25 + i));
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: x + Math.sin(frame / 50 + i) * 8,
              top: y,
              width: size,
              height: size,
              borderRadius: "50%",
              background: "white",
              opacity: twinkle,
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};

const RetroGrid: React.FC<{ opacity?: number }> = ({ opacity = 0.35 }) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ perspective: 400, overflow: "hidden", opacity }}>
      <div
        style={{
          position: "absolute",
          inset: "-100% -50% 0 -50%",
          top: "55%",
          transform: "rotateX(65deg)",
          transformOrigin: "top",
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            height: "300%",
            backgroundImage:
              "linear-gradient(to right, rgba(192,132,252,0.35) 1px, transparent 0), linear-gradient(to bottom, rgba(192,132,252,0.35) 1px, transparent 0)",
            backgroundSize: "60px 60px",
            transform: `translateY(${-((frame * 1.2) % 60)}px)`,
          }}
        />
      </div>
      <AbsoluteFill style={{ background: `linear-gradient(to bottom, ${COLORS.bg} 50%, transparent 80%, ${COLORS.bg} 100%)` }} />
    </AbsoluteFill>
  );
};

export const Background: React.FC<{ grid?: boolean; hue?: "violet" | "sunrise" }> = ({ grid = true, hue = "violet" }) => {
  const frame = useCurrentFrame();
  const drift = (n: number) => Math.sin(frame / 60 + n) * 60;
  const secondary = hue === "sunrise" ? COLORS.orange : COLORS.purple;
  return (
    <AbsoluteFill style={{ background: COLORS.bg, overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          width: 900,
          height: 900,
          left: -250 + drift(0),
          top: -350 + drift(1),
          borderRadius: "50%",
          background: `radial-gradient(circle, ${COLORS.violet}55 0%, transparent 65%)`,
          filter: "blur(40px)",
        }}
      />
      <div
        style={{
          position: "absolute",
          width: 800,
          height: 800,
          right: -300 + drift(2),
          bottom: -300 + drift(3),
          borderRadius: "50%",
          background: `radial-gradient(circle, ${secondary}44 0%, transparent 65%)`,
          filter: "blur(40px)",
        }}
      />
      {grid && <RetroGrid />}
      <Particles />
    </AbsoluteFill>
  );
};
