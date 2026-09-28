// Video-only stand-in for "@/components/ui/sheet".
// The real Sheet portals to <body> with CSS keyframe animations, neither of which survive
// Remotion's frame-by-frame capture. This renders the same surface inline so the real
// ItemDetailPanel can be placed and animated inside a scene.
import * as React from "react";
import { cn } from "@/lib/utils";

type DivProps = React.HTMLAttributes<HTMLDivElement>;

export const Sheet = ({ open, children }: { open?: boolean; onOpenChange?: (open: boolean) => void; children?: React.ReactNode }) =>
  open ? <>{children}</> : null;

export const SheetContent = React.forwardRef<HTMLDivElement, DivProps & { side?: string }>(
  ({ className, side: _side, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("relative h-full gap-4 bg-background p-6 shadow-lg border-l", className, "w-full sm:max-w-none")}
      {...props}
    />
  ),
);

export const SheetHeader = ({ className, ...props }: DivProps) => (
  <div className={cn("flex flex-col space-y-2 text-center sm:text-left", className)} {...props} />
);
export const SheetFooter = ({ className, ...props }: DivProps) => (
  <div className={cn("flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2", className)} {...props} />
);
export const SheetTitle = ({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) => (
  <h2 className={cn("text-lg font-semibold text-foreground", className)} {...props} />
);
export const SheetDescription = ({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) => (
  <p className={cn("text-sm text-muted-foreground", className)} {...props} />
);
export const SheetTrigger = ({ children }: { children?: React.ReactNode }) => <>{children}</>;
export const SheetClose = SheetTrigger;
export const SheetPortal = SheetTrigger;
export const SheetOverlay = () => null;
