// "Read less. Know more." + CTA.
import React from "react";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Background } from "../fx/Background";
import { BorderBeam } from "../fx/BorderBeam";
import { Headline } from "../fx/Headline";
import { inter } from "../fx/fonts";
import { COLORS } from "../fx/theme";

const CHIPS = ["RSS · Newsletters · Web · Reddit", "Daily email brief", "Open source · Self-host"];

export const OutroScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  // Local frame 62 is the soundtrack's final hit (36.40s).
  const HIT = 62;
  const brand = spring({ frame: frame - HIT + 5, fps, config: { damping: 16 } });
  const cta = spring({ frame: frame - HIT + 3, fps, config: { damping: 12 } });

  return (
    <AbsoluteFill>
      <Background hue="sunrise" />
      <AbsoluteFill
        style={{
          background: `radial-gradient(circle at 50% 70%, ${COLORS.orange}66, transparent 60%)`,
          opacity: interpolate(frame, [HIT - 1, HIT + 2, HIT + 24], [0, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      />
      <Headline text="Read *less.* | Know *more.*" size={128} top={150} delay={4} />

      <AbsoluteFill style={{ alignItems: "center", fontFamily: inter }}>
        <div
          style={{
            position: "absolute",
            top: 520,
            display: "flex",
            alignItems: "center",
            gap: 18,
            opacity: brand,
            transform: `translateY(${(1 - brand) * 30}px)`,
          }}
        >
          <Img src={staticFile("logo.png")} style={{ width: 78, height: 78, filter: "invert(1)" }} />
          <span style={{ fontSize: 56, fontWeight: 800, letterSpacing: "-0.04em", color: COLORS.text }}>Morning Deck</span>
        </div>

        <div style={{ position: "absolute", top: 655, display: "flex", gap: 12, justifyContent: "center", width: 1040 }}>
          {CHIPS.map((c, i) => {
            const s = spring({ frame: frame - HIT - 2 - i * 4, fps, config: { damping: 200 } });
            return (
              <div
                key={c}
                style={{
                  opacity: s,
                  transform: `translateY(${(1 - s) * 20}px)`,
                  padding: "10px 18px",
                  borderRadius: 999,
                  border: "1px solid rgba(255,255,255,0.18)",
                  background: "rgba(255,255,255,0.06)",
                  color: COLORS.text,
                  fontSize: 21,
                  fontWeight: 500,
                  whiteSpace: "nowrap",
                }}
              >
                {c}
              </div>
            );
          })}
        </div>

        <div
          style={{
            position: "absolute",
            top: 770,
            transform: `scale(${cta})`,
            borderRadius: 999,
          }}
        >
          <div
            style={{
              position: "relative",
              padding: "26px 56px",
              borderRadius: 999,
              background: "white",
              color: COLORS.bg,
              fontSize: 42,
              fontWeight: 800,
              letterSpacing: "-0.02em",
              boxShadow: `0 0 ${40 + Math.sin(frame / 8) * 15}px ${COLORS.orange}88`,
            }}
          >
            morningdeck.com →
          </div>
          <div style={{ position: "absolute", inset: -4, borderRadius: 999 }}>
            <BorderBeam borderWidth={4} duration={60} radius={999} />
          </div>
        </div>
        <div
          style={{
            position: "absolute",
            top: 920,
            fontSize: 24,
            color: COLORS.muted,
            opacity: interpolate(frame, [HIT + 25, HIT + 45], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          }}
        >
          Start every day informed, not overwhelmed.
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
