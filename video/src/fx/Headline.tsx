import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { AuroraText } from "./Text";
import { inter, serif } from "./fonts";
import { COLORS } from "./theme";

interface HeadlineProps {
  /** Words wrapped in *asterisks* get the serif-italic sunrise accent. Use "|" for a line break. */
  text: string;
  sub?: string;
  delay?: number;
  size?: number;
  top?: number;
  /** Local frame at which the headline blurs out (optional). */
  exitAt?: number;
  align?: "center" | "left";
}

export const Headline: React.FC<HeadlineProps> = ({ text, sub, delay = 0, size = 78, top = 70, exitAt, align = "center" }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const lines = text.split("|").map((l) => l.trim().split(" "));
  const exit = exitAt === undefined ? 0 : spring({ frame: frame - exitAt, fps, config: { damping: 200 }, durationInFrames: 15 });
  let wordIndex = 0;

  const subIn = spring({ frame: frame - delay - 12, fps, config: { damping: 200 } });

  return (
    <div
      style={{
        position: "absolute",
        top,
        left: 60,
        right: 60,
        textAlign: align,
        fontFamily: inter,
        color: COLORS.text,
        opacity: 1 - exit,
        filter: `blur(${exit * 12}px)`,
        transform: `translateY(${-exit * 30}px)`,
      }}
    >
      {lines.map((words, li) => (
        <div key={li} style={{ fontSize: size, fontWeight: 800, letterSpacing: "-0.04em", lineHeight: 1.05 }}>
          {words.map((raw) => {
            const i = wordIndex++;
            const s = spring({ frame: frame - delay - i * 3, fps, config: { damping: 18, stiffness: 140 } });
            const accent = raw.startsWith("*");
            const word = raw.replace(/\*/g, "");
            const content = accent ? (
              <AuroraText style={{ fontFamily: serif, fontWeight: 400, fontStyle: "italic", letterSpacing: "-0.01em", fontSize: size * 1.12 }}>
                {word}
              </AuroraText>
            ) : (
              word
            );
            return (
              <span
                key={i}
                style={{
                  display: "inline-block",
                  marginRight: "0.24em",
                  opacity: s,
                  filter: `blur(${(1 - s) * 10}px)`,
                  transform: `translateY(${(1 - s) * 40}px)`,
                }}
              >
                {content}
              </span>
            );
          })}
        </div>
      ))}
      {sub && (
        <div
          style={{
            marginTop: 18,
            fontSize: size * 0.36,
            fontWeight: 500,
            color: COLORS.muted,
            letterSpacing: "-0.01em",
            opacity: subIn,
            transform: `translateY(${interpolate(subIn, [0, 1], [16, 0])}px)`,
          }}
        >
          {sub}
        </div>
      )}
    </div>
  );
};
