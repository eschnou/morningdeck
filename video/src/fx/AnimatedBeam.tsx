// Frame-driven port of Magic UI's AnimatedBeam (as listed on 21st.dev).
// Takes explicit coordinates instead of measuring DOM refs, which keeps it deterministic
// under Remotion's parallel frame rendering.
import React, { useId } from "react";
import { interpolate, useCurrentFrame } from "remotion";
import { COLORS } from "./theme";

interface AnimatedBeamProps {
  from: [number, number];
  to: [number, number];
  curvature?: number;
  /** Frame at which the beam starts drawing. */
  delay?: number;
  /** Frames for one pulse to travel the path. */
  duration?: number;
  colorFrom?: string;
  colorTo?: string;
  width?: number;
}

export const AnimatedBeam: React.FC<AnimatedBeamProps> = ({
  from,
  to,
  curvature = 0,
  delay = 0,
  duration = 45,
  colorFrom = COLORS.orange,
  colorTo = COLORS.violet,
  width = 3,
}) => {
  const frame = useCurrentFrame() - delay;
  const id = useId().replace(/:/g, "");
  const [x1, y1] = from;
  const [x2, y2] = to;
  const d = `M ${x1},${y1} Q ${(x1 + x2) / 2},${(y1 + y2) / 2 - curvature} ${x2},${y2}`;

  const reveal = interpolate(frame, [0, 20], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const pulse = ((Math.max(frame, 0) % duration) / duration) * 1.4 - 0.2;

  return (
    <g opacity={reveal}>
      <defs>
        <linearGradient id={`beam-${id}`} gradientUnits="userSpaceOnUse" x1={x1} y1={y1} x2={x2} y2={y2}>
          <stop offset="0" stopColor={colorFrom} />
          <stop offset="1" stopColor={colorTo} />
        </linearGradient>
      </defs>
      <path d={d} stroke="white" strokeOpacity={0.12} strokeWidth={width} fill="none" strokeLinecap="round" />
      <path
        d={d}
        pathLength={1}
        stroke={`url(#beam-${id})`}
        strokeWidth={width + 1}
        fill="none"
        strokeLinecap="round"
        strokeDasharray="0.22 1.5"
        strokeDashoffset={-pulse}
        style={{ filter: `drop-shadow(0 0 6px ${colorFrom})` }}
      />
    </g>
  );
};
