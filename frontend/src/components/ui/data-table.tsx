import * as React from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';

export interface Column<T> {
  key: string;
  header: string;
  render: (item: T) => React.ReactNode;
  sortable?: boolean;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  isLoading?: boolean;
  emptyState?: React.ReactNode;
  onRowClick?: (item: T) => void;
  sortKey?: string;
  sortDirection?: 'asc' | 'desc';
  onSort?: (key: string) => void;
  getRowKey?: (item: T) => string;
}

function DataTableSkeleton({ columns, rows }: { columns: number; rows: number }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {Array.from({ length: columns }).map((_, i) => (
            <TableHead key={i}>
              <Skeleton className="h-4 w-24" />
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <TableRow key={rowIndex}>
            {Array.from({ length: columns }).map((_, colIndex) => (
              <TableCell key={colIndex}>
                <Skeleton className="h-4 w-full" />
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function DataTable<T extends { id: string }>({
  columns,
  data,
  isLoading,
  emptyState,
  onRowClick,
  sortKey,
  sortDirection,
  onSort,
  getRowKey,
}: DataTableProps<T>) {
  if (isLoading) {
    return <DataTableSkeleton columns={columns.length} rows={5} />;
  }

  if (data.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  const handleHeaderClick = (col: Column<T>) => {
    if (col.sortable && onSort) {
      onSort(col.key);
    }
  };

  const handleRowClick = (item: T, event: React.MouseEvent) => {
    // Don't trigger row click if clicking on an interactive element
    const target = event.target as HTMLElement;
    if (
      target.closest('button') ||
      target.closest('a') ||
      target.closest('[role="button"]')
    ) {
      return;
    }
    onRowClick?.(item);
  };

  return (
    <Table>
      <TableHeader>
        <TableRow>
          {columns.map((col) => (
            <TableHead
              key={col.key}
              className={cn(
                col.className,
                col.sortable && onSort && 'cursor-pointer select-none hover:bg-muted/50'
              )}
              onClick={() => handleHeaderClick(col)}
            >
              <div className="flex items-center gap-1">
                {col.header}
                {col.sortable && sortKey === col.key && (
                  sortDirection === 'asc' ? (
                    <ChevronUp className="size-4" />
                  ) : (
                    <ChevronDown className="size-4" />
                  )
                )}
              </div>
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((item) => (
          <TableRow
            key={getRowKey ? getRowKey(item) : item.id}
            className={cn(onRowClick && 'cursor-pointer')}
            onClick={(e) => handleRowClick(item, e)}
          >
            {columns.map((col) => (
              <TableCell key={col.key} className={col.className}>
                {col.render(item)}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
