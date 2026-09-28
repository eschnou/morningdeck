// "Your brief, ready at 7:00." — inbox notification, then the report's ranked stories.
import React from "react";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { format } from "date-fns";
import { Calendar, FileText, ListOrdered, Mail } from "lucide-react";
import { ReportNewsItemRow } from "@/components/reports/ReportNewsItemRow";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Card, CardContent } from "@/components/ui/card";
import { AppSurface } from "../fx/AppSurface";
import { Background } from "../fx/Background";
import { Headline } from "../fx/Headline";
import { inter } from "../fx/fonts";
import { COLORS } from "../fx/theme";
import { reportItems, reports } from "../mocks/fixtures";

const W = 720;
const SCALE = 1.22;
const X = (1080 - W * SCALE) / 2;

export const BriefScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Clock: 6:58 -> 7:00, then the notification drops in.
  const minute = frame < 22 ? 58 : frame < 40 ? 59 : 0;
  const hour = minute === 0 ? 7 : 6;
  const tick = spring({ frame: frame - (minute === 59 ? 22 : minute === 0 ? 40 : 0), fps, config: { damping: 12 } });
  const notif = spring({ frame: frame - 46, fps, config: { damping: 15, stiffness: 120 } });
  const report = spring({ frame: frame - 62, fps, config: { damping: 200 } });

  return (
    <AbsoluteFill>
      <Background hue="sunrise" />
      <Headline text="Your brief, | ready by *7:00.*" size={84} top={56} />

      {/* Clock */}
      <div
        style={{
          position: "absolute",
          top: 90,
          right: 70,
          fontFamily: inter,
          fontSize: 30,
          fontWeight: 700,
          color: COLORS.amber,
          padding: "8px 18px",
          borderRadius: 999,
          border: `1px solid ${COLORS.amber}66`,
          background: `${COLORS.amber}14`,
          fontVariantNumeric: "tabular-nums",
          transform: `scale(${0.9 + tick * 0.1})`,
          opacity: interpolate(frame, [0, 10], [0, 1], { extrapolateRight: "clamp" }),
        }}
      >
        {hour}:{String(minute).padStart(2, "0")} AM
      </div>

      {/* Inbox notification, composed from the app's own Card primitives */}
      <AppSurface x={X} y={interpolate(notif, [0, 1], [150, 290])} width={W} scale={SCALE} style={{ opacity: notif }}>
        <Card className="shadow-2xl">
          <CardContent className="p-4 flex items-center gap-3">
            <div className="h-10 w-10 rounded-lg bg-muted flex items-center justify-center flex-shrink-0">
              <Img src={staticFile("logo.png")} style={{ width: 28, height: 28 }} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-sm">Morning Deck</span>
                <span className="text-xs text-muted-foreground">now</span>
              </div>
              <p className="text-sm text-muted-foreground truncate">
                Your AI &amp; Dev Tools brief is ready — {reportItems.length} stories worth your time
              </p>
            </div>
            <Mail className="h-5 w-5 text-muted-foreground" />
          </CardContent>
        </Card>
      </AppSurface>

      {/* Report */}
      <AppSurface x={X} y={400 + (1 - report) * 60} width={W} scale={SCALE} style={{ opacity: report }}>
        <Card className="shadow-2xl overflow-hidden">
          <CardContent className="p-4 border-b">
            <div className="flex items-center gap-3">
              <FileText className="h-5 w-5 text-muted-foreground" />
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-medium">{format(new Date(reports[0].generatedAt), "EEEE, MMMM d, yyyy")}</span>
                  <StatusBadge status="GENERATED" size="sm" />
                </div>
                <div className="flex items-center gap-4 text-sm text-muted-foreground mt-0.5">
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3 w-3" />
                    7:00 AM
                  </span>
                  <span className="flex items-center gap-1">
                    <ListOrdered className="h-3 w-3" />
                    {reportItems.length} items
                  </span>
                </div>
              </div>
            </div>
          </CardContent>
          {reportItems.slice(0, 4).map((item, i) => {
            const s = spring({ frame: frame - 72 - i * 7, fps, config: { damping: 18, stiffness: 130 } });
            return (
              <div key={item.id} style={{ opacity: s, transform: `translateX(${(1 - s) * 80}px)` }}>
                <ReportNewsItemRow item={item} isSelected={false} onClick={() => {}} onExternalLink={() => {}} />
              </div>
            );
          })}
        </Card>
      </AppSurface>
    </AbsoluteFill>
  );
};
