// Frame-driven port of Magic UI's BorderBeam: a light that travels around its parent's border.
import React from "react";
import { useCurrentFrame } from "remotion";
import { COLORS } from "./theme";

interface BorderBeamProps {
  duration?: number;
  borderWidth?: number;
  colorFrom?: string;
  colorTo?: string;
  radius?: number | string;
  offset?: number;
}

export const BorderBeam: React.FC<BorderBeamProps> = ({
  duration = 90,
  borderWidth = 2,
  colorFrom = COLORS.orange,
  colorTo = COLORS.violet,
  radius = "inherit",
  offset = 0,
}) => {
  const frame = useCurrentFrame();
  const angle = (((frame + offset) % duration) / duration) * 360;
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        borderRadius: radius,
        padding: borderWidth,
        pointerEvents: "none",
        background: `conic-gradient(from ${angle}deg, transparent 0deg, transparent 250deg, ${colorTo} 300deg, ${colorFrom} 345deg, transparent 360deg)`,
        WebkitMask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)",
        WebkitMaskComposite: "xor",
        maskComposite: "exclude",
      }}
    />
  );
};
