// Frame-driven ports of Magic UI's AnimatedShinyText and AuroraText.
import React from "react";
import { interpolate, useCurrentFrame } from "remotion";
import { COLORS, SUNRISE } from "./theme";

export const AuroraText: React.FC<{ children: React.ReactNode; colors?: string[]; style?: React.CSSProperties }> = ({
  children,
  colors = SUNRISE,
  style,
}) => {
  const frame = useCurrentFrame();
  const pos = 50 + Math.sin(frame / 40) * 50;
  return (
    <span
      style={{
        backgroundImage: `linear-gradient(120deg, ${[...colors, colors[0]].join(", ")})`,
        backgroundSize: "220% auto",
        backgroundPosition: `${pos}% 50%`,
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        paddingRight: "0.08em",
        ...style,
      }}
    >
      {children}
    </span>
  );
};

export const ShinyText: React.FC<{ children: React.ReactNode; delay?: number; style?: React.CSSProperties }> = ({
  children,
  delay = 0,
  style,
}) => {
  const frame = useCurrentFrame();
  const sweep = interpolate(frame - delay, [0, 45], [-40, 140], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <span
      style={{
        color: COLORS.muted,
        backgroundImage: `linear-gradient(100deg, transparent ${sweep - 20}%, rgba(255,255,255,0.95) ${sweep}%, transparent ${sweep + 20}%)`,
        backgroundColor: COLORS.muted,
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        ...style,
      }}
    >
      {children}
    </span>
  );
};
