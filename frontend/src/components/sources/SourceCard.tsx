import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Rss, Mail, Globe, Clock, FileText, Newspaper, MessageSquare } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import type { SourceDTO } from '@/types';

interface SourceCardProps {
  source: SourceDTO;
  onClick?: () => void;
}

export function SourceCard({ source, onClick }: SourceCardProps) {
  const displayAddress = source.type === 'EMAIL' && source.emailAddress
    ? source.emailAddress
    : source.url;

  const truncate = (text: string, maxLength = 40): string => {
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
  };

  return (
    <Card
      className="cursor-pointer transition-colors hover:bg-muted/50"
      onClick={onClick}
    >
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              {source.type === 'WEB' ? (
                <Globe className="h-4 w-4 text-muted-foreground flex-shrink-0" />
              ) : source.type === 'EMAIL' ? (
                <Mail className="h-4 w-4 text-muted-foreground flex-shrink-0" />
              ) : source.type === 'REDDIT' ? (
                <MessageSquare className="h-4 w-4 text-muted-foreground flex-shrink-0" />
              ) : (
                <Rss className="h-4 w-4 text-muted-foreground flex-shrink-0" />
              )}
              <h3 className="font-medium truncate">{source.name}</h3>
            </div>
            <Tooltip>
              <TooltipTrigger asChild>
                <p className="text-sm text-muted-foreground truncate">
                  {truncate(displayAddress)}
                </p>
              </TooltipTrigger>
              <TooltipContent side="bottom" className="max-w-md">
                <p className="break-all">{displayAddress}</p>
              </TooltipContent>
            </Tooltip>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <Badge variant="outline">{source.type}</Badge>
            <StatusBadge status={source.status} size="sm" />
          </div>
        </div>

        <div className="flex items-center gap-4 mt-3 text-xs text-muted-foreground">
          {source.briefingTitle && (
            <div className="flex items-center gap-1">
              <Newspaper className="h-3 w-3" />
              <span>{source.briefingTitle}</span>
            </div>
          )}
          <div className="flex items-center gap-1">
            <FileText className="h-3 w-3" />
            <span>{source.itemCount} items</span>
            {source.unreadCount > 0 && (
              <Badge variant="default" className="ml-1 h-5 px-1.5 text-[10px]">
                {source.unreadCount} new
              </Badge>
            )}
          </div>
          {source.lastFetchedAt && (
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="flex items-center gap-1 cursor-default">
                  <Clock className="h-3 w-3" />
                  <span>
                    {formatDistanceToNow(parseApiDate(source.lastFetchedAt), { addSuffix: true })}
                  </span>
                </div>
              </TooltipTrigger>
              <TooltipContent>
                {format(parseApiDate(source.lastFetchedAt), 'PPpp')}
              </TooltipContent>
            </Tooltip>
          )}
        </div>

        {source.tags && source.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-2">
            {source.tags.map((tag) => (
              <Badge key={tag} variant="secondary" className="text-xs">
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
