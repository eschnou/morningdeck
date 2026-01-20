import React, { forwardRef } from 'react';
import { ExternalLink, Star } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { ReportItemDTO } from '@/types';

interface ReportNewsItemRowProps {
  item: ReportItemDTO;
  isSelected: boolean;
  onClick: () => void;
  onExternalLink: (e: React.MouseEvent) => void;
}

export const ReportNewsItemRow = React.memo(
  forwardRef<HTMLDivElement, ReportNewsItemRowProps>(function ReportNewsItemRow(
    { item, isSelected, onClick, onExternalLink },
    ref
  ) {
    return (
      <div
        ref={ref}
        data-state={isSelected ? 'selected' : undefined}
        onClick={onClick}
        className={cn(
          "flex items-center justify-between gap-4 px-4 py-3 border-b cursor-pointer transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted"
        )}
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground font-mono w-5 flex-shrink-0">
              #{item.position}
            </span>
            <h4 className="text-sm font-medium line-clamp-1">
              {item.title}
            </h4>
          </div>
          {item.summary && (
            <p className="text-xs text-muted-foreground line-clamp-2 mt-1 ml-7">
              {item.summary}
            </p>
          )}
          <div className="flex items-center gap-2 mt-1.5 ml-7 text-xs text-muted-foreground">
            <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 font-normal">
              {item.sourceName}
            </Badge>
          </div>
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          <Badge
            variant={item.score >= 70 ? 'default' : item.score >= 40 ? 'secondary' : 'outline'}
            className="text-[10px] px-1.5 py-0 h-5"
          >
            <Star className="h-3 w-3 mr-0.5" />
            {item.score.toFixed(0)}
          </Badge>
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0"
            onClick={onExternalLink}
            title="Open original article"
          >
            <ExternalLink className="h-4 w-4" />
          </Button>
        </div>
      </div>
    );
  })
);
