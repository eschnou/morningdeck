// "Scored for you." then "Know why it matters." — the real NewsItemRow list gets scored and
// re-ranked frame by frame, then the real ItemDetailPanel slides in with the AI reasoning.
import React from "react";
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { NewsItemRow } from "@/components/sources/NewsItemRow";
import { ItemDetailPanel } from "@/components/shared/ItemDetailPanel";
import { AppSurface } from "../fx/AppSurface";
import { Background } from "../fx/Background";
import { BorderBeam } from "../fx/BorderBeam";
import { Headline } from "../fx/Headline";
import { COLORS } from "../fx/theme";
import { newsItems } from "../mocks/fixtures";

// Chronological (unsorted) order as it arrives, mixing signal and noise.
const ORDER = [6, 1, 7, 0, 4, 8];
const rows = ORDER.map((i) => ({ ...newsItems[i], readAt: null, saved: false }));
const ranked = [...rows].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));

const LIST_W = 700;
const SCALE = 1.25;
const ROW_H = 100;
const LIST_X = (1080 - LIST_W * SCALE) / 2;
const LIST_Y = 262;

const SCAN_START = 36;
// Timed to the soundtrack (scene starts on the 12.15s drop, 120 BPM = 15 frames per beat).
const SCAN_STEP = 15;
const SORT_AT = 156; // on a beat
const SWITCH_AT = 241; // panel lands on the 20.22s hit

export const IntelligenceScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const listIn = spring({ frame: frame - 6, fps, config: { damping: 200 } });
  const sort = spring({ frame: frame - SORT_AT, fps, config: { damping: 18, stiffness: 90 } });
  const dimJunk = interpolate(frame, [SORT_AT + 10, SORT_AT + 35], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const selected = frame > SORT_AT + 40;
  const scanY = interpolate(frame, [SCAN_START, SCAN_START + SCAN_STEP * rows.length], [0, rows.length * ROW_H], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const scanOpacity = interpolate(frame, [SCAN_START - 4, SCAN_START, SCAN_START + SCAN_STEP * rows.length, SCAN_START + SCAN_STEP * rows.length + 8], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const panel = spring({ frame: frame - SWITCH_AT - 6, fps, config: { damping: 20, stiffness: 90 } });
  const zoom = interpolate(frame, [SWITCH_AT + 40, SWITCH_AT + 137], [1, 1.08], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill>
      <Background />
      <Headline text="Scored for *you.*" sub="Every article ranked against your own interests." size={84} top={64} exitAt={SWITCH_AT - 8} />
      <Headline text="Know *why* it matters." sub="A summary and the reasoning behind every score." size={84} top={64} delay={SWITCH_AT} />

      {/* Feed list */}
      <AppSurface
        x={LIST_X - panel * 330}
        y={LIST_Y + (1 - listIn) * 80}
        width={LIST_W}
        height={rows.length * ROW_H}
        scale={SCALE}
        style={{ opacity: listIn * (1 - panel * 0.55), filter: `blur(${panel * 2}px)` }}
      >
        <div className="relative rounded-xl border bg-card shadow-2xl overflow-hidden" style={{ height: rows.length * ROW_H }}>
          {rows.map((item, i) => {
            const rank = ranked.indexOf(item);
            const y = interpolate(sort, [0, 1], [i * ROW_H, rank * ROW_H]);
            const tickStart = SCAN_START + i * SCAN_STEP;
            const p = interpolate(frame, [tickStart, tickStart + 22], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
            const score = frame < tickStart ? null : Math.round((item.score ?? 0) * p);
            const junk = (item.score ?? 0) < 50;
            return (
              <div
                key={item.id}
                className="absolute inset-x-0 bg-card"
                style={{
                  top: y,
                  height: ROW_H,
                  overflow: "hidden",
                  opacity: junk ? 1 - dimJunk * 0.7 : 1,
                  zIndex: junk ? 1 : 2,
                }}
              >
                <NewsItemRow
                  item={{ ...item, score }}
                  isSelected={selected && rank === 0}
                  showSourceName
                  onClick={() => {}}
                  onExternalLink={() => {}}
                />
              </div>
            );
          })}
          {/* AI scan line */}
          <div
            style={{
              position: "absolute",
              left: 0,
              right: 0,
              top: scanY - 40,
              height: 40,
              opacity: scanOpacity,
              background: `linear-gradient(to bottom, transparent, ${COLORS.violet}33)`,
              borderBottom: `2px solid ${COLORS.violet}`,
              boxShadow: `0 6px 24px ${COLORS.violet}`,
              zIndex: 5,
            }}
          />
        </div>
        {selected && (
          <div
            style={{
              position: "absolute",
              left: -3,
              right: -3,
              top: -3,
              height: ROW_H + 6,
              borderRadius: 14,
              boxShadow: `0 0 0 3px ${COLORS.violet}, 0 0 40px ${COLORS.violet}aa`,
              opacity: interpolate(frame, [SORT_AT + 40, SORT_AT + 50], [0, 1], { extrapolateRight: "clamp" }) * (1 - panel),
            }}
          />
        )}
      </AppSurface>

      {/* Detail panel */}
      {frame >= SWITCH_AT && (
        <AppSurface
          x={interpolate(panel, [0, 1], [1100, 1080 - 640 * 1.3 - 44])}
          y={268}
          width={640}
          height={600}
          scale={1.3 * zoom}
          style={{ opacity: panel, transformOrigin: "top left" }}
        >
          <div className="relative rounded-xl overflow-hidden shadow-2xl border" style={{ height: 600 }}>
            <ItemDetailPanel item={{ ...newsItems[0], readAt: newsItems[0].publishedAt }} open onClose={() => {}} />
          </div>
          <div style={{ position: "absolute", inset: 0, borderRadius: 12 }}>
            <BorderBeam borderWidth={2} duration={80} radius={12} />
          </div>
        </AppSurface>
      )}
    </AbsoluteFill>
  );
};
