import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { Rss, ExternalLink, Trash2, FileText } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import type { SourceDTO } from '@/types';

interface BriefSourceItemProps {
  source: SourceDTO;
  onDelete: () => void;
  onView: () => void;
}

export function BriefSourceItem({ source, onDelete, onView }: BriefSourceItemProps) {
  return (
    <div className="flex items-center justify-between p-3 rounded-lg border hover:bg-muted/50 transition-colors">
      <div
        className="flex items-center gap-3 flex-1 min-w-0 cursor-pointer"
        onClick={onView}
      >
        <Rss className="h-4 w-4 text-muted-foreground flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium truncate">{source.name}</span>
            <StatusBadge status={source.status} size="sm" />
          </div>
          <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
            <span className="flex items-center gap-1">
              <FileText className="h-3 w-3" />
              {source.itemCount} items
            </span>
            {source.lastFetchedAt && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="cursor-default">
                    Updated {formatDistanceToNow(parseApiDate(source.lastFetchedAt), { addSuffix: true })}
                  </span>
                </TooltipTrigger>
                <TooltipContent>
                  {format(parseApiDate(source.lastFetchedAt), 'PPpp')}
                </TooltipContent>
              </Tooltip>
            )}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          onClick={(e) => {
            e.stopPropagation();
            window.open(source.url, '_blank');
          }}
        >
          <ExternalLink className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-destructive hover:text-destructive"
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
