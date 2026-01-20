import { Badge } from '@/components/ui/badge';
import type { SourceStatus, DayBriefStatus, ReportStatus } from '@/types';

type Status = SourceStatus | DayBriefStatus | ReportStatus;

interface StatusBadgeProps {
  status: Status;
  size?: 'sm' | 'default';
}

const statusConfig: Record<Status, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  ACTIVE: { label: 'Active', variant: 'default' },
  PAUSED: { label: 'Paused', variant: 'secondary' },
  ERROR: { label: 'Error', variant: 'destructive' },
  DELETED: { label: 'Deleted', variant: 'outline' },
  PENDING: { label: 'Pending', variant: 'secondary' },
  GENERATED: { label: 'Generated', variant: 'default' },
};

export function StatusBadge({ status, size = 'default' }: StatusBadgeProps) {
  const config = statusConfig[status] || { label: status, variant: 'outline' as const };

  return (
    <Badge
      variant={config.variant}
      className={size === 'sm' ? 'text-xs px-1.5 py-0' : ''}
    >
      {config.label}
    </Badge>
  );
}
