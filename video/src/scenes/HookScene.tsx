// "Too much news." — real NewsItemRow cards flood the screen while the unread counter climbs.
import React from "react";
import { AbsoluteFill, interpolate, random, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { NewsItemRow } from "@/components/sources/NewsItemRow";
import type { NewsItemDTO } from "@/types";
import { AppSurface } from "../fx/AppSurface";
import { Background } from "../fx/Background";
import { Headline } from "../fx/Headline";
import { useTicker } from "../fx/NumberTicker";
import { inter } from "../fx/fonts";
import { newsItems, noiseTitles } from "../mocks/fixtures";

const flood: NewsItemDTO[] = noiseTitles
  .flatMap((title, i) => [
    { ...newsItems[i % newsItems.length], id: `noise-${i}`, title, score: null, summary: null, author: null, tags: { topics: ["Trending"] } },
    { ...newsItems[i % newsItems.length], id: `real-${i}`, score: null },
  ])
  .map((item) => ({ ...item, readAt: null, saved: false }));

const CARD_WIDTH = 620;
const CARD_SCALE = 1.2;

export const HookScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps, width } = useVideoConfig();
  const unread = useTicker(327, { delay: 8, durationInFrames: 95 });
  const exit = interpolate(frame, [101, 133], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const badgeIn = spring({ frame: frame - 30, fps, config: { damping: 14 } });

  return (
    <AbsoluteFill>
      <Background grid={false} />
      <AbsoluteFill style={{ transform: `scale(${1 + exit * 0.25})`, filter: `blur(${exit * 10}px)`, opacity: 1 - exit * 0.6 }}>
        {flood.map((item, i) => {
          const start = i * 5;
          const s = spring({ frame: frame - start, fps, config: { damping: 16, stiffness: 110 } });
          const cardW = CARD_WIDTH * CARD_SCALE;
          const restX = (width - cardW) / 2 + (random(`x${i}`) - 0.5) * 360;
          const restY = 440 + random(`y${i}`) * 480 - 40;
          const fromX = restX + (random(`fx${i}`) - 0.5) * 1400;
          const rot = (random(`r${i}`) - 0.5) * 22;
          const x = interpolate(s, [0, 1], [fromX, restX]);
          const y = interpolate(s, [0, 1], [-500, restY]);
          return (
            <AppSurface
              key={item.id}
              x={x}
              y={y}
              width={CARD_WIDTH}
              scale={CARD_SCALE}
              style={{ rotate: `${rot * (1.4 - s * 0.4)}deg`, opacity: s > 0.01 ? 1 : 0 }}
            >
              <div className="rounded-xl border bg-card shadow-xl overflow-hidden">
                <NewsItemRow item={item} isSelected={false} showSourceName onClick={() => {}} onExternalLink={() => {}} />
              </div>
            </AppSurface>
          );
        })}
      </AbsoluteFill>

      <AbsoluteFill style={{ background: "linear-gradient(to bottom, rgba(10,9,16,0.95) 0%, rgba(10,9,16,0.85) 30%, transparent 48%)" }} />
      <Headline text="Too much news. | Not enough *time.*" delay={0} size={92} top={70} />
      <div
        style={{
          position: "absolute",
          top: 300,
          left: 0,
          right: 0,
          display: "flex",
          justifyContent: "center",
          opacity: badgeIn,
          transform: `scale(${0.6 + badgeIn * 0.4})`,
        }}
      >
        <div
          style={{
            fontFamily: inter,
            display: "flex",
            alignItems: "center",
            gap: 14,
            padding: "12px 26px",
            borderRadius: 999,
            background: "#101b33",
            boxShadow: "0 10px 40px rgba(0,0,0,0.6)",
            border: "1px solid rgba(96,165,250,0.5)",
            color: "#dbeafe",
            fontSize: 32,
            fontWeight: 700,
          }}
        >
          <div style={{ width: 14, height: 14, borderRadius: "50%", background: "#3b82f6", boxShadow: "0 0 16px #3b82f6" }} />
          <span style={{ fontVariantNumeric: "tabular-nums" }}>{unread}</span>
          <span style={{ fontWeight: 500, opacity: 0.8 }}>unread this morning</span>
        </div>
      </div>
    </AbsoluteFill>
  );
};
