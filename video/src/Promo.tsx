import React from "react";
import { AbsoluteFill, Audio, interpolate, staticFile } from "remotion";
import { TransitionSeries, springTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { HookScene } from "./scenes/HookScene";
import { RevealScene } from "./scenes/RevealScene";
import { SourcesScene } from "./scenes/SourcesScene";
import { IntelligenceScene } from "./scenes/IntelligenceScene";
import { BriefScene } from "./scenes/BriefScene";
import { AppScene } from "./scenes/AppScene";
import { OutroScene } from "./scenes/OutroScene";
import { COLORS } from "./fx/theme";

const T = 16;
const timing = springTiming({ config: { damping: 200 }, durationInFrames: T });

// Durations are cut to public/soundtrack.mp3 (120 BPM). Scene starts, in frames:
// Reveal 117 (bass enters 4.09s) · Intelligence 359 (drop 12.15s) · Outro 1030 (breakdown; final hit 36.40s).
export const SCENES = [
  { Component: HookScene, duration: 133 },
  { Component: RevealScene, duration: 95 },
  { Component: SourcesScene, duration: 179 },
  { Component: IntelligenceScene, duration: 378 },
  { Component: BriefScene, duration: 165 },
  { Component: AppScene, duration: 176 },
  { Component: OutroScene, duration: 155 },
];

export const PROMO_DURATION = SCENES.reduce((sum, s) => sum + s.duration, 0) - T * (SCENES.length - 1);

const presentations = [fade(), slide({ direction: "from-right" }), fade(), fade(), fade(), fade()];

export const Promo: React.FC = () => (
  <AbsoluteFill style={{ background: COLORS.bg }}>
    <Audio
      src={staticFile("soundtrack.mp3")}
      volume={(f) => interpolate(f, [PROMO_DURATION - 30, PROMO_DURATION], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })}
    />
    <TransitionSeries>
      {SCENES.map(({ Component, duration }, i) => (
        <React.Fragment key={i}>
          {i > 0 && <TransitionSeries.Transition presentation={presentations[i - 1]} timing={timing} />}
          <TransitionSeries.Sequence durationInFrames={duration} premountFor={30}>
            <Component />
          </TransitionSeries.Sequence>
        </React.Fragment>
      ))}
    </TransitionSeries>
  </AbsoluteFill>
);
