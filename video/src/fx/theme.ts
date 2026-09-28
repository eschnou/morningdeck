export const COLORS = {
  bg: "#0a0910",
  bgSoft: "#14121c",
  text: "#f5f3ff",
  muted: "#a1a1b5",
  violet: "#7c5cff",
  purple: "#c084fc",
  orange: "#fb923c",
  amber: "#fbbf24",
};

// Sunrise gradient: the brand violet (landing page accent) into morning amber.
export const SUNRISE = [COLORS.violet, COLORS.purple, COLORS.orange, COLORS.amber];

export const SPRING = {
  smooth: { damping: 200 },
  snappy: { damping: 20, stiffness: 200 },
  bouncy: { damping: 11, stiffness: 120 },
  heavy: { damping: 15, stiffness: 80, mass: 2 },
};
