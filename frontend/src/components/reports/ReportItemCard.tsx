import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ExternalLink, Newspaper } from 'lucide-react';
import type { ReportItemDTO } from '@/types';

interface ReportItemCardProps {
  item: ReportItemDTO;
}

export function ReportItemCard({ item }: ReportItemCardProps) {
  return (
    <Card>
      <CardContent className="p-4">
        <div className="space-y-3">
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <Newspaper className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                <a
                  href={item.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-medium hover:underline flex items-center gap-1"
                >
                  {item.title}
                  <ExternalLink className="h-3 w-3 flex-shrink-0" />
                </a>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Badge variant="secondary" className="text-xs">
                  {item.sourceName}
                </Badge>
                <span>Score: {item.score.toFixed(1)}</span>
              </div>
            </div>
          </div>
          {item.summary && (
            <p className="text-sm text-muted-foreground leading-relaxed">
              {item.summary}
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
