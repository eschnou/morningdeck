import { useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { ExternalLink, User, Calendar, Sparkles, Mail, MailOpen, Bookmark, Star } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { format } from 'date-fns';
import { apiClient } from '@/lib/api';
import type { NewsItemDTO } from '@/types';

interface ItemDetailPanelProps {
  item: NewsItemDTO | null;
  open: boolean;
  onClose: () => void;
  onItemUpdated?: (item: NewsItemDTO) => void;
}

export function ItemDetailPanel({
  item,
  open,
  onClose,
  onItemUpdated,
}: ItemDetailPanelProps) {
  // Auto-mark as read after 3 seconds
  useEffect(() => {
    if (!open || !item || item.readAt) return;

    const timer = setTimeout(() => {
      apiClient.toggleRead(item.id).then((updatedItem) => {
        onItemUpdated?.(updatedItem);
      }).catch(console.error);
    }, 3000);

    return () => clearTimeout(timer);
  }, [open, item, onItemUpdated]);

  if (!item) return null;

  const content = item.content || '';
  const topics = item.tags?.topics ?? [];

  const handleExternalLink = () => {
    window.open(item.link, '_blank', 'noopener,noreferrer');
  };

  const handleToggleRead = () => {
    apiClient.toggleRead(item.id).then((updatedItem) => {
      onItemUpdated?.(updatedItem);
    }).catch(console.error);
  };

  const handleToggleSaved = () => {
    apiClient.toggleSaved(item.id).then((updatedItem) => {
      onItemUpdated?.(updatedItem);
    }).catch(console.error);
  };

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-[50vw] flex flex-col p-0"
      >
        <SheetHeader className="px-6 py-4 border-b flex-shrink-0">
          <div className="flex items-start justify-between gap-4 pr-8">
            <SheetTitle className="text-left leading-tight">
              {item.title}
            </SheetTitle>
            <div className="flex items-center gap-1 flex-shrink-0">
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0"
                onClick={handleToggleRead}
                title={item.readAt ? "Mark as unread" : "Mark as read"}
              >
                {item.readAt ? <MailOpen className="h-4 w-4" /> : <Mail className="h-4 w-4" />}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0"
                onClick={handleToggleSaved}
                title={item.saved ? "Remove from saved" : "Save for later"}
              >
                <Bookmark className={`h-4 w-4 ${item.saved ? 'fill-current text-primary' : ''}`} />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0"
                onClick={handleExternalLink}
                title="Open original article"
              >
                <ExternalLink className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground mt-2">
            <span>{item.sourceName}</span>
            {item.author && (
              <>
                <span className="text-muted-foreground/50">•</span>
                <span className="flex items-center gap-1">
                  <User className="h-3 w-3" />
                  {item.author}
                </span>
              </>
            )}
            {item.publishedAt && (
              <>
                <span className="text-muted-foreground/50">•</span>
                <span className="flex items-center gap-1">
                  <Calendar className="h-3 w-3" />
                  {format(new Date(item.publishedAt), 'MMM d, yyyy h:mm a')}
                </span>
              </>
            )}
          </div>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
          {item.summary && (
            <Card className="bg-muted/50 border-muted">
              <CardContent className="p-4">
                <div className="flex items-start gap-3">
                  <Sparkles className="h-4 w-4 text-primary mt-0.5 flex-shrink-0" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-foreground">AI Summary</p>
                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {item.summary}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {item.score !== null && item.score !== undefined && (
            <Card className="bg-muted/50 border-muted">
              <CardContent className="p-4">
                <div className="flex items-start gap-3">
                  <Star className="h-4 w-4 text-yellow-500 mt-0.5 flex-shrink-0" />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-foreground">Relevance Score</p>
                      <Badge
                        variant={item.score >= 70 ? 'default' : item.score >= 40 ? 'secondary' : 'outline'}
                      >
                        {item.score}/100
                      </Badge>
                    </div>
                    {item.scoreReasoning && (
                      <p className="text-sm text-muted-foreground leading-relaxed">
                        {item.scoreReasoning}
                      </p>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {topics.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {topics.map((topic) => (
                <Badge key={topic} variant="secondary" className="text-xs">
                  {topic}
                </Badge>
              ))}
            </div>
          )}

          {content ? (
            <div className="prose prose-sm max-w-none dark:prose-invert">
              <ReactMarkdown>{content}</ReactMarkdown>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground italic">
              No content available.{' '}
              <button
                onClick={handleExternalLink}
                className="text-primary hover:underline"
              >
                View original article
              </button>
            </p>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
