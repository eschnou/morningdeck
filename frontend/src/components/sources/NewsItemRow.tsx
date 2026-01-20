import React, { forwardRef } from 'react';
import { ExternalLink, User, Calendar, Bookmark, Star } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { formatDistanceToNow, format } from 'date-fns';
import { cn, parseApiDate } from '@/lib/utils';
import type { NewsItemDTO } from '@/types';

interface NewsItemRowProps {
  item: NewsItemDTO;
  isSelected: boolean;
  showSourceName?: boolean;
  onClick: () => void;
  onExternalLink: (e: React.MouseEvent) => void;
}

export const NewsItemRow = React.memo(
  forwardRef<HTMLDivElement, NewsItemRowProps>(function NewsItemRow(
    { item, isSelected, showSourceName = false, onClick, onExternalLink },
    ref
  ) {
    const topics = item.tags?.topics?.slice(0, 3) ?? [];
    const isUnread = !item.readAt;

    return (
      <div
        ref={ref}
        data-state={isSelected ? 'selected' : undefined}
        onClick={onClick}
        className={cn(
          "group flex items-center justify-between gap-4 px-4 py-3.5 border-b border-border/50 last:border-b-0 cursor-pointer transition-colors hover:bg-muted/30 data-[state=selected]:bg-muted/50",
          !isUnread && "opacity-70"
        )}
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            {isUnread && (
              <div className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0" />
            )}
            <h4 className={cn(
              "text-sm line-clamp-1",
              isUnread ? "font-semibold" : "font-normal text-muted-foreground"
            )}>
              {item.title}
            </h4>
          </div>
          {item.summary && (
            <p className="text-xs text-muted-foreground line-clamp-2 mt-1">
              {item.summary}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1.5 text-xs text-muted-foreground">
            {showSourceName && item.sourceName && (
              <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-4 font-normal truncate max-w-[120px]">
                {item.sourceName}
              </Badge>
            )}
            {item.publishedAt && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="flex items-center gap-1 flex-shrink-0 cursor-default">
                    <Calendar className="h-3 w-3" />
                    {formatDistanceToNow(parseApiDate(item.publishedAt), { addSuffix: true })}
                  </span>
                </TooltipTrigger>
                <TooltipContent>
                  {format(parseApiDate(item.publishedAt), 'PPpp')}
                </TooltipContent>
              </Tooltip>
            )}
            {item.author && (
              <span className="hidden sm:flex items-center gap-1 flex-shrink-0">
                <User className="h-3 w-3" />
                <span className="truncate max-w-[120px]">{item.author}</span>
              </span>
            )}
            {topics.length > 0 && (
              <div className="hidden sm:flex items-center gap-1 flex-wrap">
                {topics.map((topic) => (
                  <Badge
                    key={topic}
                    variant="secondary"
                    className="text-[10px] px-1.5 py-0 h-4 font-normal"
                  >
                    {topic}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          {item.score !== null && item.score !== undefined && item.score >= 70 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="flex items-center gap-1 text-xs text-muted-foreground">
                  <Star className="h-3.5 w-3.5" />
                  {item.score}
                </span>
              </TooltipTrigger>
              {item.scoreReasoning && (
                <TooltipContent side="left" className="max-w-xs">
                  <p className="text-xs">{item.scoreReasoning}</p>
                </TooltipContent>
              )}
            </Tooltip>
          )}
          {item.saved && (
            <Bookmark className="h-4 w-4 text-foreground fill-current" />
          )}
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100 transition-opacity"
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
