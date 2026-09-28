// Logo rises like the sun; wordmark and tagline follow.
import React from "react";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Background } from "../fx/Background";
import { ShinyText } from "../fx/Text";
import { inter } from "../fx/fonts";
import { COLORS } from "../fx/theme";

export const RevealScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const rise = spring({ frame: frame - 2, fps, config: { damping: 14, stiffness: 70 } });
  const glow = interpolate(frame, [0, 40], [0, 1], { extrapolateRight: "clamp" });
  const word = "Morning Deck";

  return (
    <AbsoluteFill>
      <Background hue="sunrise" />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", fontFamily: inter }}>
        <div
          style={{
            position: "absolute",
            width: 760,
            height: 760,
            top: 80,
            borderRadius: "50%",
            background: `radial-gradient(circle, ${COLORS.orange}66 0%, ${COLORS.violet}33 40%, transparent 70%)`,
            opacity: glow,
            transform: `scale(${0.6 + glow * 0.5})`,
            filter: "blur(20px)",
          }}
        />
        <Img
          src={staticFile("logo.png")}
          style={{
            width: 260,
            height: 260,
            filter: "invert(1) drop-shadow(0 0 30px rgba(251,146,60,0.6))",
            opacity: rise,
            transform: `translateY(${(1 - rise) * 120}px) scale(${0.7 + rise * 0.3})`,
            marginBottom: 10,
          }}
        />
        <div style={{ display: "flex", fontSize: 110, fontWeight: 800, letterSpacing: "-0.045em", color: COLORS.text }}>
          {word.split("").map((ch, i) => {
            const s = spring({ frame: frame - 16 - i * 2, fps, config: { damping: 16, stiffness: 150 } });
            return (
              <span
                key={i}
                style={{
                  display: "inline-block",
                  whiteSpace: "pre",
                  opacity: s,
                  transform: `translateY(${(1 - s) * 50}px)`,
                  filter: `blur(${(1 - s) * 8}px)`,
                }}
              >
                {ch}
              </span>
            );
          })}
        </div>
        <div style={{ marginTop: 18, fontSize: 42, fontWeight: 500, letterSpacing: "-0.015em" }}>
          <ShinyText delay={38}>Your AI news analyst.</ShinyText>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
