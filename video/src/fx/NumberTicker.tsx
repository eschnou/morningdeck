// Frame-driven port of Magic UI's NumberTicker.
import React from "react";
import { spring, useCurrentFrame, useVideoConfig } from "remotion";

interface NumberTickerProps {
  value: number;
  from?: number;
  delay?: number;
  durationInFrames?: number;
  style?: React.CSSProperties;
}

export const useTicker = (value: number, { from = 0, delay = 0, durationInFrames = 40 } = {}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: frame - delay, fps, durationInFrames, config: { damping: 200 } });
  return Math.round(from + (value - from) * p);
};

export const NumberTicker: React.FC<NumberTickerProps> = ({ value, from, delay, durationInFrames, style }) => {
  const n = useTicker(value, { from, delay, durationInFrames });
  return <span style={{ fontVariantNumeric: "tabular-nums", ...style }}>{n.toLocaleString("en-US")}</span>;
};
