// "Every source." — real SourceCard components beam into the AI core.
import React from "react";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { SourceCard } from "@/components/sources/SourceCard";
import { AppSurface } from "../fx/AppSurface";
import { AnimatedBeam } from "../fx/AnimatedBeam";
import { Background } from "../fx/Background";
import { BorderBeam } from "../fx/BorderBeam";
import { Headline } from "../fx/Headline";
import { NumberTicker } from "../fx/NumberTicker";
import { inter } from "../fx/fonts";
import { COLORS } from "../fx/theme";
import { sources } from "../mocks/fixtures";

const CARD_W = 450;
const SCALE = 1.16;
const X = 48;
const TOP = 318;
const GAP = 172;
const CORE: [number, number] = [860, 640];
const CORE_R = 105;

export const SourcesScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps, width, height } = useVideoConfig();
  const coreIn = spring({ frame: frame - 24, fps, config: { damping: 13 } });
  const pulse = (frame % 40) / 40;

  return (
    <AbsoluteFill>
      <Background />
      <Headline text="Every source. *One* place." sub="RSS, newsletters, websites and Reddit — read for you, 24/7." size={76} top={64} />

      <svg width={width} height={height} style={{ position: "absolute", inset: 0 }}>
        {sources.map((s, i) => (
          <AnimatedBeam
            key={s.id}
            from={[X + CARD_W * SCALE - 6, TOP + i * GAP + 70]}
            to={[CORE[0] - CORE_R + 10, CORE[1]]}
            curvature={(1.5 - i) * -60}
            delay={30 + i * 8}
            duration={40 + i * 5}
            colorFrom={i % 2 ? COLORS.violet : COLORS.orange}
            colorTo={i % 2 ? COLORS.amber : COLORS.purple}
          />
        ))}
      </svg>

      {sources.map((s, i) => {
        const e = spring({ frame: frame - 10 - i * 7, fps, config: { damping: 18, stiffness: 120 } });
        return (
          <AppSurface
            key={s.id}
            x={X + interpolate(e, [0, 1], [-600, 0])}
            y={TOP + i * GAP}
            width={CARD_W}
            scale={SCALE}
            style={{ opacity: e }}
          >
            <div className="rounded-lg shadow-2xl">
              <SourceCard source={s} />
            </div>
          </AppSurface>
        );
      })}

      {/* AI core */}
      <div
        style={{
          position: "absolute",
          left: CORE[0] - CORE_R,
          top: CORE[1] - CORE_R,
          width: CORE_R * 2,
          height: CORE_R * 2,
          transform: `scale(${coreIn})`,
        }}
      >
        {[0, 0.5].map((o) => {
          const p = (pulse + o) % 1;
          return (
            <div
              key={o}
              style={{
                position: "absolute",
                inset: 0,
                borderRadius: "50%",
                border: `2px solid ${COLORS.purple}`,
                transform: `scale(${1 + p * 0.7})`,
                opacity: (1 - p) * 0.6,
              }}
            />
          );
        })}
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: "50%",
            background: `radial-gradient(circle at 35% 30%, #2a2340, ${COLORS.bgSoft})`,
            boxShadow: `0 0 80px ${COLORS.violet}88, inset 0 0 30px ${COLORS.violet}55`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Img src={staticFile("logo.png")} style={{ width: 120, height: 120, filter: "invert(1)" }} />
          <BorderBeam radius="50%" borderWidth={3} duration={50} />
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: CORE[0] - 190,
          width: 380,
          top: CORE[1] + CORE_R + 36,
          textAlign: "center",
          fontFamily: inter,
          color: COLORS.text,
          opacity: coreIn,
        }}
      >
        <div style={{ fontSize: 54, fontWeight: 800, letterSpacing: "-0.03em" }}>
          <NumberTicker value={3012} delay={40} durationInFrames={100} />
        </div>
        <div style={{ fontSize: 24, color: COLORS.muted, fontWeight: 500 }}>articles read today</div>
      </div>
    </AbsoluteFill>
  );
};
